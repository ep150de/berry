package com.alexanderlingle.berry.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Child-aware data-access layer. Every entry query is scoped to a child;
 * [selectedChildId] always resolves to a real child (or -1 when none exists yet).
 */
class BerryRepository(
    db: BerryDatabase,
    private val childStore: ChildStore,
    scope: CoroutineScope
) {
    private val childDao = db.childDao()
    private val sleepDao = db.sleepDao()
    private val feedingDao = db.feedingDao()
    private val diaperDao = db.diaperDao()

    val children: Flow<List<Child>> = childDao.observeAll()

    val selectedChildId: StateFlow<Long> =
        combine(children, childStore.selectedChildId) { list, sel ->
            list.find { it.id == sel }?.id ?: list.firstOrNull()?.id ?: -1L
        }.stateIn(scope, SharingStarted.Eagerly, -1L)

    val selectedChild: StateFlow<Child?> =
        combine(children, selectedChildId) { list, id ->
            list.find { it.id == id }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    fun sleepEntries(childId: Long): Flow<List<SleepEntry>> = sleepDao.observeAll(childId)
    fun feedingEntries(childId: Long): Flow<List<FeedingEntry>> = feedingDao.observeAll(childId)
    fun diaperEntries(childId: Long): Flow<List<DiaperEntry>> = diaperDao.observeAll(childId)

    fun runningSleep(childId: Long): Flow<SleepEntry?> = sleepDao.observeRunning(childId)
    fun runningFeeding(childId: Long): Flow<FeedingEntry?> = feedingDao.observeRunning(childId)

    suspend fun insertSleep(entry: SleepEntry): Long = sleepDao.insert(entry)
    suspend fun updateSleep(entry: SleepEntry) = sleepDao.update(entry)
    suspend fun deleteSleep(entry: SleepEntry) = sleepDao.delete(entry)

    suspend fun insertFeeding(entry: FeedingEntry): Long = feedingDao.insert(entry)
    suspend fun updateFeeding(entry: FeedingEntry) = feedingDao.update(entry)
    suspend fun deleteFeeding(entry: FeedingEntry) = feedingDao.delete(entry)

    suspend fun insertDiaper(entry: DiaperEntry): Long = diaperDao.insert(entry)
    suspend fun updateDiaper(entry: DiaperEntry) = diaperDao.update(entry)
    suspend fun deleteDiaper(entry: DiaperEntry) = diaperDao.delete(entry)

    /** Creates the default profile on first launch. */
    suspend fun ensureDefaultChild() {
        if (childDao.count() == 0L) {
            val id = childDao.insert(Child(name = "Baby"))
            childStore.setSelectedChildId(id)
        }
    }

    suspend fun addChild(child: Child): Long {
        val id = childDao.insert(child)
        childStore.setSelectedChildId(id)
        return id
    }

    suspend fun updateChild(child: Child) = childDao.update(child)

    suspend fun deleteChild(child: Child) {
        sleepDao.deleteForChild(child.id)
        feedingDao.deleteForChild(child.id)
        diaperDao.deleteForChild(child.id)
        childDao.delete(child)
    }

    suspend fun selectChild(id: Long) = childStore.setSelectedChildId(id)
}
