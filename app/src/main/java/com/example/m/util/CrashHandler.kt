package com.example.m.util

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Create a log file in the public Documents directory.
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val logDir = File(documentsDir, "MyMusicApp/logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val logFile = File(logDir, "crash_log_$timestamp.txt")

        try {
            // Write the crash report to the file.
            val writer = FileWriter(logFile, true)
            val printWriter = PrintWriter(writer)

            printWriter.append("************ CAUSE OF ERROR ************\n\n")
            val stackTrace = StringWriter()
            throwable.printStackTrace(PrintWriter(stackTrace))
            printWriter.append(stackTrace.toString())

            printWriter.append("\n************ DEVICE INFORMATION ***********\n")
            printWriter.append("Brand: ").append(Build.BRAND).append("\n")
            printWriter.append("Device: ").append(Build.DEVICE).append("\n")
            printWriter.append("Model: ").append(Build.MODEL).append("\n")
            printWriter.append("Id: ").append(Build.ID).append("\n")
            printWriter.append("Product: ").append(Build.PRODUCT).append("\n")

            printWriter.append("\n************ FIRMWARE ************\n")
            printWriter.append("SDK: ").append(Build.VERSION.SDK_INT.toString()).append("\n")
            printWriter.append("Release: ").append(Build.VERSION.RELEASE).append("\n")
            printWriter.append("Incremental: ").append(Build.VERSION.INCREMENTAL).append("\n")

            printWriter.flush()
            writer.flush()
            writer.close()
            printWriter.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Let the default handler do its thing before exiting.
        defaultHandler?.uncaughtException(thread, throwable)

        // Terminate the process.
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(10)
    }
}
