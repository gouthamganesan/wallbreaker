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
 * [contentPosted] and [bookmarkId] together are the app's idempotency record —
 * see [dev.goutham.wallbreaker.SyncWorker]. A retry that cannot tell whether the
 * previous POST landed must never save the same article under a *second* URL,
 * and these two fields are how it knows.
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
)
