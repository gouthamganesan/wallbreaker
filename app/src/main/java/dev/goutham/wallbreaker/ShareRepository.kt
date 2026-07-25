package dev.goutham.wallbreaker

import android.content.Context
import dev.goutham.wallbreaker.db.AppDatabase
import dev.goutham.wallbreaker.db.ShareEntry
import kotlinx.coroutines.flow.Flow
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
    fun observeEntries(context: Context): Flow<List<ShareEntry>> = dao(context).observeAll()
    fun observeTotal(context: Context): Flow<Int> = dao(context).observeTotal()
    fun observeUnlocks(context: Context): Flow<Int> = dao(context).observeUnlocks()

    // --- worker access ----------------------------------------------------
    suspend fun get(context: Context, id: Long): ShareEntry? = dao(context).get(id)
    suspend fun update(context: Context, entry: ShareEntry) = dao(context).update(entry)

    suspend fun delete(context: Context, entry: ShareEntry) {
        entry.contentRef?.let { runCatching { contentFile(context, it).delete() } }
        dao(context).delete(entry.id)
    }

    /**
     * Local-first save: persist the receipt (PENDING) + any HTML body, prune the
     * log, and schedule the background sync. Returns the new row id.
     */
    suspend fun createAndEnqueue(context: Context, plan: PlannedSave): Long {
        val now = System.currentTimeMillis()
        var contentRef: String? = null
        if (plan.route == Route.HTML_CONTENT && plan.htmlContent != null) {
            contentRef = "doc-${UUID.randomUUID()}.html"
            runCatching { contentFile(context, contentRef!!).writeText(plan.htmlContent, Charsets.UTF_8) }
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
        SyncScheduler.enqueue(context, id)
        return id
    }

    /** Re-schedule a failed/pending entry (used by "retry" in history). */
    suspend fun retry(context: Context, id: Long) {
        val entry = dao(context).get(id) ?: return
        dao(context).update(entry.copy(status = SyncStatus.PENDING.name, error = null, updatedAt = System.currentTimeMillis()))
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
