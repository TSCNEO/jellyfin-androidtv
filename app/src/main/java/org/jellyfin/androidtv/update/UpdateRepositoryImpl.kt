package org.jellyfin.androidtv.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.androidtv.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

class UpdateRepositoryImpl(
	private val context: Context,
	base: OkHttpClient,
	private val json: Json,
) : UpdateRepository {

	private val client = base.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

	private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
	override val state: StateFlow<UpdateState> = _state.asStateFlow()

	private val _promptDismissed = MutableStateFlow(false)
	override val promptDismissed: StateFlow<Boolean> = _promptDismissed.asStateFlow()

	private var job: Job? = null
	@Volatile private var lastApk: File? = null

	init {
		UpdateInstallReceiver.onFailure = { status, message ->
			val update = (_state.value as? UpdateState.Installing)?.update
			_state.value = UpdateState.Failed(message, update)
		}
	}

	override fun check(manual: Boolean) {
		if (job?.isActive == true) return
		val now = System.currentTimeMillis()
		if (!manual && now - prefs.getLong(KEY_LAST_CHECK, 0) < AUTO_CHECK_INTERVAL_MS) return
		job = scope.launch {
			if (manual) _state.value = UpdateState.Checking
			runCatching { fetchLatest() }
				.onSuccess { latest ->
					prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
					val currentVersion = BuildConfig.VERSION_NAME
					_state.value = if (latest != null && isNewer(latest.version, currentVersion)) {
						if (manual) _promptDismissed.value = false
						UpdateState.Available(currentVersion, latest)
					} else {
						UpdateState.UpToDate(currentVersion)
					}
				}
				.onFailure {
					Log.w(TAG, "Failed to check update: ${it.message}")
					_state.value = if (manual) {
						UpdateState.Failed("Couldn't check for a new version.", null)
					} else {
						UpdateState.Idle
					}
				}
		}
	}

	override fun downloadAndInstall() {
		val update = when (val s = _state.value) {
			is UpdateState.Available -> s.update
			is UpdateState.NeedsPermission -> s.update
			is UpdateState.Failed -> s.update ?: return
			else -> return
		}
		if (job?.isActive == true) return
		if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
			_state.value = UpdateState.NeedsPermission(update)
			return
		}
		job = scope.launch {
			runCatching {
				val apk = download(update)
				_state.value = UpdateState.Installing(update)
				install(apk)
			}.onFailure {
				Log.w(TAG, "Update download/install failed: ${it.message}")
				_state.value = UpdateState.Failed("Couldn't download the update. Try again.", update)
			}
		}
	}

	override fun dismissPrompt() {
		_promptDismissed.value = true
	}

	private fun fetchLatest(): AppUpdate? {
		val request = Request.Builder()
			.url("https://api.github.com/repos/TSCNEO/jellyfin-androidtv/releases/latest")
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "JellyfinDirectPlay/${BuildConfig.VERSION_NAME}")
			.build()
		val body = client.newCall(request).execute().use { response ->
			if (response.code == 404) return null
			check(response.isSuccessful) { "GitHub HTTP ${response.code}" }
			response.body?.string().orEmpty()
		}
		val release = json.parseToJsonElement(body) as? JsonObject ?: return null
		if (release["draft"]?.jsonPrimitive?.contentOrNull == "true") return null
		val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
		val assets = (release["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
		val apk = assets.firstOrNull { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
			?: return null
		return AppUpdate(
			version = tag.removePrefix("v"),
			notes = release["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
			apkUrl = apk.string("browser_download_url") ?: return null,
			sizeBytes = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
		)
	}

	private fun download(update: AppUpdate): File {
		val dir = File(context.cacheDir, "updates").apply { mkdirs() }
		dir.listFiles()?.forEach { it.delete() }
		val file = File(dir, "JellyfinDirectPlay-${update.version}.apk")
		_state.value = UpdateState.Downloading(update, 0f)
		val request = Request.Builder().url(update.apkUrl).header("User-Agent", "JellyfinDirectPlay/${BuildConfig.VERSION_NAME}").build()
		client.newCall(request).execute().use { response ->
			check(response.isSuccessful) { "Download HTTP ${response.code}" }
			val body = checkNotNull(response.body)
			val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes
			body.byteStream().use { input ->
				file.outputStream().use { output ->
					val buffer = ByteArray(64 * 1024)
					var read = 0L
					var lastShown = 0f
					while (true) {
						val n = input.read(buffer)
						if (n < 0) break
						output.write(buffer, 0, n)
						read += n
						if (total > 0) {
							val progress = (read.toFloat() / total).coerceIn(0f, 1f)
							if (progress - lastShown >= 0.01f) {
								lastShown = progress
								_state.value = UpdateState.Downloading(update, progress)
							}
						}
					}
				}
			}
		}
		return file
	}

	private fun install(apk: File) {
		lastApk = apk
		val installer = context.packageManager.packageInstaller
		val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
			.apply { setAppPackageName(context.packageName) }
		val sessionId = installer.createSession(params)
		installer.openSession(sessionId).use { session ->
			apk.inputStream().use { input ->
				session.openWrite("app.apk", 0, apk.length()).use { output ->
					input.copyTo(output)
					session.fsync(output)
				}
			}
			val intent = Intent(context, UpdateInstallReceiver::class.java)
			val flags = PendingIntent.FLAG_UPDATE_CURRENT or
				(if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
			val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
			session.commit(pending.intentSender)
		}
	}

	private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

	companion object {
		private const val TAG = "Update"
		private const val KEY_LAST_CHECK = "last_check"
		private const val AUTO_CHECK_INTERVAL_MS = 6 * 3_600_000L

		fun isNewer(candidate: String, current: String): Boolean {
			fun parse(v: String): List<Int> {
				val clean = v.trim().removePrefix("v")
				val main = clean.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
				val pre = clean.substringAfter('-', "")
				val beta = if (pre.contains("beta.")) {
					pre.substringAfter("beta.").substringBefore('.').substringBefore('-').toIntOrNull() ?: 0
				} else if (pre.isNotEmpty() && !pre.startsWith("fork.") && !pre.startsWith("directplay.")) {
					0
				} else {
					999
				}
				val fork = if (pre.contains("directplay.")) {
					pre.substringAfter("directplay.").substringBefore('-').substringBefore('.').toIntOrNull() ?: 0
				} else if (pre.contains("fork.")) {
					pre.substringAfter("fork.").substringBefore('-').substringBefore('.').toIntOrNull() ?: 0
				} else {
					0
				}
				return main + listOf(beta, fork)
			}
			val a = parse(candidate)
			val b = parse(current)
			for (i in 0 until maxOf(a.size, b.size)) {
				val x = a.getOrElse(i) { 0 }
				val y = b.getOrElse(i) { 0 }
				if (x != y) return x > y
			}
			return false
		}
	}
}
