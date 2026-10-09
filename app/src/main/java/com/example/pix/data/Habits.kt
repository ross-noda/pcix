package com.example.pix.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "habit_groups")
data class HabitGroupEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String,
    val sortOrder: Long = 0, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)

@Entity(tableName = "habits", foreignKeys = [ForeignKey(entity = HabitGroupEntity::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.SET_NULL)], indices = [Index("groupId")])
data class HabitEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String,
    val icon: String = "REPEAT", val color: Int = 0, val groupId: String? = null, val notes: String = "",
    val csvId: String? = null, @ColumnInfo(defaultValue = "'rep'") val unit: String = "rep",
    val active: Boolean = true, val sortOrder: Long = 0, val reminderMinute: Int? = null,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt)

/** A revision applies from effectiveDay; editing never changes earlier scheduled days or targets. */
@Entity(tableName = "habit_rules", foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)], indices = [Index("habitId"), Index(value = ["habitId", "effectiveDay"], unique = true)])
data class HabitRuleEntity(@PrimaryKey val id: String = UUID.randomUUID().toString(), val habitId: String,
    val effectiveDay: Long, val startDay: Long = effectiveDay, val endDay: Long? = null,
    val quantity: Boolean = false, val target: Int = 1, val step: Int = 1,
    val weekdays: Int = 127, val intervalDays: Int = 1, val enabled: Boolean = true, val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "habit_logs", foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)], indices = [Index("habitId"), Index(value = ["habitId", "day"], unique = true), Index("day")])
data class HabitLogEntity(@PrimaryKey val id: String, val habitId: String, val day: Long,
    val count: Int = 0, val skipped: Boolean = false, val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt, val sourceStatus: String? = null)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder,createdAt,id") fun observeHabits(): Flow<List<HabitEntity>>
    @Query("SELECT * FROM habit_groups ORDER BY sortOrder,createdAt,id") fun observeGroups(): Flow<List<HabitGroupEntity>>
    @Query("SELECT * FROM habit_rules ORDER BY effectiveDay") fun observeRules(): Flow<List<HabitRuleEntity>>
    @Query("SELECT * FROM habit_logs WHERE day=:day") fun observeDay(day: Long): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM habit_logs WHERE habitId=:id ORDER BY day") fun observeHistory(id: String): Flow<List<HabitLogEntity>>
    @Query("SELECT * FROM habits") suspend fun habits(): List<HabitEntity>
    @Query("SELECT * FROM habit_groups") suspend fun groups(): List<HabitGroupEntity>
    @Query("SELECT * FROM habit_rules ORDER BY effectiveDay") suspend fun rules(): List<HabitRuleEntity>
    @Query("SELECT * FROM habit_logs") suspend fun logs(): List<HabitLogEntity>
    @Query("SELECT * FROM habits WHERE id=:id") suspend fun habit(id: String): HabitEntity?
    @Query("SELECT * FROM habit_logs WHERE habitId=:id AND day=:day") suspend fun log(id: String, day: Long): HabitLogEntity?
    @Upsert suspend fun save(row: HabitEntity)
    @Upsert suspend fun save(row: HabitGroupEntity)
    @Upsert suspend fun save(row: HabitRuleEntity)
    @Upsert suspend fun save(row: HabitLogEntity)
    @Query("DELETE FROM habits WHERE id=:id") suspend fun deleteHabit(id: String)
    @Query("DELETE FROM habit_groups WHERE id=:id") suspend fun deleteGroup(id: String)
    @Query("DELETE FROM habit_rules WHERE id=:id") suspend fun deleteRule(id: String)
    @Query("DELETE FROM habit_logs WHERE id=:id") suspend fun deleteLog(id: String)
    @Query("DELETE FROM habits") suspend fun clearHabits()
    @Query("DELETE FROM habit_groups") suspend fun clearGroups()
}
