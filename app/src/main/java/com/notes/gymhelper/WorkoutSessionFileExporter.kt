package com.notes.gymhelper

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WorkoutSessionFileExporter {

    private const val FOLDER_NAME = "Gym Helper"

    /**
     * Exports one completed workout session as a CSV file.
     *
     * Example:
     * 8#2026-07-26.csv
     */
    fun exportSession(
        context: Context,
        database: WorkoutLogDatabaseHelper,
        sessionId: Long
    ): Result<Uri> {
        return runCatching {
            val session =
                database.getWorkoutSession(sessionId)
                    ?: throw IllegalStateException(
                        "Workout session $sessionId was not found."
                    )

            val records =
                database.getSetRecordsForSession(sessionId)

            if (records.isEmpty()) {
                throw IllegalStateException(
                    "This session does not contain any saved sets."
                )
            }

            val currentDate =
                formatDate(
                    session.finishedAt
                        ?: System.currentTimeMillis()
                )

            val fileName =
                "${session.dayNumber}#$currentDate.csv"

            val csvText =
                createCsvText(
                    session = session,
                    records = records
                )

            /*
             * Android 10 and newer use MediaStore.
             *
             * Android 9 and older write directly into
             * the public Downloads directory.
             */
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                exportUsingMediaStore(
                    context = context,
                    fileName = fileName,
                    csvText = csvText
                )
            } else {
                exportUsingLegacyStorage(
                    context = context,
                    fileName = fileName,
                    csvText = csvText
                )
            }
        }
    }

    /**
     * Android 10 and newer.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportUsingMediaStore(
        context: Context,
        fileName: String,
        csvText: String
    ): Uri {
        val relativePath =
            "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME"

        deleteExistingMediaStoreFile(
            context = context,
            fileName = fileName,
            relativePath = relativePath
        )

        val values =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "text/csv"
                )

                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    relativePath
                )

                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    1
                )
            }

        val resolver =
            context.contentResolver

        val fileUri =
            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw IllegalStateException(
                "Android could not create the workout file."
            )

        try {
            resolver.openOutputStream(fileUri)
                ?.use { outputStream ->

                    outputStream
                        .writer(Charsets.UTF_8)
                        .use { writer ->
                            writer.write(csvText)
                        }
                }
                ?: throw IllegalStateException(
                    "Android could not open the workout file."
                )

            val publishValues =
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.IS_PENDING,
                        0
                    )
                }

            resolver.update(
                fileUri,
                publishValues,
                null,
                null
            )

            return fileUri
        } catch (exception: Exception) {
            resolver.delete(
                fileUri,
                null,
                null
            )

            throw exception
        }
    }

    /**
     * Android 9 and older.
     */
    @Suppress("DEPRECATION")
    private fun exportUsingLegacyStorage(
        context: Context,
        fileName: String,
        csvText: String
    ): Uri {
        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            throw SecurityException(
                "Storage permission was not granted."
            )
        }

        if (
            Environment.getExternalStorageState() !=
            Environment.MEDIA_MOUNTED
        ) {
            throw IllegalStateException(
                "Shared storage is not currently available."
            )
        }

        /*
         * Public Download folder visible in My Files.
         */
        val downloadsDirectory =
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )

        val gymHelperDirectory =
            File(
                downloadsDirectory,
                FOLDER_NAME
            )

        if (
            !gymHelperDirectory.exists() &&
            !gymHelperDirectory.mkdirs()
        ) {
            throw IllegalStateException(
                "Could not create Downloads/Gym Helper."
            )
        }

        val outputFile =
            File(
                gymHelperDirectory,
                fileName
            )

        /*
         * Replace the old file when the same day is
         * completed more than once on the same date.
         */
        if (
            outputFile.exists() &&
            !outputFile.delete()
        ) {
            throw IllegalStateException(
                "Could not replace the existing workout file."
            )
        }

        outputFile.writeText(
            text = csvText,
            charset = Charsets.UTF_8
        )

        /*
         * Notify Android and file-manager apps that
         * the new file now exists.
         */
        MediaScannerConnection.scanFile(
            context,
            arrayOf(outputFile.absolutePath),
            arrayOf("text/csv"),
            null
        )

        return Uri.fromFile(outputFile)
    }

    private fun createCsvText(
        session: WorkoutSession,
        records: List<WorkoutSetRecord>
    ): String {
        val sessionDate =
            formatDate(
                session.finishedAt
                    ?: session.startedAt
            )

        return buildString {
            appendLine(
                "Day,Date,Session ID,Category,Exercise," +
                        "Set,Actual Reps,Weight,Unit,Target,Completed At"
            )

            records.forEach { record ->
                val row =
                    listOf(
                        session.dayNumber.toString(),
                        sessionDate,
                        session.id.toString(),
                        record.categoryName,
                        record.exerciseName,
                        record.setNumber.toString(),
                        record.completedReps
                            ?.toString()
                            .orEmpty(),
                        record.weight
                            ?.let(::formatNumber)
                            .orEmpty(),
                        if (record.weight == null) {
                            ""
                        } else {
                            record.weightUnit.name
                        },
                        record.target,
                        formatDateTime(
                            record.completedAt
                        )
                    )

                appendLine(
                    row.joinToString(",") { value ->
                        escapeCsv(value)
                    }
                )
            }
        }
    }

    private fun escapeCsv(
        value: String
    ): String {
        val needsQuotes =
            value.contains(",") ||
                    value.contains("\"") ||
                    value.contains("\n") ||
                    value.contains("\r")

        if (!needsQuotes) {
            return value
        }

        val escapedValue =
            value.replace(
                oldValue = "\"",
                newValue = "\"\""
            )

        return "\"$escapedValue\""
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteExistingMediaStoreFile(
        context: Context,
        fileName: String,
        relativePath: String
    ) {
        val resolver =
            context.contentResolver

        val projection =
            arrayOf(
                MediaStore.MediaColumns._ID
            )

        val selection =
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"

        val selectionArguments =
            arrayOf(
                fileName,
                "$relativePath/"
            )

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArguments,
            null
        )?.use { cursor ->
            val idIndex =
                cursor.getColumnIndexOrThrow(
                    MediaStore.MediaColumns._ID
                )

            while (cursor.moveToNext()) {
                val existingId =
                    cursor.getLong(idIndex)

                val existingUri =
                    Uri.withAppendedPath(
                        MediaStore.Downloads
                            .EXTERNAL_CONTENT_URI,
                        existingId.toString()
                    )

                resolver.delete(
                    existingUri,
                    null,
                    null
                )
            }
        }
    }

    private fun formatDate(
        timestamp: Long
    ): String {
        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(
            Date(timestamp)
        )
    }

    private fun formatDateTime(
        timestamp: Long
    ): String {
        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.US
        ).format(
            Date(timestamp)
        )
    }

    private fun formatNumber(
        value: Double
    ): String {
        return if (
            value % 1.0 == 0.0
        ) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }
}