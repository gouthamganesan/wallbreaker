package dev.goutham.wallbreaker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.goutham.wallbreaker.db.ShareEntry
import java.util.concurrent.TimeUnit

/** Schedules a per-entry background sync. */
object SyncScheduler {
    private const val KEY_ID = "entry_id"

    fun enqueue(context: Context, id: Long) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            // The share overlay disappears after 3s and aggressive OEM battery
            // managers (Samsung's Freecess in particular) freeze the process the
            // moment it stops being visible — mid-POST. Expedited work asks the
            // system to run this NOW and, together with getForegroundInfo(),
            // lets it run as a short foreground service so the request survives.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // One unique chain per entry: re-enqueuing REPLACEs so an explicit retry
        // supersedes any pending backoff attempt.
        WorkManager.getInstance(context)
            .enqueueUniqueWork("sync-$id", ExistingWorkPolicy.REPLACE, request)
    }

    fun keyId() = KEY_ID
}

/**
 * Delivers one saved receipt to Instapaper. Runs off the UI, survives process
 * death, and — critically — signs the OAuth request HERE, at POST time, so the
 * timestamp/nonce are fresh (never at enqueue time). Retriable failures
 * (network, 5xx, rate-limit) go back through WorkManager's exponential backoff;
 * terminal failures (bad credentials, bad URL) mark the row FAILED for the user
 * to fix.
 *
 * ### The one invariant that matters
 *
 * An article must never end up in Instapaper **twice**. Instapaper's add is
 * idempotent by URL, so re-POSTing the *same* URL is always safe — but this
 * worker can deliver a Freedium article under two different URLs: the canonical
 * `medium.com/...` one (content upload) or the `freedium-mirror.cfd/...` one
 * (Simple-API link save). Falling back from the first to the second after the
 * first may already have landed is what produced duplicate bookmarks.
 *
 * So: the mirror-URL fallback is allowed **only when nothing can possibly have
 * landed yet** — [ShareEntry.contentPosted] false, [ShareEntry.bookmarkId] null,
 * and [ShareEntry.delivered] false. Anything ambiguous retries the idempotent
 * content POST instead.
 *
 * That third condition is not redundant. A Simple-API add answers with only an
 * `X-Instapaper-Title` header, so a link that definitely landed carries no
 * bookmark id — and re-routing it through Freedium later (adding its domain to
 * the allowlist does exactly that) would otherwise look like a first delivery.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val ctx = appContext

    private sealed interface Outcome {
        data class Ok(val title: String?, val bookmarkId: Long?) : Outcome
        data class Retry(val message: String) : Outcome
        data class Fail(val message: String) : Outcome
    }

    override suspend fun doWork(): Result {
        val id = inputData.getLong(SyncScheduler.keyId(), -1L)
        if (id < 0L) return Result.failure()
        val entry = ShareRepository.get(ctx, id) ?: return Result.success()  // pruned/deleted
        if (entry.status == SyncStatus.SYNCED.name) {
            WbLog.i("sync #$id already SYNCED, skipping")
            return Result.success()
        }

        WbLog.i(
            "sync #$id start route=${entry.route} attempt=$runAttemptCount host=${entry.host} " +
                "posted=${entry.contentPosted} bookmark=${entry.bookmarkId ?: "-"} delivered=${entry.delivered}",
        )
        val working = markSyncing(entry)

        return when (val outcome = send(working)) {
            is Outcome.Ok -> {
                WbLog.i("sync #$id OK title=${outcome.title?.let { "\"$it\"" } ?: "(none)"} bookmark=${outcome.bookmarkId ?: "-"}")
                working.contentRef?.let { ShareRepository.deleteContent(ctx, it) }
                ShareRepository.update(
                    ctx,
                    working.copy(
                        status = SyncStatus.SYNCED.name,
                        title = outcome.title ?: working.title,
                        bookmarkId = outcome.bookmarkId ?: working.bookmarkId,
                        delivered = true,
                        error = null,
                        contentRef = null,
                        updatedAt = now(),
                    ),
                )
                Result.success()
            }

            is Outcome.Retry ->
                if (runAttemptCount < MAX_ATTEMPTS) {
                    WbLog.w("sync #$id retry (attempt $runAttemptCount): ${outcome.message}")
                    ShareRepository.update(ctx, working.copy(status = SyncStatus.PENDING.name, error = outcome.message, updatedAt = now()))
                    Result.retry()
                } else {
                    WbLog.w("sync #$id giving up after $runAttemptCount attempts: ${outcome.message}")
                    ShareRepository.update(ctx, working.copy(status = SyncStatus.FAILED.name, error = outcome.message, updatedAt = now()))
                    Result.failure()
                }

            is Outcome.Fail -> {
                WbLog.w("sync #$id FAILED: ${outcome.message}")
                ShareRepository.update(ctx, working.copy(status = SyncStatus.FAILED.name, error = outcome.message, updatedAt = now()))
                Result.failure()
            }
        }
    }

    /** Marks the row SYNCING and returns the row as it now stands on disk. */
    private suspend fun markSyncing(entry: ShareEntry): ShareEntry {
        val updated = entry.copy(
            status = SyncStatus.SYNCING.name,
            attempts = entry.attempts + 1,
            updatedAt = now(),
        )
        ShareRepository.update(ctx, updated)
        return updated
    }

    /** Remember that a content POST is about to go out, before it does. */
    private suspend fun markContentPosted(entry: ShareEntry): ShareEntry {
        if (entry.contentPosted) return entry
        val updated = entry.copy(contentPosted = true, updatedAt = now())
        ShareRepository.update(ctx, updated)
        return updated
    }

    private suspend fun send(entry: ShareEntry): Outcome {
        val route = runCatching { Route.valueOf(entry.route) }.getOrNull()
            ?: return Outcome.Fail("Unknown route")
        return when (route) {
            Route.SIMPLE_LINK -> simpleAdd(entry.url)
            Route.FREEDIUM_WRAP -> {
                val mirror = AppSettingsStore.load(ctx).freediumMirror
                simpleAdd(Freedium.wrap(entry.url, mirror))
            }
            Route.FREEDIUM_CONTENT -> freediumContent(entry)
            Route.HTML_CONTENT -> htmlContent(entry)
        }
    }

    // --- Simple API -------------------------------------------------------

    private fun simpleAdd(url: String): Outcome {
        val creds = CredentialStore.load(ctx) ?: return Outcome.Fail("Instapaper account not set up")
        return when (val r = InstapaperClient.add(creds, url)) {
            is InstapaperClient.AddResult.Saved -> Outcome.Ok(r.title, null)
            InstapaperClient.AddResult.BadCredentials -> Outcome.Fail("Instapaper rejected your login")
            InstapaperClient.AddResult.BadUrl -> Outcome.Fail("Instapaper rejected the URL")
            is InstapaperClient.AddResult.ServerError -> Outcome.Retry("Instapaper error ${r.code}")
            is InstapaperClient.AddResult.NetworkError -> Outcome.Retry("Network error")
        }
    }

    // --- Full API: fetch Freedium HTML, upload under the original URL ------

    private suspend fun freediumContent(entry: ShareEntry): Outcome {
        val mirror = AppSettingsStore.load(ctx).freediumMirror
        val wrapped = Freedium.wrap(entry.url, mirror)
        // True only while no delivery for this article can have landed yet.
        val virgin = !entry.contentPosted && entry.bookmarkId == null && !entry.delivered

        // Full API app removed since enqueue → still deliver full text via the
        // mirror URL rather than the paywalled original.
        val full = resolveFull() ?: return mirrorFallback(virgin, wrapped, "Full API no longer configured")
        if (full is FullResult.Retry) return Outcome.Retry(full.message)
        if (full is FullResult.Fail) return Outcome.Fail(full.message)
        val creds = (full as FullResult.Ok).creds

        val html = FreediumFetcher.fetch(wrapped)
            ?: return mirrorFallback(virgin, wrapped, "Freedium fetch returned nothing")

        // Instapaper does NOT crawl a bookmark you supply `content` for, so if we
        // send no title it derives one from the uploaded HTML — and Freedium's
        // <title> carries a "- Freedium" suffix. Sending the cleaned title is
        // what keeps the mirror invisible in the Instapaper inbox.
        val title = entry.title ?: Freedium.cleanTitle(HtmlMeta.title(html))

        val posted = markContentPosted(entry)
        return uploadContent(
            creds,
            url = posted.url,
            title = title,
            html = html,
            fallbackUrl = if (virgin) wrapped else null,
        )
    }

    /**
     * Save the mirror URL instead — full text, just a mirror-shaped link. Only
     * safe while [virgin]; otherwise a content upload may already be sitting in
     * Instapaper under the canonical URL and this would be the duplicate.
     */
    private fun mirrorFallback(virgin: Boolean, wrapped: String, why: String): Outcome {
        if (!virgin) {
            WbLog.w("$why — but content was already POSTed for this entry; retrying instead of saving the mirror URL")
            return Outcome.Retry(why)
        }
        WbLog.w("$why → falling back to a Simple-API save of the mirror URL")
        return simpleAdd(wrapped)
    }

    // --- Full API: upload a shared raw-HTML file --------------------------

    private suspend fun htmlContent(entry: ShareEntry): Outcome {
        val full = resolveFull()
            ?: return Outcome.Fail("Add Instapaper API keys in Settings to save HTML")
        if (full is FullResult.Retry) return Outcome.Retry(full.message)
        if (full is FullResult.Fail) return Outcome.Fail(full.message)
        val creds = (full as FullResult.Ok).creds

        val ref = entry.contentRef ?: return Outcome.Fail("HTML content missing")
        val html = ShareRepository.readContent(ctx, ref) ?: return Outcome.Fail("HTML content missing")
        val posted = markContentPosted(entry)
        return uploadContent(creds, url = posted.url, title = posted.title, html = html, fallbackUrl = null)
    }

    /**
     * Upload [html] as content under [url]. [fallbackUrl] — a Simple-API save of
     * a *different* URL — is only ever taken when the server explicitly rejected
     * the request (an `error_code` proves nothing was created). An unparseable
     * or truncated response is ambiguous, so it retries the idempotent POST
     * rather than risking a second bookmark.
     */
    private fun uploadContent(
        creds: FullCredentials,
        url: String,
        title: String?,
        html: String,
        fallbackUrl: String?,
    ): Outcome = try {
        val added = InstapaperFullApi.addBookmark(creds, url = url, title = title, content = html)
        Outcome.Ok(added.title ?: title, added.bookmarkId.takeIf { it > 0L })
    } catch (e: InstapaperNetworkException) {
        // The POST may or may not have reached Instapaper. Re-POSTing the same
        // URL is idempotent, so retrying is always the safe answer here.
        Outcome.Retry(e.message ?: "Network error")
    } catch (e: InstapaperApiException) {
        WbLog.w("content upload rejected: http=${e.httpStatus} code=${e.errorCode} ${e.message}")
        when {
            isAuthError(e) -> { FullApiAuth.invalidate(ctx); Outcome.Retry("Re-authenticating with Instapaper") }
            e.retryable -> Outcome.Retry(e.message)
            e.errorCode == null -> Outcome.Retry(e.message)   // ambiguous: don't risk a duplicate
            fallbackUrl != null -> {
                WbLog.w("content rejected with code ${e.errorCode} → saving the mirror URL instead")
                simpleAdd(fallbackUrl)
            }
            else -> Outcome.Fail(e.hint ?: e.message)
        }
    }

    // --- Full API auth resolution -----------------------------------------

    private sealed interface FullResult {
        data class Ok(val creds: FullCredentials) : FullResult
        data class Retry(val message: String) : FullResult
        data class Fail(val message: String) : FullResult
    }

    /** Returns null only when the Full API is not configured at all. */
    private fun resolveFull(): FullResult? = try {
        FullApiAuth.resolve(ctx)?.let { FullResult.Ok(it) }
    } catch (e: InstapaperNetworkException) {
        FullResult.Retry(e.message ?: "Network error")
    } catch (e: InstapaperApiException) {
        if (isAuthError(e)) { FullApiAuth.invalidate(ctx) }
        FullResult.Fail(e.hint ?: e.message)
    }

    private fun isAuthError(e: InstapaperApiException): Boolean =
        e.httpStatus == 401 || e.httpStatus == 403

    // --- foreground promotion (fallback only) ------------------------------

    /**
     * The primary defence against the process being frozen mid-request is that
     * the share overlay stays on screen until the sync finishes — a visible
     * activity means a foreground process, which no OEM battery manager will
     * freeze. This exists for the case that isn't covered: the user swipes the
     * card away, or the request outlives it.
     *
     * On API 31+ expedited work runs as an expedited job and never surfaces
     * this notification; below 31 WorkManager needs a foreground service, and
     * this is the notification it shows.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val note: Notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync_note)
            .setContentTitle(ctx.getString(R.string.sync_notification_title))
            .setContentText(ctx.getString(R.string.sync_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, note)
        }
    }

    private fun ensureChannel() {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = ctx.getString(R.string.sync_channel_description)
                setShowBadge(false)
            },
        )
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val CHANNEL_ID = "sync"
        private const val NOTIFICATION_ID = 4201
    }
}
