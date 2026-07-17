package com.magicbill.app.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.magicbill.app.BuildConfig
import com.magicbill.app.core.UpdateInfo
import com.magicbill.app.data.prefs.SecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateUiState(
    val available: UpdateInfo? = null,
    val checking: Boolean = false,
    /** Sheet suppressed (dismissed <24h ago) — show only the Account dot. */
    val sheetSuppressed: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    /** "Install unknown apps" not yet granted — show the settings prompt. */
    val needsInstallPermission: Boolean = false,
    /** Downloaded APK ready to hand to the installer. */
    val readyFile: String? = null,
)

/**
 * Direct-APK auto-update. Every GitHub release ships `version.json`
 * ({version, apk_url, release_notes}); `releases/latest/download/version.json`
 * always points at the newest. Checks on app open + manually from Account.
 * Updates are never forced; a dismissal quiets the sheet for 24h and leaves
 * a dot on the Account tab.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val json: Json,
    private val prefs: SecurePrefs,
) {
    private companion object {
        const val VERSION_JSON_URL =
            "https://github.com/Sacchukulal/MB-android/releases/latest/download/version.json"
        const val AUTHORITY = "com.magicbill.app.fileprovider"
        const val DISMISS_WINDOW_MS = 24L * 60 * 60 * 1000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** @return "update" | "up-to-date" | "error" (for the manual check UI). */
    suspend fun check(): String = withContext(Dispatchers.IO) {
        _state.update { it.copy(checking = true) }
        try {
            val res = client.newCall(
                Request.Builder().url(VERSION_JSON_URL)
                    .header("Accept", "application/json").build(),
            ).execute()
            res.use {
                if (!it.isSuccessful) return@withContext "error"
                val info = json.decodeFromString(
                    UpdateInfo.serializer(), it.body?.string() ?: return@withContext "error",
                )
                if (info.version.isBlank() || info.apk_url.isBlank()) return@withContext "error"

                if (isNewer(info.version, BuildConfig.VERSION_NAME)) {
                    val dismissedVersion = prefs.getString(SecurePrefs.UPDATE_DISMISSED_VERSION)
                    val dismissedAt = prefs.getLong(SecurePrefs.UPDATE_DISMISSED_AT)
                    val suppressed = dismissedVersion == info.version &&
                        System.currentTimeMillis() - dismissedAt < DISMISS_WINDOW_MS
                    _state.update {
                        it.copy(available = info, sheetSuppressed = suppressed)
                    }
                    "update"
                } else {
                    _state.update { it.copy(available = null, sheetSuppressed = false) }
                    "up-to-date"
                }
            }
        } catch (e: Exception) {
            "error"
        } finally {
            _state.update { it.copy(checking = false) }
        }
    }

    fun checkOnLaunch() {
        scope.launch { check() }
    }

    fun dismiss() {
        val version = _state.value.available?.version ?: return
        prefs.putString(SecurePrefs.UPDATE_DISMISSED_VERSION, version)
        prefs.putLong(SecurePrefs.UPDATE_DISMISSED_AT, System.currentTimeMillis())
        _state.update { it.copy(sheetSuppressed = true) }
    }

    /** Re-open the sheet from the Account row even while suppressed. */
    fun reopenSheet() {
        _state.update { it.copy(sheetSuppressed = false) }
    }

    fun downloadAndInstall() {
        val info = _state.value.available ?: return
        if (_state.value.downloading) return
        scope.launch {
            _state.update { it.copy(downloading = true, progress = 0f) }
            try {
                val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
                val dest = File(dir, "magic-bill-${info.version}.apk")
                if (dest.exists()) dest.delete()

                val dm = context.getSystemService(DownloadManager::class.java)
                val request = DownloadManager.Request(Uri.parse(info.apk_url))
                    .setTitle("Magic Bill ${info.version}")
                    .setDescription("Downloading update…")
                    .setDestinationUri(Uri.fromFile(dest))
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE,
                    )
                    .setMimeType("application/vnd.android.package-archive")
                val id = dm.enqueue(request)

                var done = false
                while (!done) {
                    delay(400)
                    val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                    if (cursor == null || !cursor.moveToFirst()) {
                        cursor?.close()
                        throw IllegalStateException("download-cancelled")
                    }
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS),
                    )
                    val downloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    )
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    )
                    cursor.close()

                    if (total > 0) {
                        _state.update { it.copy(progress = downloaded.toFloat() / total) }
                    }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> done = true
                        DownloadManager.STATUS_FAILED ->
                            throw IllegalStateException("download-failed")
                    }
                }

                _state.update {
                    it.copy(downloading = false, progress = 1f, readyFile = dest.absolutePath)
                }
                launchInstaller(dest)
            } catch (e: Exception) {
                _state.update { it.copy(downloading = false, progress = 0f) }
            }
        }
    }

    /** Hands the APK to the system installer (the one confirm Android requires). */
    fun launchInstaller(explicitFile: File? = null) {
        val file = explicitFile ?: _state.value.readyFile?.let(::File) ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(needsInstallPermission = true) }
            return
        }
        val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    /** Opens the "Install unknown apps" settings page for this app. */
    fun openInstallPermissionSettings() {
        _state.update { it.copy(needsInstallPermission = false) }
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun isNewer(a: String, b: String): Boolean {
        val pa = a.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
