package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** 288 MiB, the byte budget reserved by the [BufferLength.AGGRESSIVE] profile.
 * Sized safely below Android TV's 384 MB JVM heap ceiling (dalvik.vm.heapsize) leaving ~96 MB for UI & bitmaps. */
const val AGGRESSIVE_BUFFER_BYTES: Int = 288 * 1024 * 1024

/** 256 MiB, the byte budget reserved by the [BufferLength.HIGH] profile. */
const val HIGH_BUFFER_BYTES: Int = 256 * 1024 * 1024

/** 64 MiB, the byte budget reserved by the [BufferLength.LOW] profile. */
const val LOW_BUFFER_BYTES: Int = 64 * 1024 * 1024

/**
 * Network buffer (RAM) profiles used by both the legacy [org.jellyfin.androidtv.ui.playback.VideoManager]
 * player and the Media3 [org.jellyfin.playback.media3.exoplayer.ExoPlayerBackend].
 *
 * A profile controls two independent dimensions:
 * - **Duration thresholds** ([minBufferDuration] / [maxBufferDuration]): how many seconds of media
 *   ExoPlayer keeps ahead of the playhead. This is what protects against network jitter.
 * - **Byte budget** ([targetBufferBytes]): the hard RAM ceiling for the sample allocator. On Android TV
 *   devices with 1-2 GB of RAM this is the value that decides whether a high bitrate remux can be kept
 *   fully in memory.
 *
 * [prioritizeTimeOverSize] is the `DefaultLoadControl.setPrioritizeTimeOverSizeThresholds` flag. It MUST be
 * `true` for every profile that declares a [targetBufferBytes] budget, otherwise ExoPlayer stops loading as
 * soon as the byte budget is exhausted even when [minBufferDuration] has not been reached yet, which
 * manifests as permanent re-buffering on high bitrate streams.
 */
enum class BufferLength(
	override val nameRes: Int,
	val descriptionRes: Int,
	val minBufferDuration: Duration? = null,
	val maxBufferDuration: Duration? = null,
	val bufferForPlaybackDuration: Duration? = null,
	val bufferForPlaybackAfterRebufferDuration: Duration? = null,
	val targetBufferBytes: Int? = null,
	val prioritizeTimeOverSize: Boolean = false,
) : PreferenceEnum {
	/**
	 * Adaptive profile: ExoPlayer sizes the allocator on its own for the current track set (never forcing an
	 * explicit byte reservation) while the duration thresholds keep a 25-60 second window ahead of playback.
	 * Time is prioritized over size so a slow but steady connection is allowed to keep filling the window.
	 */
	AUTO(
		nameRes = R.string.playback_buffer_auto,
		descriptionRes = R.string.playback_buffer_auto_desc,
		minBufferDuration = 25.seconds,
		maxBufferDuration = 60.seconds,
		bufferForPlaybackDuration = 1500.milliseconds,
		bufferForPlaybackAfterRebufferDuration = 3.seconds,
		prioritizeTimeOverSize = true,
	),

	/**
	 * Aggressive profile for boxes with 3 GB of RAM or more (Homatics, Shield). Reserves a 288 MiB
	 * sample budget (tuned to stay comfortably inside Android TV's 384 MB heap ceiling without OOM kills)
	 * and grows the window to 60-120 seconds: enough headroom to absorb bitrate spikes of 4K remuxes
	 * without a single re-buffer. The settings screen hides this entry on devices with less than 3 GB of RAM.
	 */
	AGGRESSIVE(
		nameRes = R.string.playback_buffer_aggressive,
		descriptionRes = R.string.playback_buffer_aggressive_desc,
		minBufferDuration = 60.seconds,
		maxBufferDuration = 120.seconds,
		bufferForPlaybackDuration = 2.seconds,
		bufferForPlaybackAfterRebufferDuration = 4.seconds,
		targetBufferBytes = AGGRESSIVE_BUFFER_BYTES,
		prioritizeTimeOverSize = true,
	),

	/**
	 * High profile: reserves a full 256 MiB sample budget and keeps the same 25-60 second window. This is the
	 * profile that removes hiccups on Wi-Fi links and on streams with bitrate spikes.
	 */
	HIGH(
		nameRes = R.string.playback_buffer_high,
		descriptionRes = R.string.playback_buffer_high_desc,
		minBufferDuration = 25.seconds,
		maxBufferDuration = 60.seconds,
		bufferForPlaybackDuration = 1500.milliseconds,
		bufferForPlaybackAfterRebufferDuration = 3.seconds,
		targetBufferBytes = HIGH_BUFFER_BYTES,
		prioritizeTimeOverSize = true,
	),

	/**
	 * Stock ExoPlayer behavior: [androidx.media3.exoplayer.DefaultLoadControl] defaults (50s/50s) and the
	 * default allocator sizing (~131 MB of video plus audio/metadata).
	 */
	STANDARD(
		nameRes = R.string.playback_buffer_standard,
		descriptionRes = R.string.playback_buffer_standard_desc,
	),

	/**
	 * Low profile for devices with very little free RAM: shorter window and a 64 MiB byte budget.
	 */
	LOW(
		nameRes = R.string.playback_buffer_low,
		descriptionRes = R.string.playback_buffer_low_desc,
		minBufferDuration = 15.seconds,
		maxBufferDuration = 30.seconds,
		targetBufferBytes = LOW_BUFFER_BYTES,
	),

	;

	companion object {
		/**
		 * Profiles removed in the Direct Play fork. Kept only so previously stored values can be migrated
		 * instead of silently falling back to the default profile.
		 */
		val LEGACY_NAMES: Set<String> = setOf("LARGE", "EXTRA_LARGE")
	}
}
