package com.alexanderlingle.berry.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY createdAtMillis ASC")
    fun observeAll(): Flow<List<Child>>

    @Query("SELECT COUNT(*) FROM children")
    suspend fun count(): Long

    @Insert
    suspend fun insert(child: Child): Long

    @Update
    suspend fun update(child: Child)

    @Delete
    suspend fun delete(child: Child)
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_entries WHERE childId = :childId ORDER BY startTimeMillis DESC")
    fun observeAll(childId: Long): Flow<List<SleepEntry>>

    @Query("SELECT * FROM sleep_entries WHERE childId = :childId AND endTimeMillis IS NULL ORDER BY startTimeMillis DESC LIMIT 1")
    fun observeRunning(childId: Long): Flow<SleepEntry?>

    @Query("DELETE FROM sleep_entries WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)

    @Insert
    suspend fun insert(entry: SleepEntry): Long

    @Update
    suspend fun update(entry: SleepEntry)

    @Delete
    suspend fun delete(entry: SleepEntry)
}

@Dao
interface FeedingDao {
    @Query("SELECT * FROM feeding_entries WHERE childId = :childId ORDER BY startTimeMillis DESC")
    fun observeAll(childId: Long): Flow<List<FeedingEntry>>

    @Query("SELECT * FROM feeding_entries WHERE childId = :childId AND endTimeMillis IS NULL AND type != 'BOTTLE' ORDER BY startTimeMillis DESC LIMIT 1")
    fun observeRunning(childId: Long): Flow<FeedingEntry?>

    @Query("DELETE FROM feeding_entries WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)

    @Insert
    suspend fun insert(entry: FeedingEntry): Long

    @Update
    suspend fun update(entry: FeedingEntry)

    @Delete
    suspend fun delete(entry: FeedingEntry)
}

@Dao
interface DiaperDao {
    @Query("SELECT * FROM diaper_entries WHERE childId = :childId ORDER BY timeMillis DESC")
    fun observeAll(childId: Long): Flow<List<DiaperEntry>>

    @Query("DELETE FROM diaper_entries WHERE childId = :childId")
    suspend fun deleteForChild(childId: Long)

    @Insert
    suspend fun insert(entry: DiaperEntry): Long

    @Update
    suspend fun update(entry: DiaperEntry)

    @Delete
    suspend fun delete(entry: DiaperEntry)
}
