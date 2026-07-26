package com.notes.gymhelper

/**
 * One workout day downloaded from the Google Sheets "Days" tab.
 *
 * Example:
 * Day 1:
 * - Warm up
 * - Back
 * - Chest
 * - Shoulders
 */
data class WorkoutDay(
    val dayNumber: Int,
    val categories: List<String>,
    val isCompleted: Boolean = false
)

/**
 * One exercise downloaded from the Google Sheets "Exercises" tab.
 *
 * totalSets is the minimum number of sets.
 * maximumSets is the maximum number of sets.
 *
 * Examples:
 *
 * Fixed 3 sets:
 * totalSets = 3
 * maximumSets = 3
 *
 * Range 6-8:
 * totalSets = 6
 * maximumSets = 8
 */
data class Exercise(
    val id: Int = 0,
    val category: String,
    val name: String,
    val target: String,
    val totalSets: Int,
    val maximumSets: Int = totalSets,
    val weight: Double? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val rest: String? = null,
    val notes: String? = null,
    val order: Int = 0
) {
    init {
        require(totalSets > 0) {
            "Minimum sets must be greater than zero."
        }

        require(maximumSets >= totalSets) {
            "Maximum sets cannot be smaller than minimum sets."
        }
    }

    /**
     * Text used by the UI.
     *
     * Examples:
     * 3
     * 6-8
     */
    val setRangeLabel: String
        get() {
            return if (totalSets == maximumSets) {
                totalSets.toString()
            } else {
                "$totalSets-$maximumSets"
            }
        }
}

/**
 * A category and the detailed exercises that belong to it.
 *
 * Example:
 *
 * Back:
 * - T-Bar Row
 * - Barbell Row
 * - Lat Pulldown
 */
data class WorkoutCategory(
    val name: String,
    val exercises: List<Exercise>
)

/**
 * Complete information for one workout day.
 */
data class DailyWorkout(
    val day: WorkoutDay,
    val categories: List<WorkoutCategory>
)

/**
 * Tracks the user's position while completing a workout.
 */
data class WorkoutProgress(
    val dayNumber: Int,
    val categoryIndex: Int = 0,
    val exerciseIndex: Int = 0,
    val currentSet: Int = 1,
    val completed: Boolean = false
)

/**
 * One workout session.
 *
 * A new session is created whenever the user presses
 * "Start Workout".
 *
 * Example:
 * Session 15 belongs to Day 2 and started at a particular time.
 */
data class WorkoutSession(
    val id: Long = 0L,
    val dayNumber: Int,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val isCompleted: Boolean = false
)

/**
 * One completed set saved on the phone.
 *
 * One row is inserted only when the user presses:
 *
 * - Next Set
 * - Next Exercise
 * - Finish Workout
 */
data class WorkoutSetRecord(
    val id: Long = 0L,

    /**
     * Connects this set to one workout session.
     */
    val sessionId: Long,

    /**
     * Workout day where the set was performed.
     */
    val dayNumber: Int,

    /**
     * Category such as Back, Chest, or Arms.
     */
    val categoryName: String,

    /**
     * Exercise such as T-Bar Row.
     */
    val exerciseName: String,

    /**
     * Set number, not repetition number.
     *
     * Example:
     * Set 1
     * Set 2
     * Set 3
     */
    val setNumber: Int,

    /**
     * Actual repetitions performed by the user.
     *
     * Nullable because exercises such as running, stretching,
     * and timed intervals may not use repetitions.
     */
    val completedReps: Int?,

    /**
     * Weight used during this exact set.
     *
     * Nullable for bodyweight, cardio, mobility, and stretching.
     */
    val weight: Double?,

    /**
     * KG or LB for the saved weight.
     */
    val weightUnit: WeightUnit = WeightUnit.KG,

    /**
     * Original target downloaded from the spreadsheet.
     *
     * Examples:
     * "10-12"
     * "Failure"
     * "30 seconds"
     */
    val target: String,

    /**
     * Time when the user submitted this set.
     */
    val completedAt: Long = System.currentTimeMillis()
)

/**
 * Used when parsing ranges such as 6-8 from Google Sheets.
 */
data class SetRange(
    val minimum: Int,
    val maximum: Int
)

/**
 * Converts spreadsheet text into a set range.
 *
 * Supported examples:
 *
 * "3"      -> minimum 3, maximum 3
 * "6-8"    -> minimum 6, maximum 8
 * "6 – 8"  -> minimum 6, maximum 8
 * "6 to 8" -> minimum 6, maximum 8
 */
fun parseSetRange(rawValue: String): SetRange {
    val numbers = Regex("\\d+")
        .findAll(rawValue)
        .mapNotNull { match ->
            match.value.toIntOrNull()
        }
        .toList()

    if (numbers.isEmpty()) {
        return SetRange(
            minimum = 1,
            maximum = 1
        )
    }

    if (numbers.size == 1) {
        val value = numbers.first().coerceAtLeast(1)

        return SetRange(
            minimum = value,
            maximum = value
        )
    }

    val firstValue = numbers[0].coerceAtLeast(1)
    val secondValue = numbers[1].coerceAtLeast(1)

    return SetRange(
        minimum = minOf(firstValue, secondValue),
        maximum = maxOf(firstValue, secondValue)
    )
}

/**
 * Supported weight units.
 */
enum class WeightUnit {
    KG,
    LB
}