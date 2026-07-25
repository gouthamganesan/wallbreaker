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
)
