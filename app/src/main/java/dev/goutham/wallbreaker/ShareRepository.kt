package dev.goutham.wallbreaker

import android.content.Context
import dev.goutham.wallbreaker.db.AppDatabase
import dev.goutham.wallbreaker.db.ShareEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

/**
 * The seam between the share flow, the sync worker, and the history UI. Writes a
 * receipt the instant a share arrives (local-first), stashes any raw-HTML body
 * to a file, schedules background sync, and exposes the log as Flows.
 */
object ShareRepository {

    private const val PENDING_DIR = "pending"
    private const val MAX_ENTRIES = 500   // receipt log, not an archive

    private fun dao(context: Context) = AppDatabase.get(context).shareDao()

    // --- observation (history UI) -----------------------------------------
    fun observeEntry(context: Context, id: Long): Flow<ShareEntry?> = dao(context).observe(id)
    fun observeEntries(context: Context): Flow<List<ShareEntry>> = dao(context).observeAll()
    fun observeTotal(context: Context): Flow<Int> = dao(context).observeTotal()
    fun observeUnlocks(context: Context): Flow<Int> = dao(context).observeUnlocks()

    /**
     * Suspends until entry [id] reaches a terminal state (SYNCED or FAILED).
     * The overlay uses this to stay on screen — and therefore keep the process
     * in the foreground — for the whole Instapaper round trip.
     */
    suspend fun awaitSettled(context: Context, id: Long): ShareEntry =
        dao(context).observe(id).filterNotNull().first { entry ->
            entry.status == SyncStatus.SYNCED.name || entry.status == SyncStatus.FAILED.name
        }

    // --- worker access ----------------------------------------------------
    suspend fun get(context: Context, id: Long): ShareEntry? = dao(context).get(id)
    suspend fun update(context: Context, entry: ShareEntry) = dao(context).update(entry)

    suspend fun delete(context: Context, entry: ShareEntry) {
        entry.contentRef?.let { runCatching { contentFile(context, it).delete() } }
        dao(context).delete(entry.id)
    }

    /** What [createAndEnqueue] did — the overlay needs to tell these apart. */
    data class Enqueued(val id: Long, val wasAlreadySaved: Boolean)

    /**
     * Local-first save: persist the receipt (PENDING) + any HTML body, prune the
     * log, and schedule the background sync.
     *
     * Re-sharing a URL already in the log **updates that row** instead of adding
     * a second one. Instapaper's add is idempotent by URL, so a re-share was
     * never going to produce a second bookmark — stacking identical rows just
     * made it look like the save had failed and been retried.
     */
    suspend fun createAndEnqueue(context: Context, plan: PlannedSave): Enqueued {
        val now = System.currentTimeMillis()
        var contentRef: String? = null
        if (plan.route == Route.HTML_CONTENT && plan.htmlContent != null) {
            contentRef = "doc-${UUID.randomUUID()}.html"
            runCatching { contentFile(context, contentRef!!).writeText(plan.htmlContent, Charsets.UTF_8) }
        }

        val existing = dao(context).findByUrl(plan.url)
        if (existing != null) {
            val landed = existing.status == SyncStatus.SYNCED.name
            // Deliberate re-share: run the delivery again (it's idempotent), but
            // keep bookmarkId so the worker still knows something already landed
            // and must not save this article under a second URL.
            dao(context).update(
                existing.copy(
                    route = plan.route.name,
                    contentRef = contentRef ?: existing.contentRef,
                    status = SyncStatus.PENDING.name,
                    error = null,
                    contentPosted = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            WbLog.i("re-share of #${existing.id} (alreadySaved=$landed) host=${plan.host}")
            SyncScheduler.enqueue(context, existing.id)
            return Enqueued(existing.id, wasAlreadySaved = landed)
        }

        val entry = ShareEntry(
            url = plan.url,
            title = plan.title,
            host = plan.host,
            route = plan.route.name,
            contentRef = contentRef,
            status = SyncStatus.PENDING.name,
            error = null,
            attempts = 0,
            createdAt = now,
            updatedAt = now,
        )
        val id = dao(context).insert(entry)
        dao(context).prune(MAX_ENTRIES)
        WbLog.i("saved receipt #$id route=${plan.route} host=${plan.host}")
        SyncScheduler.enqueue(context, id)
        return Enqueued(id, wasAlreadySaved = false)
    }

    /**
     * Re-schedule an entry — history's "Save again", and tapping a failed row.
     *
     * A link entry is **re-planned**, not merely re-queued: the allowlist may
     * have gained its domain since the receipt was written (from Settings, or
     * from the share card's one-tap offer), and a "save again" that replayed the
     * stored route would quietly redeliver the same paywalled link while looking
     * like it had done something. A raw-HTML entry keeps its route; its body is
     * on disk and there is nothing to re-decide.
     *
     * [ShareEntry.delivered] is deliberately untouched. Whatever this entry
     * delivered before is still in Instapaper, and that is exactly what stops
     * the worker treating a re-route as a first delivery and saving the mirror
     * URL alongside it.
     */
    suspend fun retry(context: Context, id: Long) {
        val entry = dao(context).get(id) ?: return
        val stored = runCatching { Route.valueOf(entry.route) }.getOrNull()
        val route = if (stored == Route.HTML_CONTENT) {
            entry.route
        } else {
            (SendRouter.plan(context, SharePayload.Link(entry.url)) as? RouteResult.Ready)
                ?.save?.route?.name ?: entry.route
        }
        if (route != entry.route) WbLog.i("retry #$id re-planned ${entry.route} -> $route")
        dao(context).update(
            entry.copy(
                route = route,
                status = SyncStatus.PENDING.name,
                error = null,
                contentPosted = false,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        SyncScheduler.enqueue(context, id)
    }

    // --- HTML content files -----------------------------------------------
    fun contentFile(context: Context, ref: String): File =
        File(File(context.filesDir, PENDING_DIR).apply { mkdirs() }, ref)

    fun readContent(context: Context, ref: String): String? =
        runCatching { contentFile(context, ref).readText(Charsets.UTF_8) }.getOrNull()

    fun deleteContent(context: Context, ref: String) {
        runCatching { contentFile(context, ref).delete() }
    }
}
