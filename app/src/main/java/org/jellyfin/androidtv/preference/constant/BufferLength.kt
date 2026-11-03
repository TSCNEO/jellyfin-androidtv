package org.jellyfin.androidtv.preference.constant

import org.jellyfin.androidtv.R
import org.jellyfin.preference.PreferenceEnum
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
 * [prioritizeTimeOverSize] is the `DefaultLoadControl.setPrioritizeTimeOverSizeThresholds` flag. When it is
 * `true` the duration thresholds win and [targetBufferBytes] only works as an advisory hint: the loader keeps
 * going until [maxBufferDuration] even after the byte budget is exhausted, so the allocator grows past the
 * declared ceiling (a 60 second window at 80-100 Mbps is 600-750 MB of samples). When it is `false` the byte
 * budget is authoritative: loading stops as soon as the allocator is full and resumes as playback releases
 * samples, so the profile behaves as a fixed RAM window that refills continuously.
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
	 * High profile: fills a real 256 MiB sample budget and keeps requesting as playback releases samples. The
	 * 25-60 second window is only a ceiling for low bitrate streams: on high bitrate remuxes the byte budget is
	 * reached first (256 MiB is roughly 25 seconds at 80 Mbps) and the loader refills up to that budget instead
	 * of stopping at [minBufferDuration].
	 */
	HIGH(
		nameRes = R.string.playback_buffer_high,
		descriptionRes = R.string.playback_buffer_high_desc,
		minBufferDuration = 25.seconds,
		maxBufferDuration = 60.seconds,
		bufferForPlaybackDuration = 1500.milliseconds,
		bufferForPlaybackAfterRebufferDuration = 3.seconds,
		targetBufferBytes = HIGH_BUFFER_BYTES,
		prioritizeTimeOverSize = false,
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
		val LEGACY_NAMES: Set<String> = setOf("LARGE", "EXTRA_LARGE", "AGGRESSIVE")
	}
}
