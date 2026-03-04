package com.example.mycarapp.dto

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.mycarapp.Repository.AccountDao
import com.example.mycarapp.Repository.AlbumDao
import com.example.mycarapp.utils.RoomConverters

@Database(entities = [Account::class, Album::class], version = 4, exportSchema = false)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun albumDao(): AlbumDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `albums` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `coverArtUrl` TEXT, 
                        `albumArtist` TEXT NOT NULL, 
                        `playCount` INTEGER, 
                        `playDate` TEXT, 
                        `starredAt` TEXT, 
                        `libraryId` INTEGER NOT NULL, 
                        `libraryPath` TEXT NOT NULL, 
                        `libraryName` TEXT NOT NULL, 
                        `maxYear` INTEGER NOT NULL, 
                        `minYear` INTEGER NOT NULL, 
                        `date` TEXT NOT NULL, 
                        `maxOriginalYear` INTEGER NOT NULL, 
                        `minOriginalYear` INTEGER NOT NULL, 
                        `releaseDate` TEXT NOT NULL, 
                        `compilation` INTEGER NOT NULL, 
                        `songCount` INTEGER NOT NULL, 
                        `duration` REAL NOT NULL, 
                        `size` INTEGER NOT NULL, 
                        `discs` TEXT NOT NULL, 
                        `orderAlbumName` TEXT NOT NULL, 
                        `orderAlbumArtistName` TEXT NOT NULL, 
                        `explicitStatus` TEXT NOT NULL, 
                        `externalInfoUpdatedAt` TEXT, 
                        `genre` TEXT NOT NULL, 
                        `genres` TEXT NOT NULL, 
                        `tags` TEXT NOT NULL, 
                        `participants` TEXT NOT NULL, 
                        `missing` INTEGER NOT NULL, 
                        `importedAt` TEXT NOT NULL, 
                        `createdAt` TEXT NOT NULL, 
                        `updatedAt` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE albums ADD COLUMN lastPlaybackPosition INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
