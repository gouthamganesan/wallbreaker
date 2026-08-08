package dev.goutham.wallbreaker.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved-share receipt. Written the instant a share arrives (status PENDING),
 * then updated by the background sync worker. The history screen observes these.
 *
 * [route], [status] are stored as the `.name` of the corresponding enums to keep
 * Room converter-free. [contentRef] is the filename (under filesDir/pending) of
 * the stored HTML for the raw-HTML-file route; null otherwise.
 *
 * [contentPosted], [bookmarkId] and [delivered] together are the app's
 * idempotency record — see [dev.goutham.wallbreaker.SyncWorker]. A retry that
 * cannot tell whether the previous POST landed must never save the same article
 * under a *second* URL, and these three fields are how it knows.
 */
@Entity(tableName = "share_entries")
data class ShareEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String?,
    val host: String?,
    val route: String,
    val contentRef: String?,
    val status: String,
    val error: String?,
    val attempts: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** Instapaper's id, once a delivery is confirmed. Non-null ⇒ it definitely landed. */
    val bookmarkId: Long? = null,
    /** Set *before* the content POST goes out, so an ambiguous outcome is still remembered. */
    val contentPosted: Boolean = false,
    /**
     * A delivery for this URL has landed in Instapaper at least once — sticky for
     * the life of the row, deliberately NOT cleared when the entry is re-shared or
     * re-routed.
     *
     * [bookmarkId] cannot carry this fact on its own: the Simple API answers a
     * successful add with nothing but an `X-Instapaper-Title` header, so a link
     * that definitely landed still leaves [bookmarkId] null. Re-routing such an
     * entry through Freedium then looked untouched to the worker, which is
     * exactly when it is licensed to save the mirror URL as a *second* bookmark.
     */
    val delivered: Boolean = false,
)
