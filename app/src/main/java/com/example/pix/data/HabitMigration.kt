package com.example.pix.data

object HabitMigration : androidx.room.migration.Migration(10, 11) {
 override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS habit_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS habits (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER NOT NULL, groupId TEXT, notes TEXT NOT NULL, active INTEGER NOT NULL, sortOrder INTEGER NOT NULL, reminderMinute INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(groupId) REFERENCES habit_groups(id) ON DELETE SET NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS habit_rules (id TEXT NOT NULL PRIMARY KEY, habitId TEXT NOT NULL, effectiveDay INTEGER NOT NULL, startDay INTEGER NOT NULL, endDay INTEGER, quantity INTEGER NOT NULL, target INTEGER NOT NULL, step INTEGER NOT NULL, weekdays INTEGER NOT NULL, intervalDays INTEGER NOT NULL, enabled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE)")
  db.execSQL("CREATE TABLE IF NOT EXISTS habit_logs (id TEXT NOT NULL PRIMARY KEY, habitId TEXT NOT NULL, day INTEGER NOT NULL, count INTEGER NOT NULL, skipped INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, FOREIGN KEY(habitId) REFERENCES habits(id) ON DELETE CASCADE)")
  db.execSQL("CREATE INDEX index_habits_groupId ON habits(groupId)")
  db.execSQL("CREATE INDEX index_habit_rules_habitId ON habit_rules(habitId)")
  db.execSQL("CREATE UNIQUE INDEX index_habit_rules_habitId_effectiveDay ON habit_rules(habitId,effectiveDay)")
  db.execSQL("CREATE INDEX index_habit_logs_habitId ON habit_logs(habitId)")
  db.execSQL("CREATE UNIQUE INDEX index_habit_logs_habitId_day ON habit_logs(habitId,day)")
  db.execSQL("CREATE INDEX index_habit_logs_day ON habit_logs(day)")
 }
}
