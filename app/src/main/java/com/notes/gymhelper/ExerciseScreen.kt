package com.notes.gymhelper

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    dayNumber: Int,
    sessionId: Long,
    onBackClick: () -> Unit,
    onWorkoutFinished: () -> Unit
) {
    val context = LocalContext.current

    /*
     * This database stores only local workout history:
     *
     * - workout sessions
     * - completed sets
     * - actual repetitions
     * - weights
     */
    val logDatabase = remember {
        WorkoutLogDatabaseHelper(
            context.applicationContext
        )
    }

    /*
     * Workout data is downloaded directly from Google Sheets.
     */
    var dailyWorkout by remember {
        mutableStateOf<DailyWorkout?>(null)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }
    var showExitDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var exitDeleteError by rememberSaveable {
        mutableStateOf("")
    }
    /*
     * Increase this value to retry the Google Sheets download.
     */
    var reloadKey by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(
        dayNumber,
        reloadKey
    ) {
        isLoading = true
        errorMessage = ""
        dailyWorkout = null

        val result =
            GoogleSheetsDataSource.downloadDailyWorkout(
                dayNumber = dayNumber
            )

        result.onSuccess { downloadedWorkout ->
            dailyWorkout = downloadedWorkout
            isLoading = false
        }

        result.onFailure { error ->
            errorMessage =
                error.message
                    ?: "Could not download the workout."

            isLoading = false
        }
    }

    BackHandler(
        enabled = !showExitDialog
    ) {
        exitDeleteError = ""
        showExitDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Day $dayNumber")
                },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            exitDeleteError = ""
                            showExitDialog = true
                        }
                    ) {
                        Text("Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                ExerciseLoadingContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            errorMessage.isNotEmpty() -> {
                ExerciseDownloadErrorContent(
                    errorMessage = errorMessage,
                    onRetry = {
                        reloadKey++
                    },
                    onBackClick = {
                        exitDeleteError = ""
                        showExitDialog = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            dailyWorkout == null -> {
                ExerciseDownloadErrorContent(
                    errorMessage = "Workout data was not found.",
                    onRetry = {
                        reloadKey++
                    },
                    onBackClick = {
    exitDeleteError = ""
    showExitDialog = true
},
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            else -> {
                val workoutSteps = dailyWorkout!!
                    .categories
                    .flatMap { category ->
                        category.exercises.map { exercise ->
                            category.name to exercise
                        }
                    }

                if (workoutSteps.isEmpty()) {
                    NoDetailedExercisesContent(
                        dayNumber = dayNumber,
                        onRetry = {
                            reloadKey++
                        },
                        onBackClick = {
    exitDeleteError = ""
    showExitDialog = true
},
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                } else {
                    ActiveWorkoutContent(
                        dayNumber = dayNumber,
                        sessionId = sessionId,
                        workoutSteps = workoutSteps,
                        logDatabase = logDatabase,
                        onWorkoutFinished = onWorkoutFinished,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                exitDeleteError = ""
                showExitDialog = false
            },

            title = {
                Text("Leave Workout?")
            },

            text = {
                Column {
                    Text(
                        text =
                            "Going back will delete this workout session " +
                                    "and every set you submitted during it."
                    )

                    if (exitDeleteError.isNotEmpty()) {
                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = exitDeleteError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        val deletedSuccessfully =
                            logDatabase.deleteWorkoutSession(
                                sessionId = sessionId
                            )

                        if (deletedSuccessfully) {
                            exitDeleteError = ""
                            showExitDialog = false
                            onBackClick()
                        } else {
                            exitDeleteError =
                                "Could not delete the workout session."
                        }
                    }
                ) {
                    Text("Delete and Exit")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        exitDeleteError = ""
                        showExitDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActiveWorkoutContent(
    dayNumber: Int,
    sessionId: Long,
    workoutSteps: List<Pair<String, Exercise>>,
    logDatabase: WorkoutLogDatabaseHelper,
    onWorkoutFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    /*
     * Position of the current exercise in the full workout.
     */
    var currentExerciseIndex by rememberSaveable(
        dayNumber,
        sessionId
    ) {
        mutableIntStateOf(0)
    }

    /*
     * Current set starts from 1.
     */
    var currentSet by rememberSaveable(
        dayNumber,
        sessionId
    ) {
        mutableIntStateOf(1)
    }

    val safeExerciseIndex =
        currentExerciseIndex.coerceIn(
            minimumValue = 0,
            maximumValue = workoutSteps.lastIndex
        )

    val currentStep =
        workoutSteps[safeExerciseIndex]

    val currentCategoryName =
        currentStep.first

    val currentExercise =
        currentStep.second

    /*
     * Only exercises whose actual Exercises-sheet category is
     * "Warm up" are skipped from input and workout history.
     *
     * Mobility is not considered warm-up and will still be saved.
     */
    val isWarmUp =
        isWarmUpExercise(currentExercise)

    val minimumSets =
        currentExercise.totalSets.coerceAtLeast(1)

    val maximumSets =
        currentExercise.maximumSets.coerceAtLeast(
            minimumSets
        )

    /*
     * Get the most recent weight used for this exercise.
     *
     * If no history exists, use the weight from Google Sheets.
     */
    val lastUsedWeight = remember(
        currentExerciseIndex
    ) {
        logDatabase.getLastUsedWeight(
            currentExercise.name
        )
    }

    val suggestedWeight =
        lastUsedWeight?.first
            ?: currentExercise.weight

    val suggestedWeightUnit =
        lastUsedWeight?.second
            ?: currentExercise.weightUnit

    /*
     * For a range such as 6-8, the default planned total is 6.
     *
     * The user can change it to 7 or 8.
     */
    var selectedTotalSetsText by rememberSaveable(
        dayNumber,
        sessionId,
        currentExerciseIndex
    ) {
        mutableStateOf(
            minimumSets.toString()
        )
    }

    /*
     * Weight remains during all sets of the current exercise.
     *
     * The user can change it before submitting each set.
     */
    var weightText by rememberSaveable(
        dayNumber,
        sessionId,
        currentExerciseIndex
    ) {
        mutableStateOf(
            suggestedWeight?.let { weight ->
                formatWeight(weight)
            } ?: ""
        )
    }

    var weightUnit by rememberSaveable(
        dayNumber,
        sessionId,
        currentExerciseIndex
    ) {
        mutableStateOf(suggestedWeightUnit)
    }

    /*
     * Repetitions are cleared automatically whenever the set changes.
     */
    var actualRepsText by rememberSaveable(
        dayNumber,
        sessionId,
        currentExerciseIndex,
        currentSet
    ) {
        mutableStateOf("")
    }

    var totalSetsError by rememberSaveable(
        dayNumber,
        sessionId,
        currentExerciseIndex
    ) {
        mutableStateOf("")
    }

    var repsError by rememberSaveable(
        dayNumber,
        sessionId,
        currentExerciseIndex,
        currentSet
    ) {
        mutableStateOf("")
    }

    var weightError by rememberSaveable(
        dayNumber,
        sessionId,
        currentExerciseIndex
    ) {
        mutableStateOf("")
    }

    var saveError by rememberSaveable(
        dayNumber,
        sessionId,
        currentExerciseIndex,
        currentSet
    ) {
        mutableStateOf("")
    }

    val focusManager =
        LocalFocusManager.current

    /*
     * Timed exercises do not require an actual-repetitions value.
     *
     * Examples:
     * - 30 seconds
     * - 20 minutes
     * - Cardio intervals
     *
     * "Until failure" can still use actual reps.
     */
    val requiresRepetitions =
        !isWarmUp &&
                exerciseUsesRepetitions(
                    currentExercise
                )

    /*
     * A weight is required when Google Sheets or local history
     * already provides a suggested weight.
     *
     * Bodyweight/cardio exercises can leave weight empty.
     */
    val requiresWeight =
        !isWarmUp &&
                suggestedWeight != null

    val enteredTotalSets =
        selectedTotalSetsText
            .toIntOrNull()

    val displayedTotalSets =
        when {
            enteredTotalSets == null -> {
                minimumSets
            }

            enteredTotalSets < minimumSets -> {
                minimumSets
            }

            enteredTotalSets > maximumSets -> {
                maximumSets
            }

            else -> {
                enteredTotalSets
            }
        }

    val isLastSet =
        currentSet >= displayedTotalSets

    val isLastExercise =
        currentExerciseIndex >=
                workoutSteps.lastIndex

    val mainButtonText =
        when {
            !isLastSet -> {
                "Next Set"
            }

            !isLastExercise -> {
                "Next Exercise"
            }

            else -> {
                "Finish Workout"
            }
        }

    /*
     * Validate the number selected from a set range.
     */
    fun validateTotalSets(): Boolean {
        val selectedSets =
            selectedTotalSetsText
                .toIntOrNull()

        if (selectedSets == null) {
            totalSetsError =
                "Enter the total number of sets."

            return false
        }

        if (
            selectedSets !in
            minimumSets..maximumSets
        ) {
            totalSetsError =
                if (minimumSets == maximumSets) {
                    "This exercise requires $minimumSets sets."
                } else {
                    "Choose between $minimumSets and $maximumSets sets."
                }

            return false
        }

        if (selectedSets < currentSet) {
            totalSetsError =
                "Total sets cannot be below the current set."

            return false
        }

        totalSetsError = ""
        return true
    }

    /*
     * Validate actual repetitions.
     */
    fun validateRepetitions(): Boolean {
        if (!requiresRepetitions) {
            repsError = ""
            return true
        }

        val repetitions =
            actualRepsText
                .toIntOrNull()

        if (
            repetitions == null ||
            repetitions <= 0
        ) {
            repsError =
                "Enter the repetitions completed."

            return false
        }

        repsError = ""
        return true
    }

    /*
     * Validate weight.
     *
     * Blank weight is allowed only for exercises that do not
     * already have a suggested/default weight.
     */
    fun validateWeight(): Boolean {
        if (weightText.isBlank()) {
            if (requiresWeight) {
                weightError =
                    "Enter the weight used."

                return false
            }

            weightError = ""
            return true
        }

        val enteredWeight =
            weightText.toDoubleOrNull()

        if (
            enteredWeight == null ||
            enteredWeight <= 0.0
        ) {
            weightError =
                "Enter a valid weight."

            return false
        }

        weightError = ""
        return true
    }

    /*
     * Save the current set locally.
     *
     * This function is called only from the main button.
     */
    fun saveCurrentSet(): Boolean {
        saveError = ""

        /*
         * Set-count validation is still required so that the
         * screen knows when to move to the next exercise.
         */
        if (!validateTotalSets()) {
            return false
        }

        /*
         * Warm-up exercises are intentionally not saved.
         *
         * Returning true allows the app to continue to the next
         * set or exercise without inserting a database record.
         */
        if (isWarmUp) {
            return true
        }

        if (!validateRepetitions()) {
            return false
        }

        if (!validateWeight()) {
            return false
        }

        val completedReps =
            if (requiresRepetitions) {
                actualRepsText.toIntOrNull()
            } else {
                null
            }

        val enteredWeight =
            if (weightText.isBlank()) {
                null
            } else {
                weightText.toDoubleOrNull()
            }

        val recordId =
            logDatabase.saveSetRecord(
                WorkoutSetRecord(
                    sessionId = sessionId,
                    dayNumber = dayNumber,

                    /*
                     * Use the exercise's real category from the
                     * Exercises sheet.
                     *
                     * This is especially important when the Days
                     * sheet contains "Warm up + Mobility".
                     */
                    categoryName =
                        currentExercise.category,

                    exerciseName =
                        currentExercise.name,

                    setNumber = currentSet,

                    completedReps =
                        completedReps,

                    weight = enteredWeight,

                    weightUnit = weightUnit,

                    target =
                        currentExercise.target
                )
            )

        return if (recordId != -1L) {
            saveError = ""
            true
        } else {
            saveError =
                "Could not save this set."

            false
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp)
    ) {
        Text(
            text =
                "Exercise ${currentExerciseIndex + 1} " +
                        "of ${workoutSteps.size}",
            style =
                MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = currentCategoryName,
            style =
                MaterialTheme.typography.headlineSmall,
            color =
                MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = currentExercise.name,
                    style =
                        MaterialTheme.typography
                            .headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Text(
                    text =
                        "Set $currentSet " +
                                "of $displayedTotalSets",
                    style =
                        MaterialTheme.typography
                            .titleLarge
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Text(
                    text =
                        if (
                            minimumSets ==
                            maximumSets
                        ) {
                            "Required sets: $minimumSets"
                        } else {
                            "Allowed sets: " +
                                    "$minimumSets-$maximumSets"
                        },
                    style =
                        MaterialTheme.typography
                            .bodyLarge
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Text(
                    text = "Target",
                    style =
                        MaterialTheme.typography
                            .titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = currentExercise.target,
                    style =
                        MaterialTheme.typography
                            .bodyLarge
                )

                if (
                    !currentExercise
                        .rest
                        .isNullOrBlank()
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Text(
                        text = "Rest",
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            currentExercise.rest,
                        style =
                            MaterialTheme.typography
                                .bodyLarge
                    )
                }

                if (
                    !currentExercise
                        .notes
                        .isNullOrBlank()
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    Text(
                        text = "Notes",
                        style =
                            MaterialTheme.typography
                                .titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text =
                            currentExercise.notes,
                        style =
                            MaterialTheme.typography
                                .bodyLarge
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        /*
         * Show total-set selection only when the spreadsheet
         * contains a range such as 6-8.
         */
        if (minimumSets != maximumSets) {
            OutlinedTextField(
                value = selectedTotalSetsText,
                onValueChange = { newValue ->
                    if (
                        newValue.isEmpty() ||
                        newValue.all { character ->
                            character.isDigit()
                        }
                    ) {
                        selectedTotalSetsText =
                            newValue

                        totalSetsError = ""
                    }
                },
                label = {
                    Text("Planned total sets")
                },
                supportingText = {
                    Text(
                        "Choose from " +
                                "$minimumSets to " +
                                "$maximumSets"
                    )
                },
                isError =
                    totalSetsError.isNotEmpty(),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType =
                            KeyboardType.Number,
                        imeAction =
                            ImeAction.Next
                    ),
                modifier =
                    Modifier.fillMaxWidth()
            )

            if (totalSetsError.isNotEmpty()) {
                Text(
                    text = totalSetsError,
                    color =
                        MaterialTheme
                            .colorScheme
                            .error,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }

        /*
         * Warm-up exercises do not require input and are not saved.
         *
         * Mobility is not warm-up, so mobility still uses the normal
         * repetitions/weight behavior and is saved to workout history.
         */
        if (isWarmUp) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Warm-up Exercise",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "No repetitions or weight need to be entered. " +
                                "Warm-up exercises are not saved.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        } else {
            /*
             * Actual repetitions are shown only for repetition-based
             * exercises. Timed mobility/cardio exercises do not need reps,
             * but they are still saved when the main button is pressed.
             */
            if (requiresRepetitions) {
                OutlinedTextField(
                    value = actualRepsText,
                    onValueChange = { newValue ->
                        if (
                            newValue.isEmpty() ||
                            newValue.all { character ->
                                character.isDigit()
                            }
                        ) {
                            actualRepsText = newValue
                            repsError = ""
                        }
                    },
                    label = {
                        Text("Actual repetitions")
                    },
                    supportingText = {
                        Text(
                            "Target: ${currentExercise.target}"
                        )
                    },
                    isError = repsError.isNotEmpty(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (repsError.isNotEmpty()) {
                    Text(
                        text = repsError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )
            } else {
                Text(
                    text = "This exercise does not require actual repetitions.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )
            }

            /*
             * Weight remains available for every non-warm-up exercise.
             * It is optional when the sheet/history has no suggested weight.
             */
            Text(
                text = "Weight used for this set",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { newValue ->
                        if (
                            newValue.isEmpty() ||
                            newValue.matches(
                                Regex("^\\d*\\.?\\d*$")
                            )
                        ) {
                            weightText = newValue
                            weightError = ""
                        }
                    },
                    label = {
                        Text("Weight")
                    },
                    supportingText = {
                        if (!requiresWeight) {
                            Text(
                                "Optional when no weight is used"
                            )
                        }
                    },
                    isError = weightError.isNotEmpty(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                OutlinedButton(
                    onClick = {
                        val currentValue =
                            weightText.toDoubleOrNull()

                        if (weightUnit == WeightUnit.KG) {
                            if (currentValue != null) {
                                weightText = formatWeight(
                                    kilogramsToPounds(currentValue)
                                )
                            }

                            weightUnit = WeightUnit.LB
                        } else {
                            if (currentValue != null) {
                                weightText = formatWeight(
                                    poundsToKilograms(currentValue)
                                )
                            }

                            weightUnit = WeightUnit.KG
                        }

                        weightError = ""
                    }
                ) {
                    Text(
                        when (weightUnit) {
                            WeightUnit.KG -> "kg"
                            WeightUnit.LB -> "lb"
                        }
                    )
                }
            }

            if (weightError.isNotEmpty()) {
                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = weightError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (saveError.isNotEmpty()) {
            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = saveError,
                color =
                    MaterialTheme.colorScheme.error,
                style =
                    MaterialTheme.typography
                        .bodyMedium
            )
        }

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = {
                focusManager.clearFocus()

                /*
                 * No navigation or progression happens unless
                 * the current set was successfully saved.
                 */
                if (!saveCurrentSet()) {
                    return@Button
                }

                val confirmedTotalSets =
                    selectedTotalSetsText
                        .toIntOrNull()
                        ?: minimumSets

                when {
                    /*
                     * Continue to the next set of the same exercise.
                     */
                    currentSet <
                            confirmedTotalSets -> {
                        currentSet++
                    }

                    /*
                     * All sets are complete.
                     * Move to the next exercise.
                     */
                    currentExerciseIndex <
                            workoutSteps.lastIndex -> {
                        currentExerciseIndex++
                        currentSet = 1
                    }

                    /*
                     * Final set of the final exercise.
                     *
                     * GymApp will mark the session completed.
                     */
                    else -> {
                        onWorkoutFinished()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(mainButtonText)
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text =
                if (isWarmUp) {
                            "Press $mainButtonText to continue."
                } else {
                    "This set is saved only when " +
                            "you press $mainButtonText."
                },
            style =
                MaterialTheme.typography
                    .bodyMedium
        )
    }
}

@Composable
private fun ExerciseLoadingContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement =
            Arrangement.Center
    ) {
        CircularProgressIndicator()

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text =
                "Downloading exercises " +
                        "from Google Sheets...",
            style =
                MaterialTheme.typography
                    .bodyLarge
        )
    }
}

@Composable
private fun ExerciseDownloadErrorContent(
    errorMessage: String,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement =
            Arrangement.Center
    ) {
        Text(
            text =
                "Could not load the exercises.",
            style =
                MaterialTheme.typography
                    .headlineSmall,
            fontWeight = FontWeight.Bold,
            color =
                MaterialTheme.colorScheme.error
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = errorMessage,
            style =
                MaterialTheme.typography
                    .bodyMedium
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Again")
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun NoDetailedExercisesContent(
    dayNumber: Int,
    onRetry: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement =
            Arrangement.Center
    ) {
        Text(
            text =
                "No detailed exercises were found " +
                        "for Day $dayNumber.",
            style =
                MaterialTheme.typography
                    .headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text =
                "Check that the category names in " +
                        "Days match the sections in Exercises.",
            style =
                MaterialTheme.typography
                    .bodyLarge
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reload")
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

/**
 * Returns true only when the exercise itself belongs to the Warm up
 * category in the Exercises sheet.
 *
 * Mobility remains false here, even when the Days sheet groups the
 * categories under "Warm up + Mobility".
 */
private fun isWarmUpExercise(
    exercise: Exercise
): Boolean {
    val normalizedCategory =
        exercise.category
            .trim()
            .lowercase()
            .replace(
                Regex("[\\s_-]+"),
                ""
            )

    return normalizedCategory == "warmup"
}

/**
 * Returns true when the exercise should have an actual-reps field.
 */
private fun exerciseUsesRepetitions(
    exercise: Exercise
): Boolean {
    val target =
        exercise.target
            .trim()
            .lowercase()

    val timedWords = listOf(
        "second",
        "seconds",
        "sec",
        "minute",
        "minutes",
        "min",
        "hour",
        "time",
        "heart-rate",
        "heart rate"
    )

    return timedWords.none { word ->
        target.contains(word)
    }
}

private fun kilogramsToPounds(
    kilograms: Double
): Double {
    return kilograms * 2.20462
}

private fun poundsToKilograms(
    pounds: Double
): Double {
    return pounds / 2.20462
}

private fun formatWeight(
    weight: Double
): String {
    val roundedWeight =
        kotlin.math.round(
            weight * 10
        ) / 10

    return if (
        roundedWeight % 1.0 == 0.0
    ) {
        roundedWeight
            .toInt()
            .toString()
    } else {
        roundedWeight.toString()
    }
}