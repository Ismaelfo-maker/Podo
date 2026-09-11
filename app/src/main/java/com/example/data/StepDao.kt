package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Query("SELECT * FROM daily_steps WHERE date = :date")
    fun getEntryFlowByDate(date: String): Flow<DayStepEntry?>

    @Query("SELECT * FROM daily_steps WHERE date = :date")
    suspend fun getEntryByDate(date: String): DayStepEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entry: DayStepEntry)

    @Query("SELECT * FROM daily_steps ORDER BY date DESC")
    fun getAllEntries(): Flow<List<DayStepEntry>>

    @Query("SELECT * FROM daily_steps WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC")
    fun getEntriesInRange(startDate: String, endDate: String): Flow<List<DayStepEntry>>

    @Query("DELETE FROM daily_steps WHERE date = :date")
    suspend fun deleteByDate(date: String)
}
