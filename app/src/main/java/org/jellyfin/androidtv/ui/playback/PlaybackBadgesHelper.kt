package org.jellyfin.androidtv.ui.playback

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

	private val DOLBY_VISION_HDR10_PLUS_TYPES = setOf(
		VideoRangeType.DOVI_WITH_HDR10_PLUS,
		VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
	)

	/**
	 * @param item the currently playing item (used to resolve media streams when no media source is given)
	 * @param mediaSource the active media source, if known
	 * @param activeAudioIndex the currently selected audio stream index, or null to use the default stream
	 */
	fun getBadges(
		item: BaseItemDto?,
		mediaSource: MediaSourceInfo?,
		activeAudioIndex: Int? = null,
	): List<BadgeInfo> {
		val streams: List<MediaStream> = mediaSource?.mediaStreams
			?: item?.mediaStreams
			?: emptyList()

		return getBadges(
			streams = streams,
			defaultAudioIndex = mediaSource?.defaultAudioStreamIndex,
			activeAudioIndex = activeAudioIndex,
		)
	}

	fun getBadges(
		streams: List<MediaStream>,
		defaultAudioIndex: Int? = null,
		activeAudioIndex: Int? = null,
	): List<BadgeInfo> {
		if (streams.isEmpty()) return emptyList()

		val badges = mutableListOf<BadgeInfo>()

		val videoStream = streams.firstOrNull { it.type == MediaStreamType.VIDEO }
		videoStream?.let { stream ->
			resolutionBadge(stream)?.let(badges::add)
			badges += hdrBadges(stream)
		}

		val audioStream = resolveAudioStream(streams, defaultAudioIndex, activeAudioIndex)
		audioStream?.let { stream ->
			audioBadge(stream)?.let(badges::add)
			channelBadge(stream)?.let(badges::add)
		}

		return badges
	}

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
	 * Returns the HDR badges for a video stream. Dolby Vision can be combined with HDR10/HDR10+
	 * in the same stream (profiles 7/8), so the result may hold more than one badge.
	 */
	private fun hdrBadges(stream: MediaStream): List<BadgeInfo> {
		val rangeType = stream.videoRangeType
		val range = stream.videoRange
		val rangeName = rangeType?.name.orEmpty()
		val rawText = stream.videoRange?.name.orEmpty()

		// Dolby Vision first: it wins over the HDR base layer it carries.
		if (rangeType != null && rangeType in DOLBY_VISION_TYPES) {
			val badges = mutableListOf(BadgeInfo("Dolby Vision"))
			val hasHdr10Plus = rangeType in DOLBY_VISION_HDR10_PLUS_TYPES ||
				rawText.contains("HDR10+", ignoreCase = true)
			val hasHdr10 = rangeType == VideoRangeType.DOVI_WITH_HDR10

			if (hasHdr10Plus) badges += BadgeInfo("HDR10+")
			if (hasHdr10) badges += BadgeInfo("HDR10")
			return badges
		}

		if (rangeType == VideoRangeType.HDR10_PLUS || rawText.contains("HDR10+", ignoreCase = true)) {
			return listOf(BadgeInfo("HDR10+"))
		}

		if (rangeType == VideoRangeType.HLG || rangeName.contains("HLG", ignoreCase = true) || rawText.contains("HLG", ignoreCase = true)) {
			return listOf(BadgeInfo("HLG"))
		}

		if (rangeType == VideoRangeType.HDR10 ||
			rawText.contains("HDR10", ignoreCase = true) ||
			rawText.contains("HDR", ignoreCase = true) ||
			range == VideoRange.HDR
		) {
			return listOf(BadgeInfo("HDR10"))
		}

		return emptyList()
	}

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
