package io.github.devasenan134.cauldron.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.edit
import io.github.devasenan134.cauldron.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * App updates from the Cauldron server (the GitHub repository is private, so the server hosts
 * the APKs). Android only installs a download signed with the same key as the installed app, so
 * a tampered file can't replace it.
 */
class Updates(private val context: Context, private val api: Api) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    private val _available = MutableStateFlow<AppRelease?>(null)
    /** A newer version, once a check found one. */
    val available: StateFlow<AppRelease?> = _available

    val currentVersion: String = BuildConfig.VERSION_NAME

    /** On app start: checks at most every few hours. Never throws. */
    suspend fun checkNowAndThen() {
        if (System.currentTimeMillis() - prefs.getLong(LAST_CHECK, 0) < CHECK_EVERY_MS) {
            // Still show what the last check found, until that version is installed.
            prefs.getString(FOUND, null)?.let { v -> if (isNewer(v, currentVersion)) runCatching { check() } }
            return
        }
        runCatching { check() }
    }

    /** Asks the server for its newest version. Returns it if it's newer than this app, else null. */
    suspend fun check(): AppRelease? {
        val latest = api.latestApp()
        val update = latest?.takeIf { isNewer(it.version, currentVersion) }
        prefs.edit {
            putLong(LAST_CHECK, System.currentTimeMillis())
            putString(FOUND, update?.version)
        }
        _available.value = update
        return update
    }

    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // only keep the one being downloaded
        val file = File(dir, "cauldron-${release.version}.apk")
        api.downloadApp(release.version, file, onProgress)
        return file
    }

    /** Android asks once whether Cauldron may install apps; until then, [install] can't work. */
    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() = context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /** Opens Android's installer for the downloaded file. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        private const val LAST_CHECK = "last_check"
        private const val FOUND = "found_version"
        private const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L

        /** "0.3.10" is newer than "0.3.9". */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
