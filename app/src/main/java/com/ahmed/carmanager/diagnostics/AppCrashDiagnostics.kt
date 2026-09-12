package com.ahmed.carmanager.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight local crash diagnostics. It deliberately stores only technical process data
 * in the app-private directory and never uploads anything by itself.
 */
object AppCrashDiagnostics {
    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_STACK_CHARS = 24_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun lastCrash(context: Context): CrashSnapshot? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val first = text.lineSequence().firstOrNull().orEmpty()
        val time = first.substringAfter("timestamp=", "").trim().toLongOrNull()
        return CrashSnapshot(timeMillis = time, raw = text)
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    fun summary(snapshot: CrashSnapshot): String {
        val lines = snapshot.raw.lineSequence().toList()
        val type = lines.firstOrNull { it.startsWith("exception=") }?.substringAfter('=') ?: "Unknown"
        val message = lines.firstOrNull { it.startsWith("message=") }?.substringAfter('=')?.take(180).orEmpty()
        val date = snapshot.timeMillis?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar", "EG")).format(Date(it))
        } ?: "وقت غير معروف"
        return buildString {
            append("آخر توقف مسجل: ").append(date)
            append("\n").append(type)
            if (message.isNotBlank()) append(" — ").append(message)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val stack = throwable.stackTraceToString().take(MAX_STACK_CHARS)
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(
            buildString {
                appendLine("timestamp=${System.currentTimeMillis()}")
                appendLine("thread=${thread.name}")
                appendLine("exception=${throwable.javaClass.name}")
                appendLine("message=${throwable.message.orEmpty().replace('\n', ' ')}")
                appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("--- stack ---")
                append(stack)
            }
        )
    }
}

data class CrashSnapshot(val timeMillis: Long?, val raw: String)
