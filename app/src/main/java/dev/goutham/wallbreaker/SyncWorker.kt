package dev.goutham.wallbreaker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
 */
class SyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val ctx = appContext

    private sealed interface Outcome {
        data class Ok(val title: String?, val effectiveRoute: Route?) : Outcome
        data class Retry(val message: String) : Outcome
        data class Fail(val message: String) : Outcome
    }

    override suspend fun doWork(): Result {
        val id = inputData.getLong(SyncScheduler.keyId(), -1L)
        if (id < 0L) return Result.failure()
        val entry = ShareRepository.get(ctx, id) ?: return Result.success()  // pruned/deleted
        if (entry.status == SyncStatus.SYNCED.name) return Result.success()

        markSyncing(entry)

        return when (val outcome = send(entry)) {
            is Outcome.Ok -> {
                entry.contentRef?.let { ShareRepository.deleteContent(ctx, it) }
                ShareRepository.update(
                    ctx,
                    entry.copy(
                        status = SyncStatus.SYNCED.name,
                        route = outcome.effectiveRoute?.name ?: entry.route,
                        title = outcome.title ?: entry.title,
                        error = null,
                        contentRef = null,
                        updatedAt = now(),
                    ),
                )
                Result.success()
            }

            is Outcome.Retry ->
                if (runAttemptCount < MAX_ATTEMPTS) {
                    ShareRepository.update(ctx, entry.copy(status = SyncStatus.PENDING.name, error = outcome.message, updatedAt = now()))
                    Result.retry()
                } else {
                    ShareRepository.update(ctx, entry.copy(status = SyncStatus.FAILED.name, error = outcome.message, updatedAt = now()))
                    Result.failure()
                }

            is Outcome.Fail -> {
                ShareRepository.update(ctx, entry.copy(status = SyncStatus.FAILED.name, error = outcome.message, updatedAt = now()))
                Result.failure()
            }
        }
    }

    private suspend fun markSyncing(entry: ShareEntry) {
        ShareRepository.update(
            ctx,
            entry.copy(status = SyncStatus.SYNCING.name, attempts = entry.attempts + 1, updatedAt = now()),
        )
    }

    private fun send(entry: ShareEntry): Outcome {
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

    private fun freediumContent(entry: ShareEntry): Outcome {
        val mirror = AppSettingsStore.load(ctx).freediumMirror
        val wrapped = Freedium.wrap(entry.url, mirror)

        // Full API app removed since enqueue → still deliver full text via the
        // mirror URL rather than the paywalled original.
        val full = resolveFull() ?: return simpleAdd(wrapped)
        if (full is FullResult.Retry) return Outcome.Retry(full.message)
        if (full is FullResult.Fail) return Outcome.Fail(full.message)
        val creds = (full as FullResult.Ok).creds

        val html = FreediumFetcher.fetch(wrapped)
            ?: return simpleAdd(wrapped)   // fetch failed → save the mirror URL, still full text

        return uploadContent(creds, url = entry.url, title = entry.title, html = html, fallbackUrl = wrapped)
    }

    // --- Full API: upload a shared raw-HTML file --------------------------

    private fun htmlContent(entry: ShareEntry): Outcome {
        val full = resolveFull()
            ?: return Outcome.Fail("Add Instapaper API keys in Settings to save HTML")
        if (full is FullResult.Retry) return Outcome.Retry(full.message)
        if (full is FullResult.Fail) return Outcome.Fail(full.message)
        val creds = (full as FullResult.Ok).creds

        val ref = entry.contentRef ?: return Outcome.Fail("HTML content missing")
        val html = ShareRepository.readContent(ctx, ref) ?: return Outcome.Fail("HTML content missing")
        return uploadContent(creds, url = entry.url, title = entry.title, html = html, fallbackUrl = null)
    }

    /** Upload [html] as content under [url]; on a terminal content error fall
     *  back to a Simple-API save of [fallbackUrl] when one is available. */
    private fun uploadContent(
        creds: FullCredentials,
        url: String,
        title: String?,
        html: String,
        fallbackUrl: String?,
    ): Outcome = try {
        val added = InstapaperFullApi.addBookmark(creds, url = url, title = title, content = html)
        Outcome.Ok(added.title, null)
    } catch (e: InstapaperNetworkException) {
        Outcome.Retry(e.message ?: "Network error")
    } catch (e: InstapaperApiException) {
        when {
            isAuthError(e) -> { FullApiAuth.invalidate(ctx); Outcome.Retry("Re-authenticating with Instapaper") }
            e.retryable -> Outcome.Retry(e.message)
            fallbackUrl != null -> simpleAdd(fallbackUrl)   // content rejected → mirror URL
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

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val MAX_ATTEMPTS = 5
    }
}
