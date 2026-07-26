package com.notes.gymhelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/**
 * Reads workout information directly from Google Sheets.
 *
 * Data flow:
 *
 * Google Sheets
 *      ↓
 * GoogleSheetsDataSource
 *      ↓
 * WorkoutHomeScreen / ExerciseScreen
 *
 * SQLite is used only for local workout history,
 * not as the main source of workout information.
 */
object GoogleSheetsDataSource {

    private val spreadsheetId: String
        get() = BuildConfig.SPREADSHEET_ID

    private val apiKey: String
        get() = BuildConfig.SHEETS_API_KEY

    /*
     * Days sheet:
     *
     * A = Day
     * B = Exercise 1
     * C = Exercise 2
     * D = Exercise 3
     * E = Exercise 4
     * F = Exercise 5
     * G = Completed checkbox
     * H = Date
     */
    private const val DAYS_RANGE = "Days!A2:H"

    /*
     * Exercises sheet:
     *
     * The sheet contains category headings followed by:
     *
     * Exercise | Reps | Sets | Note | Extra note | Extra note
     */
    private const val EXERCISES_RANGE = "Exercises!A:F"

    /**
     * Downloads all workout days.
     */
    suspend fun downloadWorkoutDays(): Result<List<WorkoutDay>> {
        return try {
            validateConfiguration()

            val response = downloadRange(DAYS_RANGE)
            val days = parseWorkoutDays(response)

            if (days.isEmpty()) {
                Result.failure(
                    IllegalStateException(
                        "No valid workout days were found in the Days sheet."
                    )
                )
            } else {
                Result.success(days)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /**
     * Downloads every detailed exercise from the Exercises sheet.
     */
    suspend fun downloadExercises(): Result<List<Exercise>> {
        return try {
            validateConfiguration()

            val response = downloadRange(EXERCISES_RANGE)
            val exercises = parseExercises(response)

            if (exercises.isEmpty()) {
                Result.failure(
                    IllegalStateException(
                        "No valid exercises were found in the Exercises sheet."
                    )
                )
            } else {
                Result.success(exercises)
            }
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /**
     * Downloads everything needed for one workout day.
     *
     * This directly connects:
     *
     * Day row
     *      ↓
     * Category names
     *      ↓
     * Detailed exercises
     */
    suspend fun downloadDailyWorkout(
        dayNumber: Int
    ): Result<DailyWorkout> {
        return try {
            validateConfiguration()

            /*
             * Download both ranges from Google Sheets.
             */
            val daysResponse = downloadRange(DAYS_RANGE)
            val exercisesResponse = downloadRange(EXERCISES_RANGE)

            val days = parseWorkoutDays(daysResponse)
            val allExercises = parseExercises(exercisesResponse)

            val selectedDay = days.firstOrNull { workoutDay ->
                workoutDay.dayNumber == dayNumber
            } ?: throw IllegalStateException(
                "Workout Day $dayNumber was not found."
            )

            val workoutCategories =
                selectedDay.categories.map { dayCategoryName ->

                    val requiredCategories =
                        mapDayCategoryToExerciseCategories(
                            dayCategoryName
                        )

                    val categoryExercises = allExercises
                        .filter { exercise ->
                            requiredCategories.any { requiredCategory ->
                                categoriesMatch(
                                    exercise.category,
                                    requiredCategory
                                )
                            }
                        }
                        .sortedWith(
                            compareBy<Exercise> { exercise ->
                                requiredCategories.indexOfFirst {
                                        requiredCategory ->
                                    categoriesMatch(
                                        exercise.category,
                                        requiredCategory
                                    )
                                }.let { index ->
                                    if (index == -1) {
                                        Int.MAX_VALUE
                                    } else {
                                        index
                                    }
                                }
                            }.thenBy { exercise ->
                                exercise.order
                            }
                        )

                    WorkoutCategory(
                        name = dayCategoryName.trim(),
                        exercises = categoryExercises
                    )
                }

            Result.success(
                DailyWorkout(
                    day = selectedDay,
                    categories = workoutCategories
                )
            )
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    /**
     * Sends a request to the Google Sheets API.
     */
    private suspend fun downloadRange(
        range: String
    ): String {
        return withContext(Dispatchers.IO) {
            val encodedRange = URLEncoder.encode(
                range,
                Charsets.UTF_8.name()
            )

            val requestUrl =
                "https://sheets.googleapis.com/v4/spreadsheets/" +
                        "$spreadsheetId/values/$encodedRange" +
                        "?majorDimension=ROWS" +
                        "&valueRenderOption=FORMATTED_VALUE" +
                        "&key=$apiKey"

            val connection = URL(requestUrl).openConnection()
                    as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000

                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val responseCode = connection.responseCode

                val responseText =
                    if (responseCode in 200..299) {
                        connection.inputStream
                            .bufferedReader()
                            .use { reader ->
                                reader.readText()
                            }
                    } else {
                        connection.errorStream
                            ?.bufferedReader()
                            ?.use { reader ->
                                reader.readText()
                            }
                            .orEmpty()
                    }

                if (responseCode !in 200..299) {
                    throw IllegalStateException(
                        buildString {
                            append("Google Sheets request failed.")
                            append("\nHTTP code: ")
                            append(responseCode)

                            if (responseText.isNotBlank()) {
                                append("\n")
                                append(responseText)
                            }
                        }
                    )
                }

                responseText
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Converts the Days sheet response into WorkoutDay objects.
     */
    private fun parseWorkoutDays(
        responseText: String
    ): List<WorkoutDay> {
        val rows = getRows(responseText)

        if (rows.length() == 0) {
            return emptyList()
        }

        /*
         * Read the first row as the column-heading row.
         */
        val headerRow = rows.getJSONArray(0)

        /*
         * Find the column named:
         * "Check after the workout"
         *
         * This is safer than assuming that it is always column G.
         */
        val completionColumnIndex =
            findColumnIndex(
                row = headerRow,
                expectedHeader =
                    "Check after the workout"
            )

        if (completionColumnIndex == -1) {
            throw IllegalStateException(
                "The Days sheet must contain a column named " +
                        "\"Check after the workout\"."
            )
        }

        val workoutDays =
            mutableListOf<WorkoutDay>()

        /*
         * Start at row 1 because row 0 contains headings.
         */
        for (rowIndex in 1 until rows.length()) {
            val row =
                rows.getJSONArray(rowIndex)

            val dayNumber =
                getCell(row, 0)
                    .trim()
                    .toIntOrNull()
                    ?: continue

            /*
             * Columns B-F:
             * Exercise 1 through Exercise 5.
             */
            val categories =
                (1..5)
                    .map { columnIndex ->
                        getCell(
                            row,
                            columnIndex
                        ).trim()
                    }
                    .filter { category ->
                        category.isNotBlank()
                    }

            if (categories.isEmpty()) {
                continue
            }

            val checkedValue =
                getCell(
                    row,
                    completionColumnIndex
                ).trim()

            val isCompleted =
                checkedValue.equals(
                    "true",
                    ignoreCase = true
                ) ||
                        checkedValue.equals(
                            "checked",
                            ignoreCase = true
                        ) ||
                        checkedValue.equals(
                            "yes",
                            ignoreCase = true
                        ) ||
                        checkedValue == "1"

            workoutDays.add(
                WorkoutDay(
                    dayNumber = dayNumber,
                    categories = categories,
                    isCompleted = isCompleted
                )
            )
        }

        return workoutDays.sortedBy { workoutDay ->
            workoutDay.dayNumber
        }
    }

    private fun findColumnIndex(
        row: JSONArray,
        expectedHeader: String
    ): Int {
        val normalizedExpectedHeader =
            normalize(expectedHeader)

        for (columnIndex in 0 until row.length()) {
            val currentHeader =
                normalize(
                    getCell(
                        row,
                        columnIndex
                    )
                )

            if (
                currentHeader ==
                normalizedExpectedHeader
            ) {
                return columnIndex
            }
        }

        return -1
    }
    /**
     * Converts the current Exercises sheet structure into Exercise objects.
     */
    private fun parseExercises(
        responseText: String
    ): List<Exercise> {
        val rows = getRows(responseText)

        if (rows.length() == 0) {
            return emptyList()
        }

        val exercises = mutableListOf<Exercise>()

        var currentCategory: String? = null
        var cardioSection = false

        /*
         * Tracks exercise order separately for every category.
         */
        val categoryOrder = mutableMapOf<String, Int>()

        for (rowIndex in 0 until rows.length()) {
            val row = rows.getJSONArray(rowIndex)

            val columnA = getCell(row, 0).trim()
            val columnB = getCell(row, 1).trim()
            val columnC = getCell(row, 2).trim()
            val columnD = getCell(row, 3).trim()
            val columnE = getCell(row, 4).trim()
            val columnF = getCell(row, 5).trim()

            val rowValues = listOf(
                columnA,
                columnB,
                columnC,
                columnD,
                columnE,
                columnF
            )

            if (rowValues.all { value ->
                    value.isBlank()
                }
            ) {
                continue
            }

            /*
             * Special title above the cardio exercises.
             */
            if (
                normalize(columnA).startsWith(
                    "cardio and endurance"
                )
            ) {
                cardioSection = true
                currentCategory = null
                continue
            }

            /*
             * Ignore table-header rows.
             */
            if (isExerciseHeaderRow(rowValues)) {
                continue
            }

            /*
             * A section heading has text in column A
             * and no values in the other columns.
             *
             * Examples:
             *
             * Arms
             * Chest
             * Core
             * Legs
             * Back (heavy weight)
             */
            val isCategoryHeading =
                columnA.isNotBlank() &&
                        rowValues
                            .drop(1)
                            .all { value ->
                                value.isBlank()
                            }

            if (isCategoryHeading) {
                currentCategory = cleanCategoryName(columnA)
                cardioSection = false
                continue
            }

            /*
             * Cardio rows use a different column structure:
             *
             * A = system name
             * B = exercise time
             * C = rest
             * D = sets
             * E = heart-rate zone
             * F = note
             */
            if (cardioSection && columnA.isNotBlank()) {
                val setRange = parseSetRange(columnD)

                val cardioNotes = buildList {
                    if (columnE.isNotBlank()) {
                        add("Heart-rate zone: $columnE")
                    }

                    if (columnF.isNotBlank()) {
                        add(columnF)
                    }
                }.joinToString(" | ")

                val exerciseOrder = nextOrder(
                    categoryOrder,
                    columnA
                )

                exercises.add(
                    Exercise(
                        id = rowIndex + 1,
                        category = columnA,
                        name = columnA,
                        target = normalizeRangeText(columnB),
                        totalSets = setRange.minimum,
                        maximumSets = setRange.maximum,
                        weight = null,
                        rest = columnC.ifBlank { null },
                        notes = cardioNotes.ifBlank { null },
                        order = exerciseOrder
                    )
                )

                continue
            }

            /*
             * A normal exercise must belong to a category.
             */
            val category = currentCategory ?: continue

            if (columnA.isBlank()) {
                continue
            }

            val setRange = parseSetRange(columnC)

            /*
             * Columns D-F sometimes contain weight history.
             *
             * We use the last non-empty cell containing kg or lb
             * as the suggested starting weight.
             */
            val noteCells = listOf(
                columnD,
                columnE,
                columnF
            ).filter { value ->
                value.isNotBlank()
            }

            val latestWeight = noteCells
                .asReversed()
                .firstNotNullOfOrNull { note ->
                    parseWeight(note)
                }

            val combinedNotes = noteCells
                .joinToString(" | ")
                .ifBlank { null }

            val exerciseOrder = nextOrder(
                categoryOrder,
                category
            )

            exercises.add(
                Exercise(
                    id = rowIndex + 1,
                    category = category,
                    name = columnA,
                    target = normalizeRangeText(columnB),
                    totalSets = setRange.minimum,
                    maximumSets = setRange.maximum,
                    weight = latestWeight?.first,
                    weightUnit =
                        latestWeight?.second ?: WeightUnit.KG,
                    rest = null,
                    notes = combinedNotes,
                    order = exerciseOrder
                )
            )
        }

        return exercises
    }

    /**
     * Maps names from the Days sheet to headings
     * in the Exercises sheet.
     */
    private fun mapDayCategoryToExerciseCategories(
        categoryName: String
    ): List<String> {
        val normalizedCategory = normalize(categoryName)

        return when {
            /*
             * This category combines two sections.
             */
            normalizedCategory.contains("warm up") &&
                    normalizedCategory.contains("mobility") -> {
                listOf(
                    "Warm up",
                    "Mobility"
                )
            }

            /*
             * The "20min" part describes duration,
             * while the exercise category is Aerobic system.
             */
            normalizedCategory.contains("aerobic") -> {
                listOf("Aerobic system")
            }

            /*
             * The Days sheet says Back, while the Exercises
             * sheet heading says Back (heavy weight).
             */
            normalizedCategory == "back" -> {
                listOf("Back (heavy weight)")
            }

            else -> {
                listOf(categoryName)
            }
        }
    }

    private fun categoriesMatch(
        first: String,
        second: String
    ): Boolean {
        val normalizedFirst =
            normalizeCategoryForMatching(first)

        val normalizedSecond =
            normalizeCategoryForMatching(second)

        /*
         * Exact matching prevents:
         *
         * "Lactic system"
         * from matching
         * "Alactic system"
         */
        return normalizedFirst == normalizedSecond
    }

    private fun normalizeCategoryForMatching(
        value: String
    ): String {
        return normalize(value)
            .replace("heavy weight", "")
            .replace("(", "")
            .replace(")", "")
            .trim()
    }

    /**
     * Reads a weight from text.
     *
     * Examples:
     *
     * 7.5kg
     * 35kg 16 12 12
     * 30kg + barbell
     */
    private fun parseWeight(
        text: String
    ): Pair<Double, WeightUnit>? {
        val match = Regex(
            pattern = """(\d+(?:\.\d+)?)\s*(kg|lb)""",
            option = RegexOption.IGNORE_CASE
        ).find(text) ?: return null

        val weight =
            match.groupValues[1].toDoubleOrNull()
                ?: return null

        val unit =
            when (
                match.groupValues[2].lowercase()
            ) {
                "lb" -> WeightUnit.LB
                else -> WeightUnit.KG
            }

        return weight to unit
    }

    /**
     * Repairs range values that Google Sheets may format as dates.
     *
     * Examples:
     *
     * 10-12          stays 10-12
     * 10/12/2026     becomes 10-12
     * 12/10/2026     becomes 10-12
     * Failure        stays Failure
     */
    private fun normalizeRangeText(
        rawValue: String
    ): String {
        val value = rawValue.trim()

        if (value.isBlank()) {
            return ""
        }

        val numbers = Regex("\\d+")
            .findAll(value)
            .mapNotNull { match ->
                match.value.toIntOrNull()
            }
            .toList()

        val looksLikeFormattedDate =
            value.contains("/") &&
                    numbers.size >= 3 &&
                    numbers.any { number ->
                        number >= 2000
                    }

        if (looksLikeFormattedDate) {
            val firstNumber = numbers[0]
            val secondNumber = numbers[1]

            return "${minOf(firstNumber, secondNumber)}-" +
                    maxOf(firstNumber, secondNumber)
        }

        return value
            .replace("–", "-")
            .replace("—", "-")
            .replace(
                oldValue = " to ",
                newValue = "-",
                ignoreCase = true
            )
    }

    private fun isExerciseHeaderRow(
        values: List<String>
    ): Boolean {
        val first = normalize(values.getOrElse(0) { "" })
        val second = normalize(values.getOrElse(1) { "" })
        val third = normalize(values.getOrElse(2) { "" })

        return (
                first == "exercise" ||
                        first.isBlank()
                ) &&
                (
                        second == "reps" ||
                                second == "time of exercise"
                        ) &&
                third.contains(
                    "set",
                    ignoreCase = true
                )
    }

    private fun cleanCategoryName(
        value: String
    ): String {
        return value
            .trim()
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private fun nextOrder(
        orderMap: MutableMap<String, Int>,
        category: String
    ): Int {
        val key = normalize(category)

        val nextValue =
            (orderMap[key] ?: 0) + 1

        orderMap[key] = nextValue

        return nextValue
    }

    private fun getRows(
        responseText: String
    ): JSONArray {
        val root = JSONObject(responseText)

        return root.optJSONArray("values")
            ?: JSONArray()
    }

    private fun getCell(
        row: JSONArray,
        columnIndex: Int
    ): String {
        return row.optString(
            columnIndex,
            ""
        )
    }

    private fun normalize(
        value: String
    ): String {
        return value
            .trim()
            .lowercase()
            .replace(
                Regex("\\s+"),
                " "
            )
    }

    private fun validateConfiguration() {
        if (
            spreadsheetId.isBlank() ||
            spreadsheetId == "NO_SPREADSHEET_ID"
        ) {
            throw IllegalStateException(
                "Add SPREADSHEET_ID to secrets.properties."
            )
        }

        if (
            apiKey.isBlank() ||
            apiKey == "NO_API_KEY"
        ) {
            throw IllegalStateException(
                "Add SHEETS_API_KEY to secrets.properties."
            )
        }
    }
}