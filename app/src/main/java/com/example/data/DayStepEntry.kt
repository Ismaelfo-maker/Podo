package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_steps")
data class DayStepEntry(
    @PrimaryKey val date: String,
    val steps: Int,
    val goalMet: Boolean
)
