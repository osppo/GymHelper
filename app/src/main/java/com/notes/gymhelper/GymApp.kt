package com.notes.gymhelper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
object GymRoutes {

    const val HOME = "home"

    /*
     * The exercise route now carries:
     *
     * dayNumber = workout day selected by the user
     * sessionId = local database session created for this workout
     */
    const val EXERCISE =
        "exercise/{dayNumber}/{sessionId}"

    fun exerciseRoute(
        dayNumber: Int,
        sessionId: Long
    ): String {
        return "exercise/$dayNumber/$sessionId"
    }
}

@Composable
fun GymApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val storagePermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.RequestPermission()
        ) { permissionGranted ->

            if (permissionGranted) {
                Toast.makeText(
                    context,
                    "Storage permission granted.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Storage permission is required to save workout files.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    LaunchedEffect(Unit) {
        /*
         * Android 9 and older require runtime permission
         * before writing into the public Downloads folder.
         */
        if (
            Build.VERSION.SDK_INT <=
            Build.VERSION_CODES.P
        ) {
            val permissionStatus =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

            if (
                permissionStatus !=
                PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        }
    }

    /*
     * Local database used only for workout history:
     *
     * - workout sessions
     * - submitted sets
     * - reps
     * - weights
     */
    val logDatabase = remember {
        WorkoutLogDatabaseHelper(
            context.applicationContext
        )
    }

    NavHost(
        navController = navController,
        startDestination = GymRoutes.HOME
    ) {
        composable(
            route = GymRoutes.HOME
        ) {
            WorkoutHomeScreen(
                onStartWorkout = { dayNumber ->

                    /*
                     * Create a new local workout session only when
                     * the user presses Start Workout.
                     */
                    val sessionId =
                        logDatabase.startWorkoutSession(
                            dayNumber = dayNumber
                        )

                    /*
                     * SQLite returns -1 if insertion failed.
                     *
                     * Navigate only when the session was created.
                     */
                    if (sessionId != -1L) {
                        navController.navigate(
                            GymRoutes.exerciseRoute(
                                dayNumber = dayNumber,
                                sessionId = sessionId
                            )
                        )
                    }
                }
            )
        }

        composable(
            route = GymRoutes.EXERCISE,
            arguments = listOf(
                navArgument("dayNumber") {
                    type = NavType.IntType
                },

                navArgument("sessionId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->

            val dayNumber =
                backStackEntry.arguments
                    ?.getInt("dayNumber")
                    ?: return@composable

            val sessionId =
                backStackEntry.arguments
                    ?.getLong("sessionId")
                    ?: return@composable

            ExerciseScreen(
                dayNumber = dayNumber,
                sessionId = sessionId,

                onBackClick = {
                    /*
                     * The session remains incomplete if the user
                     * leaves before finishing the workout.
                     */
                    navController.popBackStack()
                },

                onWorkoutFinished = {
                    val finishedSuccessfully =
                        logDatabase.finishWorkoutSession(
                            sessionId = sessionId
                        )

                    if (finishedSuccessfully) {

                        val exportResult =
                            WorkoutSessionFileExporter.exportSession(
                                context = context.applicationContext,
                                database = logDatabase,
                                sessionId = sessionId
                            )

                        exportResult.onSuccess {
                            android.widget.Toast.makeText(
                                context,
                                "Workout saved to Downloads/Gym Helper",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }

                        exportResult.onFailure { error ->
                            android.widget.Toast.makeText(
                                context,
                                "File export failed: ${error.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()

                            android.util.Log.e(
                                "WORKOUT_EXPORT",
                                "File creation failed",
                                error
                            )
                        }

                        navController.navigate(GymRoutes.HOME) {
                            popUpTo(GymRoutes.HOME) {
                                inclusive = true
                            }

                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}