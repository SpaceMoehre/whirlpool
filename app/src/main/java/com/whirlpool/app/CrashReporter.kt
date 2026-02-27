package com.whirlpool.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

data class CrashSummary(
    val fileName: String,
    val timestamp: String,
    val exception: String,
    val screen: String,
)

object CrashReporter {
    private const val REPORT_DIR = "crash-reports"
    private const val MAX_REPORTS = 40
    private val installed = AtomicBoolean(false)
    private val handlingCrash = AtomicBoolean(false)

    @Volatile
    private var currentScreen: String = "unknown"

    fun install(application: Application) {
        if (!installed.compareAndSet(false, true)) return

        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) {
                    currentScreen = activity::class.java.simpleName
                }
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (handlingCrash.compareAndSet(false, true)) {
                runCatching {
                    persistUncaughtException(application, thread, throwable)
                }
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun recentSummaries(context: Context, limit: Int = 5): List<CrashSummary> {
        val files = reportDirectory(context)
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("log", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        return files
            .take(limit.coerceAtLeast(0))
            .mapNotNull { file -> parseSummary(file) }
    }

    private fun persistUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
        val now = Date()
        val reportTime = formatTimestamp(now)
        val reportDir = reportDirectory(context).apply { mkdirs() }
        val safeTime = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(now)
        val file = File(reportDir, "crash_$safeTime.log")

        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName.orEmpty().ifBlank { "unknown" }
        val versionCode = packageInfo?.longVersionCode?.toString().orEmpty().ifBlank { "unknown" }

        val stackTrace = StringWriter().also { writer ->
            PrintWriter(writer).use { printWriter ->
                throwable.printStackTrace(printWriter)
                var cause = throwable.cause
                while (cause != null) {
                    printWriter.println()
                    printWriter.println("Caused by:")
                    cause.printStackTrace(printWriter)
                    cause = cause.cause
                }
            }
        }.toString()

        file.bufferedWriter().use { out ->
            out.appendLine("timestamp=$reportTime")
            out.appendLine("exception=${throwable::class.java.name}: ${throwable.message.orEmpty()}")
            out.appendLine("thread=${thread.name} (id=${thread.id})")
            out.appendLine("screen=$currentScreen")
            out.appendLine("processId=${Process.myPid()}")
            out.appendLine("app=${context.packageName}")
            out.appendLine("appVersion=$versionName ($versionCode)")
            out.appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            out.appendLine("sdkInt=${Build.VERSION.SDK_INT}")
            out.appendLine("release=${Build.VERSION.RELEASE}")
            out.appendLine("fingerprint=${Build.FINGERPRINT}")
            out.appendLine("abi=${Build.SUPPORTED_ABIS.joinToString()}")
            out.appendLine("javaVm=${System.getProperty("java.vm.name").orEmpty()}")
            out.appendLine("runtime=${System.getProperty("java.runtime.version").orEmpty()}")
            out.appendLine("---- stacktrace ----")
            out.appendLine(stackTrace)
        }

        trimOldReports(reportDir)
    }

    private fun parseSummary(file: File): CrashSummary? {
        val lines = runCatching { file.readLines() }.getOrNull() ?: return null
        val timestamp = lines.firstOrNull { it.startsWith("timestamp=") }
            ?.removePrefix("timestamp=")
            ?.trim()
            .orEmpty()
            .ifBlank { formatTimestamp(Date(file.lastModified())) }
        val exception = lines.firstOrNull { it.startsWith("exception=") }
            ?.removePrefix("exception=")
            ?.trim()
            .orEmpty()
            .ifBlank { "unknown exception" }
        val screen = lines.firstOrNull { it.startsWith("screen=") }
            ?.removePrefix("screen=")
            ?.trim()
            .orEmpty()
            .ifBlank { "unknown" }
        return CrashSummary(
            fileName = file.name,
            timestamp = timestamp,
            exception = exception,
            screen = screen,
        )
    }

    private fun trimOldReports(reportDir: File) {
        val reports = reportDir
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("log", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        if (reports.size <= MAX_REPORTS) return
        reports.drop(MAX_REPORTS).forEach { stale ->
            runCatching { stale.delete() }
        }
    }

    private fun reportDirectory(context: Context): File = File(context.filesDir, REPORT_DIR)

    private fun formatTimestamp(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(date)
    }
}

