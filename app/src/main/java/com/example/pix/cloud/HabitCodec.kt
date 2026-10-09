package com.example.pix.cloud

import com.example.pix.data.*
import org.json.JSONObject

object HabitCodec {
 fun habitGroup(row: HabitGroupEntity) = JSONObject()
  .put("id", row.id)
  .put("name", row.name)
  .put("sort_order", row.sortOrder)
  .put("created_at", row.createdAt)
  .put("updated_at", row.updatedAt)
 fun parseHabitGroup(row: JSONObject) = HabitGroupEntity(
  id = row.getString("id"),
  name = row.getString("name"),
  sortOrder = row.getLong("sort_order"),
  createdAt = row.getLong("created_at"),
  updatedAt = row.getLong("updated_at"),
 )
 fun habit(row: HabitEntity) = JSONObject()
  .put("id", row.id)
  .put("name", row.name)
  .put("icon", row.icon)
  .put("color", row.color)
  .put("group_id", row.groupId ?: JSONObject.NULL)
  .put("notes", row.notes)
  .put("csv_id", row.csvId ?: JSONObject.NULL)
  .put("unit", row.unit)
  .put("active", row.active)
  .put("sort_order", row.sortOrder)
  .put("reminder_minute", row.reminderMinute ?: JSONObject.NULL)
  .put("created_at", row.createdAt)
  .put("updated_at", row.updatedAt)
 fun parseHabit(row: JSONObject) = HabitEntity(
  id = row.getString("id"),
  name = row.getString("name"),
  icon = row.getString("icon"),
  color = row.getInt("color"),
  groupId = row.nullableString("group_id"),
  notes = row.getString("notes"),
  csvId = row.nullableString("csv_id"),
  unit = row.optString("unit", "rep"),
  active = row.getBoolean("active"),
  sortOrder = row.getLong("sort_order"),
  reminderMinute = row.nullableInt("reminder_minute"),
  createdAt = row.getLong("created_at"),
  updatedAt = row.getLong("updated_at"),
 )
 fun habitRule(row: HabitRuleEntity) = JSONObject()
  .put("id", row.id)
  .put("habit_id", row.habitId)
  .put("effective_day", row.effectiveDay)
  .put("start_day", row.startDay)
  .put("end_day", row.endDay ?: JSONObject.NULL)
  .put("quantity", row.quantity)
  .put("target", row.target)
  .put("step", row.step)
  .put("weekdays", row.weekdays)
  .put("interval_days", row.intervalDays)
  .put("enabled", row.enabled)
  .put("updated_at", row.updatedAt)
 fun parseHabitRule(row: JSONObject) = HabitRuleEntity(
  id = row.getString("id"),
  habitId = row.getString("habit_id"),
  effectiveDay = row.getLong("effective_day"),
  startDay = row.getLong("start_day"),
  endDay = row.nullableLong("end_day"),
  quantity = row.getBoolean("quantity"),
  target = row.getInt("target"),
  step = row.getInt("step"),
  weekdays = row.getInt("weekdays"),
  intervalDays = row.getInt("interval_days"),
  enabled = row.getBoolean("enabled"),
  updatedAt = row.getLong("updated_at"),
 )
 fun habitLog(row: HabitLogEntity) = JSONObject()
  .put("id", row.id)
  .put("habit_id", row.habitId)
  .put("day", row.day)
  .put("count", row.count)
  .put("skipped", row.skipped)
  .put("source_status", row.sourceStatus ?: JSONObject.NULL)
  .put("created_at", row.createdAt)
  .put("updated_at", row.updatedAt)
 fun parseHabitLog(row: JSONObject) = HabitLogEntity(
  id = row.getString("id"),
  habitId = row.getString("habit_id"),
  day = row.getLong("day"),
  count = row.getInt("count"),
  skipped = row.getBoolean("skipped"),
  sourceStatus = if (row.has("source_status") && !row.isNull("source_status")) row.getString("source_status") else null,
  createdAt = row.getLong("created_at"),
  updatedAt = row.getLong("updated_at"),
 )
}
