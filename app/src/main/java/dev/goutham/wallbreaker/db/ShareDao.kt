package dev.goutham.wallbreaker.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShareDao {

    @Insert
    suspend fun insert(entry: ShareEntry): Long

    @Update
    suspend fun update(entry: ShareEntry)

    @Query("SELECT * FROM share_entries WHERE id = :id")
    suspend fun get(id: Long): ShareEntry?

    // Re-sharing an article should refresh its receipt, not stack another row.
    @Query("SELECT * FROM share_entries WHERE url = :url ORDER BY createdAt DESC LIMIT 1")
    suspend fun findByUrl(url: String): ShareEntry?

    @Query("DELETE FROM share_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM share_entries WHERE id = :id")
    fun observe(id: Long): Flow<ShareEntry?>

    @Query("SELECT * FROM share_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ShareEntry>>

    @Query("SELECT COUNT(*) FROM share_entries")
    fun observeTotal(): Flow<Int>

    // "Walls broken" = anything that went through Freedium, either route.
    @Query("SELECT COUNT(*) FROM share_entries WHERE route IN ('FREEDIUM_CONTENT', 'FREEDIUM_WRAP')")
    fun observeUnlocks(): Flow<Int>

    // Receipt log, not an archive: keep the newest [keep], drop the rest.
    @Query(
        "DELETE FROM share_entries WHERE id NOT IN " +
            "(SELECT id FROM share_entries ORDER BY createdAt DESC LIMIT :keep)",
    )
    suspend fun prune(keep: Int)
}
