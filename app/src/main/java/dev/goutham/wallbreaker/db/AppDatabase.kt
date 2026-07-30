package dev.goutham.wallbreaker.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ShareEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun shareDao(): ShareDao

    companion object {
        /**
         * v2 adds the idempotency record. A real migration, not destructive —
         * the history log is the user's receipt trail and must survive upgrades.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE share_entries ADD COLUMN bookmarkId INTEGER")
                db.execSQL("ALTER TABLE share_entries ADD COLUMN contentPosted INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wallbreaker.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
