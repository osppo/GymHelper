package com.notes.gymhelper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHomeScreen(
    onStartWorkout: (Int) -> Unit
) {
    /*
     * Data downloaded directly from the Google Sheets Days tab.
     */
    var workoutDays by remember {
        mutableStateOf<List<WorkoutDay>>(emptyList())
    }
    val context = LocalContext.current
    /*
     * Position of the day currently displayed.
     *
     * Index 0 means the first row/day in the downloaded list.
     */
    var selectedDayIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var errorMessage by remember {
        mutableStateOf("")
    }

    /*
     * Increasing this number triggers another download.
     */
    var reloadKey by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(reloadKey) {
        isLoading = true
        errorMessage = ""

        val result =
            GoogleSheetsDataSource.downloadWorkoutDays()

        result.onSuccess { downloadedDays ->

            val sortedDays =
                downloadedDays.sortedBy { workoutDay ->
                    workoutDay.dayNumber
                }

            workoutDays = sortedDays

            /*
             * Start from the first day whose Google Sheet checkbox
             * is FALSE.
             *
             * This keeps the workout order correct even if a later
             * checkbox was accidentally selected.
             */
            val firstUncheckedIndex =
                sortedDays.indexOfFirst { workoutDay ->
                    !workoutDay.isCompleted
                }

            selectedDayIndex = when {
                firstUncheckedIndex >= 0 -> {
                    firstUncheckedIndex
                }

                sortedDays.isNotEmpty() -> {
                    /*
                     * Every day is checked.
                     * The current screen will remain on the final day.
                     */
                    sortedDays.lastIndex
                }

                else -> {
                    0
                }
            }

            isLoading = false
        }

        result.onFailure { error ->
            workoutDays = emptyList()

            errorMessage =
                error.message ?: "Could not download workout days."

            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Gym Helper")
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                LoadingWorkoutContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            errorMessage.isNotEmpty() -> {
                WorkoutDownloadErrorContent(
                    errorMessage = errorMessage,
                    onRetry = {
                        reloadKey++
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            workoutDays.isEmpty() -> {
                NoWorkoutDaysContent(
                    onRetry = {
                        reloadKey++
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            else -> {
                /*
                 * Prevent an invalid index if the downloaded
                 * list later changes.
                 */
                val safeSelectedIndex =
                    selectedDayIndex.coerceIn(
                        minimumValue = 0,
                        maximumValue = workoutDays.lastIndex
                    )

                val selectedWorkoutDay =
                    workoutDays[safeSelectedIndex]

                WorkoutDayContent(
                    workoutDay = selectedWorkoutDay,

                    /*
                     * The button is disabled when the user reaches
                     * the first available workout day.
                     */
                    canGoToPreviousDay =
                        safeSelectedIndex > 0,

                    onPreviousDay = {
                        if (selectedDayIndex > 0) {
                            selectedDayIndex--
                        }
                    },

                    onStartWorkout = {
                        onStartWorkout(
                            selectedWorkoutDay.dayNumber
                        )
                    },

                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun WorkoutDayContent(
    workoutDay: WorkoutDay,
    canGoToPreviousDay: Boolean,
    onPreviousDay: () -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        /*
         * Previous-day button and day counter are displayed
         * on the same top row.
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Text(
                text = "Day ${workoutDay.dayNumber}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = onPreviousDay,
                enabled = canGoToPreviousDay
            ) {
                Text("← Back")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (workoutDay.isCompleted) {
            Text(
                text = "Completed workout",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = "Today's Training",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Exercises scheduled for this day:",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        /*
         * These values come from columns B-F:
         *
         * Exercise 1
         * Exercise 2
         * Exercise 3
         * Exercise 4
         * Exercise 5
         */
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                vertical = 4.dp
            ),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = workoutDay.categories,
                key = { index, category ->
                    "${workoutDay.dayNumber}-$index-$category"
                }
            ) { index, category ->
                WorkoutCategoryCard(
                    number = index + 1,
                    categoryName = category
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartWorkout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (workoutDay.isCompleted) {
                    "Repeat Workout"
                } else {
                    "Start Workout"
                }
            )
        }
    }
}

@Composable
private fun WorkoutCategoryCard(
    number: Int,
    categoryName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$number. $categoryName",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun LoadingWorkoutContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Downloading workout days...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun WorkoutDownloadErrorContent(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Could not load workout data.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Again")
        }
    }
}

@Composable
private fun NoWorkoutDaysContent(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No workout days were found.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Check the Days sheet and try again.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reload")
        }
    }
}
