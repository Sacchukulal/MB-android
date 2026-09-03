package com.magicbill.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.magicbill.app.BuildConfig
import com.magicbill.app.core.Clock
import com.magicbill.app.di.AppScope
import com.magicbill.app.prefs.Plain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the shelf says about the newest build. Every GitHub release carries this file as
 * `version.json`; `releases/latest/download/version.json` always points at the newest one.
 * `version_code` is what decides "newer" — the name is for people, the code is for phones.
 */
@Serializable
data class Release(
    val version: String,
    val version_code: Int? = null,
    val apk_url: String,
    val release_notes: String? = null,
    /** The APK's size in bytes, so a half-downloaded file is never handed to the installer. */
    val apk_size: Long? = null,
    /** "2026-09-03" — the day it was published, for the sheet. */
    val published: String? = null,
) {
    val name: String get() = version.removePrefix("v")
}

/**
 * The phone updates itself from GitHub Releases — no store in between. One check on every
 * start (only once the phone is signed in or paired, never over the login flow) and one on
 * demand from More → App update. A newer build is offered in a sheet; "Not now" keeps it
 * quiet for a day and leaves a dot on the More tab. "Update now" downloads the APK to this
 * app's own files, then hands it to Android's installer — the one confirm Android requires.
 *
 * "Newer" is decided by `version_code`, which only ever goes up, so a release may be named
 * anything. A shelf that carries no code is compared by name, the way the July phones did.
 */
@Singleton
class Updater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plain: Plain,
    private val clock: Clock,
    @AppScope private val scope: CoroutineScope,
) {
    sealed class State {
        /** Nothing known yet, or nothing asked. */
        data object Idle : State()
        data object Checking : State()
        data class UpToDate(val version: String) : State()
        data class Available(val release: Release) : State()
        data class Downloading(val release: Release, val progress: Float) : State()
        /** On disk and whole. [needsPermission]: Android wants "Install unknown apps" switched on first. */
        data class Ready(val release: Release, val file: File, val needsPermission: Boolean) : State()
        data class Failed(val release: Release?, val says: String) : State()

        val releaseOrNull: Release? get() = when (this) {
            is Available -> release; is Downloading -> release; is Ready -> release; is Failed -> release; else -> null
        }
    }

    private val stateFlow = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> get() = stateFlow

    /** The sheet is up. Set by a check the person asked for, or by a newer build on start. */
    private val openFlow = MutableStateFlow(false)
    val open: StateFlow<Boolean> get() = openFlow

    /** A newer build is known and the sheet is down — the dot on More. */
    val waiting: Boolean get() = state.value.let { it is State.Available || it is State.Ready }

    private var checking: Job? = null
    private var downloading: Job? = null

    /** On start: look, and open the sheet only for a build not turned down today. */
    fun checkQuietly() {
        if (checking?.isActive == true || downloading?.isActive == true) return
        checking = scope.launch {
            val found = lookUp()
            stateFlow.value = found
            if (found is State.Available && !dismissedRecently(found.release)) openFlow.value = true
        }
    }

    /** From More → App update: the sheet opens at once and says what it finds. */
    fun checkNow() {
        openFlow.value = true
        val s = state.value
        // A download in flight, or a file ready: show that, do not look again.
        if (s is State.Downloading || s is State.Ready) return
        if (checking?.isActive == true) return
        checking = scope.launch {
            stateFlow.value = State.Checking
            stateFlow.value = lookUp()
        }
    }

    /** "Not now": keep this build quiet for a day; the dot on More remembers it. */
    fun dismiss() {
        openFlow.value = false
        val r = state.value.releaseOrNull ?: return
        plain.put(Plain.UPDATE_DISMISSED_VERSION, r.version)
        plain.putLong(Plain.UPDATE_DISMISSED_AT, clock.now())
    }

    /** Close the sheet without a memory — after "up to date", or a failure. */
    fun close() { openFlow.value = false }

    /** "Update now": bring the APK down, then hand it to the installer. */
    fun download() {
        val r = (state.value as? State.Available)?.release ?: (state.value as? State.Failed)?.release ?: return
        if (downloading?.isActive == true) return
        downloading = scope.launch {
            stateFlow.value = State.Downloading(r, 0f)
            try {
                val file = fetchApk(r) { stateFlow.value = State.Downloading(r, it) }
                stateFlow.value = State.Ready(r, file, needsPermission = false)
                install()
            } catch (e: Exception) {
                stateFlow.value = State.Failed(r, "The download did not finish. Check the internet and try again.")
            }
        }
    }

    /** Hand the whole file to Android. If the switch is off, say so and wait for [resumed]. */
    fun install() {
        val s = state.value as? State.Ready ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            stateFlow.value = s.copy(needsPermission = true)
            return
        }
        val uri = FileProvider.getUriForFile(context, AUTHORITY, s.file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
    }

    /** The one-time "Install unknown apps" switch for this app, in Settings. */
    fun openInstallSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Back from Settings with the switch now on: carry on to the installer by itself. */
    fun resumed() {
        val s = state.value as? State.Ready ?: return
        if (s.needsPermission && context.packageManager.canRequestPackageInstalls()) {
            stateFlow.value = s.copy(needsPermission = false)
            install()
        }
    }

    private suspend fun lookUp(): State = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(SHELF_URL).header("Accept", "application/json").header("Cache-Control", "no-cache").build()
            client.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@withContext State.Failed(null, "GitHub answered ${res.code}. Try again in a while.")
                val release = json.decodeFromString(Release.serializer(), res.body.string())
                if (release.version.isBlank() || release.apk_url.isBlank()) return@withContext State.Failed(null, "The release on GitHub is missing its file.")
                if (!isNewer(release, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) {
                    tidy(keep = null)
                    return@withContext State.UpToDate(BuildConfig.VERSION_NAME)
                }
                tidy(keep = release)
                val have = apkFile(release)
                if (have.exists() && release.apk_size != null && have.length() == release.apk_size) State.Ready(release, have, needsPermission = false)
                else State.Available(release)
            }
        } catch (e: IOException) {
            State.Failed(null, "Could not reach GitHub. Check the internet and try again.")
        } catch (e: Exception) {
            State.Failed(null, "GitHub's answer could not be read.")
        }
    }

    private suspend fun fetchApk(r: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val file = apkFile(r)
        val part = File(file.path + ".part")
        part.delete()
        val request = Request.Builder().url(r.apk_url).header("Accept", APK_MIME).build()
        downloadClient.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IOException("apk ${res.code}")
            val body = res.body
            val total = r.apk_size ?: body.contentLength()
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastShown = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done.toFloat() / total).coerceIn(0f, 1f)
                            if (p - lastShown >= 0.01f) { lastShown = p; progress(p) }
                        }
                    }
                }
            }
        }
        if (r.apk_size != null && part.length() != r.apk_size) throw IOException("short file ${part.length()} of ${r.apk_size}")
        file.delete()
        if (!part.renameTo(file)) throw IOException("rename")
        progress(1f)
        file
    }

    private fun dismissedRecently(r: Release): Boolean =
        plain.get(Plain.UPDATE_DISMISSED_VERSION) == r.version && clock.now() - plain.getLong(Plain.UPDATE_DISMISSED_AT) < DISMISS_FOR_MS

    private fun updatesDir(): File = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
    private fun apkFile(r: Release): File = File(updatesDir(), "magic-bill-${r.name}.apk")

    /** Old downloads go; the one for [keep] stays. */
    private fun tidy(keep: Release?) {
        val keepName = keep?.let { apkFile(it).name }
        updatesDir().listFiles()?.forEach { if (it.name != keepName) it.delete() }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(8)).readTimeout(Duration.ofSeconds(15)).callTimeout(Duration.ofSeconds(25))
        .build()
    /** The APK is tens of megabytes: a stall deadline per read, and a long one for the whole. */
    private val downloadClient: OkHttpClient = client.newBuilder()
        .readTimeout(Duration.ofSeconds(30)).callTimeout(Duration.ofMinutes(15))
        .build()

    companion object {
        const val SHELF_URL = "https://github.com/Sacchukulal/MB-android/releases/latest/download/version.json"
        const val RELEASES_PAGE = "https://github.com/Sacchukulal/MB-android/releases/latest"
        private const val AUTHORITY = "com.magicbill.app.fileprovider"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val DISMISS_FOR_MS = 24L * 60 * 60 * 1000

        /** By code when the shelf carries one (it only ever goes up); by name otherwise. */
        fun isNewer(release: Release, installedCode: Int, installedName: String): Boolean {
            val code = release.version_code
            if (code != null) return code > installedCode
            return compareNames(release.name, installedName.removePrefix("v")) > 0
        }

        /** "3.1.1" vs "3.2" vs "2.0.0-rc1": numbers left to right, a missing part is 0. */
        fun compareNames(a: String, b: String): Int {
            fun parts(s: String) = s.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }
            val pa = parts(a); val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}
