package org.jellyfin.androidtv.ui.playback

import android.content.Context
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.playback.exoplayer.directplay.DolbyVisionDisplayHelper
import org.jellyfin.playback.exoplayer.directplay.Dv7FallbackMode
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType

/**
 * A single technical badge shown in the player OSD (e.g. "4K", "Dolby Vision", "DD+ Atmos", "5.1").
 *
 * @param text label rendered inside the pill
 * @param isHighlight draw with the accent/highlight styling (used for object-audio formats)
 */
data class BadgeInfo(
	val text: String,
	val isHighlight: Boolean = false,
)

/**
 * Builds the technical badge list for the player OSD from the Jellyfin item/media source metadata.
 *
 * The output is intentionally format-agnostic so the same helper can feed both the classic
 * ExoPlayer overlay (XML) and the Compose player header.
 */
object PlaybackBadgesHelper {

	private val DOLBY_VISION_TYPES = setOf(
		VideoRangeType.DOVI,
		VideoRangeType.DOVI_WITH_HDR10,
		VideoRangeType.DOVI_WITH_HDR10_PLUS,
		VideoRangeType.DOVI_WITH_EL,
		VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
		VideoRangeType.DOVI_INVALID,
		// Also Dolby Vision, just carrying a different base layer.
		VideoRangeType.DOVI_WITH_HLG,
		VideoRangeType.DOVI_WITH_SDR,
	)

	private val HDR_BASE_LAYER_TYPES = setOf(
		VideoRangeType.DOVI_WITH_HDR10,
		VideoRangeType.DOVI_WITH_HDR10_PLUS,
		VideoRangeType.DOVI_WITH_EL,
		VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
		VideoRangeType.DOVI_WITH_HLG,
	)

	fun resolveFallbackActive(context: Context?, mode: Dv7FallbackMode? = null): Boolean {
		if (context == null) return false
		val resolvedMode = mode ?: runCatching {
			org.koin.java.KoinJavaComponent.get<UserPreferences>(UserPreferences::class.java)[UserPreferences.dv7FallbackMode]
		}.getOrDefault(Dv7FallbackMode.AUTO)
		return DolbyVisionDisplayHelper.isFallbackActive(context, resolvedMode)
	}

	/**
	 * @param item the currently playing item (used to resolve media streams when no media source is given)
	 * @param mediaSource the active media source, if known
	 * @param activeAudioIndex the currently selected audio stream index, or null to use the default stream
	 */
	@JvmOverloads
	fun getBadges(
		item: BaseItemDto?,
		mediaSource: MediaSourceInfo?,
		activeAudioIndex: Int? = null,
		context: Context? = null,
		isDvFallbackActive: Boolean = resolveFallbackActive(context),
	): List<BadgeInfo> {
		val streams: List<MediaStream> = mediaSource?.mediaStreams
			?: item?.mediaStreams
			?: emptyList()

		return getBadges(
			streams = streams,
			defaultAudioIndex = mediaSource?.defaultAudioStreamIndex,
			activeAudioIndex = activeAudioIndex,
			context = context,
			isDvFallbackActive = isDvFallbackActive,
		)
	}

	fun getBadges(
		item: BaseItemDto?,
		mediaSource: MediaSourceInfo?,
		isDvFallbackActive: Boolean,
	): List<BadgeInfo> = getBadges(item, mediaSource, null, null, isDvFallbackActive)

	@JvmOverloads
	fun getBadges(
		streams: List<MediaStream>,
		defaultAudioIndex: Int? = null,
		activeAudioIndex: Int? = null,
		context: Context? = null,
		isDvFallbackActive: Boolean = resolveFallbackActive(context),
	): List<BadgeInfo> {
		if (streams.isEmpty()) return emptyList()

		val badges = mutableListOf<BadgeInfo>()

		val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
		videoStream?.let { stream ->
			resolutionBadge(stream)?.let(badges::add)
			badges += hdrBadges(stream, context, isDvFallbackActive)
		}

		val audioStream = resolveAudioStream(streams, defaultAudioIndex, activeAudioIndex)
		audioStream?.let { stream ->
			audioBadge(stream)?.let(badges::add)
			channelBadge(stream)?.let(badges::add)
		}

		return badges
	}

	fun getBadges(
		streams: List<MediaStream>,
		isDvFallbackActive: Boolean,
	): List<BadgeInfo> = getBadges(streams, null, null, null, isDvFallbackActive)

	private fun resolveAudioStream(
		streams: List<MediaStream>,
		defaultAudioIndex: Int?,
		activeAudioIndex: Int?,
	): MediaStream? {
		val audioStreams = streams.filter { it.type == MediaStreamType.AUDIO }
		if (audioStreams.isEmpty()) return null

		val requestedIndex = activeAudioIndex ?: defaultAudioIndex
		if (requestedIndex != null) {
			audioStreams.firstOrNull { it.index == requestedIndex }?.let { return it }
		}

		return audioStreams.firstOrNull { it.isDefault } ?: audioStreams.first()
	}

	private fun resolutionBadge(stream: MediaStream): BadgeInfo? {
		val width = stream.width ?: 0
		val height = stream.height ?: 0
		if (width <= 0 && height <= 0) return null

		val text = when {
			width >= 3800 || height >= 2100 -> "4K"
			width >= 1900 || height >= 1000 -> "1080p"
			width >= 1200 || height >= 700 -> "720p"
			else -> null
		}
		return text?.let(::BadgeInfo)
	}

	/**
	 * Returns the single HDR badge for a video stream. Only one HDR format is reported per stream even
	 * when the metadata carries several of them (Dolby Vision profile 7/8 also signal the HDR10 or
	 * HDR10+ base layer), because the player only ever outputs one active HDR mode at a time. Priority:
	 * Dolby Vision > HDR10+ > HLG > HDR10.
	 */
	private fun hdrBadges(
		stream: MediaStream,
		context: Context?,
		isDvFallbackActive: Boolean,
	): List<BadgeInfo> {
		val badge = hdrBadge(stream.videoRangeType, stream.videoRange, context, isDvFallbackActive) ?: return emptyList()
		return listOf(badge)
	}

	/** The active HDR mode of [stream], if any, as an OSD badge. */
	@JvmOverloads
	fun hdrBadge(
		stream: MediaStream,
		context: Context? = null,
		isDvFallbackActive: Boolean = resolveFallbackActive(context),
	): BadgeInfo? = hdrBadge(stream.videoRangeType, stream.videoRange, context, isDvFallbackActive)

	fun hdrBadge(
		stream: MediaStream,
		isDvFallbackActive: Boolean,
	): BadgeInfo? = hdrBadge(stream.videoRangeType, stream.videoRange, null, isDvFallbackActive)

	@JvmOverloads
	fun hdrBadge(
		rangeType: VideoRangeType?,
		range: VideoRange?,
		context: Context? = null,
		isDvFallbackActive: Boolean = resolveFallbackActive(context),
	): BadgeInfo? {
		val rangeName = rangeType?.name.orEmpty()
		val rawText = range?.name.orEmpty()

		if (rangeType != null && rangeType in DOLBY_VISION_TYPES) {
			// Dolby Vision wins over the HDR base layer it carries, unless playback is being
			// rewritten to that base layer. Only profiles with an HDR base layer can be demoted,
			// so a plain profile 5 stream keeps claiming Dolby Vision.
			if (!isDvFallbackActive || !hasHdrBaseLayer(rangeType, rangeName, rawText)) {
				return BadgeInfo("Dolby Vision")
			}
			return BadgeInfo(dolbyVisionBaseLayerBadge(rangeType, rangeName, rawText))
		}

		if (rangeType == VideoRangeType.HDR10_PLUS || rangeName.contains("HDR10+", ignoreCase = true) || rawText.contains("HDR10+", ignoreCase = true)) {
			return BadgeInfo("HDR10+")
		}

		if (rangeType == VideoRangeType.HLG || rangeName.contains("HLG", ignoreCase = true) || rawText.contains("HLG", ignoreCase = true)) {
			return BadgeInfo("HLG")
		}

		if (rangeType == VideoRangeType.HDR10 ||
			rawText.contains("HDR10", ignoreCase = true) ||
			rawText.contains("HDR", ignoreCase = true) ||
			range == VideoRange.HDR
		) {
			return BadgeInfo("HDR10")
		}

		return null
	}

	fun hdrBadge(
		rangeType: VideoRangeType?,
		range: VideoRange?,
		isDvFallbackActive: Boolean,
	): BadgeInfo? = hdrBadge(rangeType, range, null, isDvFallbackActive)

	/**
	 * Whether a Dolby Vision stream also carries an HDR base layer (HDR10, HDR10+ or HLG). Only
	 * those streams can be rewired to their HEVC base layer, so only those may stop advertising
	 * Dolby Vision when the fallback is active. A profile 5 stream (`DOVI`) has none.
	 */
	private fun hasHdrBaseLayer(rangeType: VideoRangeType, rangeName: String, rawText: String): Boolean =
		rangeType in HDR_BASE_LAYER_TYPES ||
			(rangeType != VideoRangeType.DOVI_WITH_SDR && (
				rangeName.contains("HDR10+", ignoreCase = true) ||
					rangeName.contains("HDR10", ignoreCase = true) ||
					rangeName.contains("HLG", ignoreCase = true) ||
					rawText.contains("HDR10+", ignoreCase = true) ||
					rawText.contains("HDR10", ignoreCase = true) ||
					rawText.contains("HLG", ignoreCase = true)
				))

	/** HDR label reported for a Dolby Vision stream that is being decoded as its base layer. */
	private fun dolbyVisionBaseLayerBadge(rangeType: VideoRangeType, rangeName: String, rawText: String): String = when {
		rangeType == VideoRangeType.DOVI_WITH_HDR10_PLUS ||
			rangeType == VideoRangeType.DOVI_WITH_ELHDR10_PLUS ||
			rangeName.contains("HDR10+", ignoreCase = true) ||
			rawText.contains("HDR10+", ignoreCase = true) -> "HDR10+"

		rangeType == VideoRangeType.DOVI_WITH_HLG ||
			rangeName.contains("HLG", ignoreCase = true) ||
			rawText.contains("HLG", ignoreCase = true) -> "HLG"

		else -> "HDR10"
	}

	/**
	 * Label for the TV-style HDR toast shown when playback starts, e.g. `DOLBY VISION` or `HDR10+`.
	 * Returns null for SDR content so no toast is displayed at all.
	 */
	@JvmOverloads
	fun getActiveHdrLabel(
		item: BaseItemDto?,
		mediaSource: MediaSourceInfo?,
		context: Context? = null,
		isDvFallbackActive: Boolean = resolveFallbackActive(context),
	): String? {
		val streams: List<MediaStream> = mediaSource?.mediaStreams
			?: item?.mediaStreams
			?: emptyList()

		return getActiveHdrLabel(streams, context, isDvFallbackActive)
	}

	fun getActiveHdrLabel(
		item: BaseItemDto?,
		mediaSource: MediaSourceInfo?,
		isDvFallbackActive: Boolean,
	): String? = getActiveHdrLabel(item, mediaSource, null, isDvFallbackActive)

	@JvmOverloads
	fun getActiveHdrLabel(
		streams: List<MediaStream>,
		context: Context? = null,
		isDvFallbackActive: Boolean = resolveFallbackActive(context),
	): String? {
		val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO } ?: return null
		val badge = hdrBadge(videoStream, context, isDvFallbackActive) ?: return null

		return when (badge.text) {
			"Dolby Vision" -> "DOLBY VISION"
			"HLG" -> "HLG"
			else -> badge.text.uppercase()
		}
	}

	fun getActiveHdrLabel(
		streams: List<MediaStream>,
		isDvFallbackActive: Boolean,
	): String? = getActiveHdrLabel(streams, null, isDvFallbackActive)


	private fun audioBadge(stream: MediaStream): BadgeInfo? {
		val codec = stream.codec?.lowercase()
		val profile = stream.profile.orEmpty()
		val title = stream.title.orEmpty()
		val isAtmos = profile.contains("Dolby Atmos", ignoreCase = true) ||
			title.contains("Atmos", ignoreCase = true)

		if (isAtmos) {
			return when (codec) {
				"truehd" -> BadgeInfo("TrueHD Atmos", isHighlight = true)
				"eac3" -> BadgeInfo("DD+ Atmos", isHighlight = true)
				else -> BadgeInfo("Dolby Atmos", isHighlight = true)
			}
		}

		if (profile.contains("DTS:X", ignoreCase = true)) return BadgeInfo("DTS:X")
		if (profile.contains("DTS-HD", ignoreCase = true) || profile.contains("DTS:HD", ignoreCase = true)) {
			return BadgeInfo("DTS-HD MA")
		}

		return when (codec) {
			"truehd" -> BadgeInfo("TrueHD")
			"dca", "dts" -> BadgeInfo("DTS")
			"eac3" -> BadgeInfo("DD+")
			"ac3" -> BadgeInfo("Dolby Digital")
			"flac" -> BadgeInfo("FLAC")
			null, "" -> null
			else -> BadgeInfo(codec.uppercase())
		}
	}

	private fun channelBadge(stream: MediaStream): BadgeInfo? {
		stream.channelLayout?.takeIf { it.isNotBlank() }?.let { return BadgeInfo(it) }

		return when (stream.channels) {
			8 -> BadgeInfo("7.1")
			6 -> BadgeInfo("5.1")
			2 -> BadgeInfo("2.0")
			else -> null
		}
	}
}
