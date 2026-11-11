package com.alexanderlingle.berry

import android.app.Application
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alexanderlingle.berry.data.BerryDatabase
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.ChildStore
import com.alexanderlingle.berry.notify.BerryNotifier
import com.alexanderlingle.berry.notify.PredictionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * v1 -> v2: child profiles. Creates the children table with a default "Baby"
 * row (id 1) and tags every existing entry to that child.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `children` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `photoPath` TEXT, " +
                "`birthdateMillis` INTEGER, `createdAtMillis` INTEGER NOT NULL)"
        )
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO `children` (`name`, `photoPath`, `birthdateMillis`, `createdAtMillis`) " +
                "VALUES ('Baby', NULL, NULL, $now)"
        )
        db.execSQL("ALTER TABLE `sleep_entries` ADD COLUMN `childId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `feeding_entries` ADD COLUMN `childId` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `diaper_entries` ADD COLUMN `childId` INTEGER NOT NULL DEFAULT 1")
    }
}

/** Holds the Room database, child store, and repository for the whole app. */
class BerryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Phase 3: notification channel + the 30-minute prediction checks.
        BerryNotifier.createChannel(this)
        PredictionScheduler.schedule(this)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: BerryDatabase by lazy {
        Room.databaseBuilder(this, BerryDatabase::class.java, "berry.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    private val childStore: ChildStore by lazy { ChildStore(this) }

    val repository: BerryRepository by lazy {
        BerryRepository(database, childStore, appScope)
    }
}
