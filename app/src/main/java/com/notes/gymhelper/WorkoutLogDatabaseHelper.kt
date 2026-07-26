package com.notes.gymhelper

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class WorkoutLogDatabaseHelper(
    context: Context
) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "workout_history.db"
        private const val DATABASE_VERSION = 1

        // --------------------------------------------
        // workout_sessions table
        // --------------------------------------------

        private const val TABLE_SESSIONS = "workout_sessions"

        private const val COLUMN_SESSION_ID = "session_id"
        private const val COLUMN_SESSION_DAY_NUMBER = "day_number"
        private const val COLUMN_SESSION_STARTED_AT = "started_at"
        private const val COLUMN_SESSION_FINISHED_AT = "finished_at"
        private const val COLUMN_SESSION_IS_COMPLETED = "is_completed"

        // --------------------------------------------
        // workout_set_records table
        // --------------------------------------------

        private const val TABLE_SET_RECORDS = "workout_set_records"

        private const val COLUMN_RECORD_ID = "record_id"
        private const val COLUMN_RECORD_SESSION_ID = "session_id"
        private const val COLUMN_RECORD_DAY_NUMBER = "day_number"
        private const val COLUMN_RECORD_CATEGORY_NAME = "category_name"
        private const val COLUMN_RECORD_EXERCISE_NAME = "exercise_name"
        private const val COLUMN_RECORD_SET_NUMBER = "set_number"
        private const val COLUMN_RECORD_COMPLETED_REPS = "completed_reps"
        private const val COLUMN_RECORD_WEIGHT = "weight"
        private const val COLUMN_RECORD_WEIGHT_UNIT = "weight_unit"
        private const val COLUMN_RECORD_TARGET = "target"
        private const val COLUMN_RECORD_COMPLETED_AT = "completed_at"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)

        /*
         * Enables foreign-key rules.
         *
         * If a workout session is deleted, its completed-set
         * records are deleted automatically.
         */
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSessionsTable = """
            CREATE TABLE $TABLE_SESSIONS (
                $COLUMN_SESSION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_DAY_NUMBER INTEGER NOT NULL,
                $COLUMN_SESSION_STARTED_AT INTEGER NOT NULL,
                $COLUMN_SESSION_FINISHED_AT INTEGER,
                $COLUMN_SESSION_IS_COMPLETED INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        val createSetRecordsTable = """
            CREATE TABLE $TABLE_SET_RECORDS (
                $COLUMN_RECORD_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_RECORD_SESSION_ID INTEGER NOT NULL,
                $COLUMN_RECORD_DAY_NUMBER INTEGER NOT NULL,
                $COLUMN_RECORD_CATEGORY_NAME TEXT NOT NULL,
                $COLUMN_RECORD_EXERCISE_NAME TEXT NOT NULL,
                $COLUMN_RECORD_SET_NUMBER INTEGER NOT NULL,
                $COLUMN_RECORD_COMPLETED_REPS INTEGER,
                $COLUMN_RECORD_WEIGHT REAL,
                $COLUMN_RECORD_WEIGHT_UNIT TEXT NOT NULL,
                $COLUMN_RECORD_TARGET TEXT NOT NULL,
                $COLUMN_RECORD_COMPLETED_AT INTEGER NOT NULL,

                FOREIGN KEY ($COLUMN_RECORD_SESSION_ID)
                    REFERENCES $TABLE_SESSIONS($COLUMN_SESSION_ID)
                    ON DELETE CASCADE,

                UNIQUE (
                    $COLUMN_RECORD_SESSION_ID,
                    $COLUMN_RECORD_CATEGORY_NAME,
                    $COLUMN_RECORD_EXERCISE_NAME,
                    $COLUMN_RECORD_SET_NUMBER
                )
            )
        """.trimIndent()

        db.execSQL(createSessionsTable)
        db.execSQL(createSetRecordsTable)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SET_RECORDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")

        onCreate(db)
    }

    // ============================================================
    // SESSION FUNCTIONS
    // ============================================================

    /**
     * Creates a new workout session.
     *
     * Call this when the user presses Start Workout.
     *
     * Returns the new session ID.
     *
     * Returns -1 if the session could not be created.
     */
    fun startWorkoutSession(
        dayNumber: Int
    ): Long {
        val db = writableDatabase

        val values = ContentValues().apply {
            put(
                COLUMN_SESSION_DAY_NUMBER,
                dayNumber
            )

            put(
                COLUMN_SESSION_STARTED_AT,
                System.currentTimeMillis()
            )

            putNull(
                COLUMN_SESSION_FINISHED_AT
            )

            put(
                COLUMN_SESSION_IS_COMPLETED,
                0
            )
        }

        return db.insert(
            TABLE_SESSIONS,
            null,
            values
        )
    }

    /**
     * Marks a workout session as completed.
     *
     * Call this after the user submits the final set of
     * the final exercise.
     */
    fun finishWorkoutSession(
        sessionId: Long
    ): Boolean {
        val db = writableDatabase

        val values = ContentValues().apply {
            put(
                COLUMN_SESSION_FINISHED_AT,
                System.currentTimeMillis()
            )

            put(
                COLUMN_SESSION_IS_COMPLETED,
                1
            )
        }

        val updatedRows = db.update(
            TABLE_SESSIONS,
            values,
            "$COLUMN_SESSION_ID = ?",
            arrayOf(sessionId.toString())
        )

        return updatedRows > 0
    }

    /**
     * Returns one workout session.
     */
    fun getWorkoutSession(
        sessionId: Long
    ): WorkoutSession? {
        val db = readableDatabase

        val cursor = db.query(
            TABLE_SESSIONS,
            null,
            "$COLUMN_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            null
        )

        cursor.use {
            return if (it.moveToFirst()) {
                cursorToWorkoutSession(it)
            } else {
                null
            }
        }
    }

    /**
     * Returns every workout session.
     *
     * Newest sessions appear first.
     */
    fun getAllWorkoutSessions(): List<WorkoutSession> {
        val sessions = mutableListOf<WorkoutSession>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_SESSION_STARTED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                sessions.add(
                    cursorToWorkoutSession(it)
                )
            }
        }

        return sessions
    }

    /**
     * Returns the newest session for a particular workout day.
     */
    fun getLatestSessionForDay(
        dayNumber: Int
    ): WorkoutSession? {
        val db = readableDatabase

        val cursor = db.query(
            TABLE_SESSIONS,
            null,
            "$COLUMN_SESSION_DAY_NUMBER = ?",
            arrayOf(dayNumber.toString()),
            null,
            null,
            "$COLUMN_SESSION_STARTED_AT DESC",
            "1"
        )

        cursor.use {
            return if (it.moveToFirst()) {
                cursorToWorkoutSession(it)
            } else {
                null
            }
        }
    }

    // ============================================================
    // SET-RECORD FUNCTIONS
    // ============================================================

    /**
     * Saves one submitted set.
     *
     * Call this only when the user presses:
     *
     * - Next Set
     * - Next Exercise
     * - Finish Workout
     *
     * Typing in the fields does not save anything.
     */
    fun saveSetRecord(
        record: WorkoutSetRecord
    ): Long {
        val db = writableDatabase

        val values = ContentValues().apply {
            put(
                COLUMN_RECORD_SESSION_ID,
                record.sessionId
            )

            put(
                COLUMN_RECORD_DAY_NUMBER,
                record.dayNumber
            )

            put(
                COLUMN_RECORD_CATEGORY_NAME,
                record.categoryName.trim()
            )

            put(
                COLUMN_RECORD_EXERCISE_NAME,
                record.exerciseName.trim()
            )

            put(
                COLUMN_RECORD_SET_NUMBER,
                record.setNumber
            )

            if (record.completedReps == null) {
                putNull(
                    COLUMN_RECORD_COMPLETED_REPS
                )
            } else {
                put(
                    COLUMN_RECORD_COMPLETED_REPS,
                    record.completedReps
                )
            }

            if (record.weight == null) {
                putNull(
                    COLUMN_RECORD_WEIGHT
                )
            } else {
                put(
                    COLUMN_RECORD_WEIGHT,
                    record.weight
                )
            }

            put(
                COLUMN_RECORD_WEIGHT_UNIT,
                record.weightUnit.name
            )

            put(
                COLUMN_RECORD_TARGET,
                record.target
            )

            put(
                COLUMN_RECORD_COMPLETED_AT,
                record.completedAt
            )
        }

        /*
         * REPLACE prevents the same set from being stored twice.
         *
         * Example:
         * Session 4
         * T-Bar Row
         * Set 1
         *
         * If it is accidentally submitted twice, the old row
         * is replaced instead of creating a duplicate.
         */
        return db.insertWithOnConflict(
            TABLE_SET_RECORDS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Returns all submitted sets for one workout session.
     *
     * The records appear in the order they were submitted.
     */
    fun getSetRecordsForSession(
        sessionId: Long
    ): List<WorkoutSetRecord> {
        val records = mutableListOf<WorkoutSetRecord>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_SET_RECORDS,
            null,
            "$COLUMN_RECORD_SESSION_ID = ?",
            arrayOf(sessionId.toString()),
            null,
            null,
            "$COLUMN_RECORD_COMPLETED_AT ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                records.add(
                    cursorToSetRecord(it)
                )
            }
        }

        return records
    }

    /**
     * Returns all submitted sets for one exercise in a session.
     */
    fun getSetRecordsForExercise(
        sessionId: Long,
        categoryName: String,
        exerciseName: String
    ): List<WorkoutSetRecord> {
        val records = mutableListOf<WorkoutSetRecord>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_SET_RECORDS,
            null,
            """
                $COLUMN_RECORD_SESSION_ID = ?
                AND $COLUMN_RECORD_CATEGORY_NAME = ?
                AND $COLUMN_RECORD_EXERCISE_NAME = ?
            """.trimIndent(),
            arrayOf(
                sessionId.toString(),
                categoryName.trim(),
                exerciseName.trim()
            ),
            null,
            null,
            "$COLUMN_RECORD_SET_NUMBER ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                records.add(
                    cursorToSetRecord(it)
                )
            }
        }

        return records
    }

    /**
     * Returns the newest saved weight for an exercise.
     *
     * This allows the app to suggest the last weight used
     * the next time that exercise appears.
     */
    fun getLastUsedWeight(
        exerciseName: String
    ): Pair<Double, WeightUnit>? {
        val db = readableDatabase

        val cursor = db.query(
            TABLE_SET_RECORDS,
            arrayOf(
                COLUMN_RECORD_WEIGHT,
                COLUMN_RECORD_WEIGHT_UNIT
            ),
            """
                $COLUMN_RECORD_EXERCISE_NAME = ?
                AND $COLUMN_RECORD_WEIGHT IS NOT NULL
            """.trimIndent(),
            arrayOf(exerciseName.trim()),
            null,
            null,
            "$COLUMN_RECORD_COMPLETED_AT DESC",
            "1"
        )

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }

            val weight = it.getDouble(
                it.getColumnIndexOrThrow(
                    COLUMN_RECORD_WEIGHT
                )
            )

            val unitText = it.getString(
                it.getColumnIndexOrThrow(
                    COLUMN_RECORD_WEIGHT_UNIT
                )
            )

            return weight to parseWeightUnit(unitText)
        }
    }

    /**
     * Checks whether one particular set was already submitted.
     */
    fun setRecordExists(
        sessionId: Long,
        categoryName: String,
        exerciseName: String,
        setNumber: Int
    ): Boolean {
        val db = readableDatabase

        val cursor = db.rawQuery(
            """
                SELECT COUNT(*)
                FROM $TABLE_SET_RECORDS
                WHERE $COLUMN_RECORD_SESSION_ID = ?
                AND $COLUMN_RECORD_CATEGORY_NAME = ?
                AND $COLUMN_RECORD_EXERCISE_NAME = ?
                AND $COLUMN_RECORD_SET_NUMBER = ?
            """.trimIndent(),
            arrayOf(
                sessionId.toString(),
                categoryName.trim(),
                exerciseName.trim(),
                setNumber.toString()
            )
        )

        cursor.use {
            return it.moveToFirst() &&
                    it.getInt(0) > 0
        }
    }

    // ============================================================
    // DELETE FUNCTIONS
    // ============================================================

    /**
     * Deletes one workout session.
     *
     * The connected set records are also deleted automatically
     * because of ON DELETE CASCADE.
     */
    fun deleteWorkoutSession(
        sessionId: Long
    ): Boolean {
        val db = writableDatabase

        val deletedRows = db.delete(
            TABLE_SESSIONS,
            "$COLUMN_SESSION_ID = ?",
            arrayOf(sessionId.toString())
        )

        return deletedRows > 0
    }

    /**
     * Deletes all local workout history.
     */
    fun deleteAllWorkoutHistory(): Boolean {
        val db = writableDatabase

        db.beginTransaction()

        return try {
            db.delete(
                TABLE_SET_RECORDS,
                null,
                null
            )

            db.delete(
                TABLE_SESSIONS,
                null,
                null
            )

            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    // ============================================================
    // CURSOR CONVERSION
    // ============================================================

    private fun cursorToWorkoutSession(
        cursor: Cursor
    ): WorkoutSession {
        val finishedAtIndex =
            cursor.getColumnIndexOrThrow(
                COLUMN_SESSION_FINISHED_AT
            )

        val finishedAt = if (
            cursor.isNull(finishedAtIndex)
        ) {
            null
        } else {
            cursor.getLong(finishedAtIndex)
        }

        return WorkoutSession(
            id = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    COLUMN_SESSION_ID
                )
            ),

            dayNumber = cursor.getInt(
                cursor.getColumnIndexOrThrow(
                    COLUMN_SESSION_DAY_NUMBER
                )
            ),

            startedAt = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    COLUMN_SESSION_STARTED_AT
                )
            ),

            finishedAt = finishedAt,

            isCompleted = cursor.getInt(
                cursor.getColumnIndexOrThrow(
                    COLUMN_SESSION_IS_COMPLETED
                )
            ) == 1
        )
    }

    private fun cursorToSetRecord(
        cursor: Cursor
    ): WorkoutSetRecord {
        val repsIndex =
            cursor.getColumnIndexOrThrow(
                COLUMN_RECORD_COMPLETED_REPS
            )

        val completedReps = if (
            cursor.isNull(repsIndex)
        ) {
            null
        } else {
            cursor.getInt(repsIndex)
        }

        val weightIndex =
            cursor.getColumnIndexOrThrow(
                COLUMN_RECORD_WEIGHT
            )

        val weight = if (
            cursor.isNull(weightIndex)
        ) {
            null
        } else {
            cursor.getDouble(weightIndex)
        }

        return WorkoutSetRecord(
            id = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    COLUMN_RECORD_ID
                )
            ),

            sessionId = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    COLUMN_RECORD_SESSION_ID
                )
            ),

            dayNumber = cursor.getInt(
                cursor.getColumnIndexOrThrow(
                    COLUMN_RECORD_DAY_NUMBER
                )
            ),

            categoryName = cursor.getString(
                cursor.getColumnIndexOrThrow(
                    COLUMN_RECORD_CATEGORY_NAME
                )
            ),

            exerciseName = cursor.getString(
                cursor.getColumnIndexOrThrow(
                    COLUMN_RECORD_EXERCISE_NAME
                )
            ),

            setNumber = cursor.getInt(
                cursor.getColumnIndexOrThrow(
                    COLUMN_RECORD_SET_NUMBER
                )
            ),

            completedReps = completedReps,

            weight = weight,

            weightUnit = parseWeightUnit(
                cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        COLUMN_RECORD_WEIGHT_UNIT
                    )
                )
            ),

            target = cursor.getString(
                cursor.getColumnIndexOrThrow(
                    COLUMN_RECORD_TARGET
                )
            ),

            completedAt = cursor.getLong(
                cursor.getColumnIndexOrThrow(
                    COLUMN_RECORD_COMPLETED_AT
                )
            )
        )
    }

    private fun parseWeightUnit(
        value: String?
    ): WeightUnit {
        return runCatching {
            WeightUnit.valueOf(
                value
                    .orEmpty()
                    .uppercase()
            )
        }.getOrDefault(
            WeightUnit.KG
        )
    }
}