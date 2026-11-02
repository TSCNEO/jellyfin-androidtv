package ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jellyfin.androidtv.ui.playback.BadgeInfo
import org.jellyfin.androidtv.ui.playback.PlaybackBadgesHelper
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType

class PlaybackBadgesHelperTest : FunSpec({

	val json = Json { ignoreUnknownKeys = true; isLenient = true }

	fun streamJson(
		type: MediaStreamType,
		index: Int,
		width: Int? = null,
		height: Int? = null,
		videoRangeType: VideoRangeType? = null,
		videoRange: VideoRange? = null,
		codec: String? = null,
		profile: String? = null,
		title: String? = null,
		channels: Int? = null,
		channelLayout: String? = null,
		isDefault: Boolean = false,
	): MediaStream = json.decodeFromString(
		MediaStream.serializer(),
		buildJsonObject {
			put("Type", type.serialName)
			put("Index", index)
			put("IsDefault", isDefault)
			put("IsInterlaced", false)
			put("IsForced", false)
			put("IsHearingImpaired", false)
			put("IsExternal", false)
			put("IsTextSubtitleStream", false)
			put("SupportsExternalStream", false)
			width?.let { put("Width", it) }
			height?.let { put("Height", it) }
			videoRangeType?.let { put("VideoRangeType", it.serialName) }
			videoRange?.let { put("VideoRange", it.name) }
			codec?.let { put("Codec", it) }
			profile?.let { put("Profile", it) }
			title?.let { put("Title", it) }
			channels?.let { put("Channels", it) }
			channelLayout?.let { put("ChannelLayout", it) }
		}.toString(),
	)

	fun videoStream(
		width: Int = 3840,
		height: Int = 2160,
		videoRangeType: VideoRangeType? = null,
		videoRange: VideoRange? = null,
	): MediaStream = streamJson(
		type = MediaStreamType.VIDEO,
		index = 0,
		width = width,
		height = height,
		videoRangeType = videoRangeType,
		videoRange = videoRange,
	)

	fun audioStream(
		codec: String? = null,
		profile: String? = null,
		title: String? = null,
		channels: Int? = null,
		channelLayout: String? = null,
		index: Int = 1,
		isDefault: Boolean = false,
	): MediaStream = streamJson(
		type = MediaStreamType.AUDIO,
		index = index,
		codec = codec,
		profile = profile,
		title = title,
		channels = channels,
		channelLayout = channelLayout,
		isDefault = isDefault,
	)

	fun texts(vararg streams: MediaStream, audioIndex: Int? = null, isDvFallbackActive: Boolean = false): List<String> =
		PlaybackBadgesHelper.getBadges(streams = streams.toList(), activeAudioIndex = audioIndex, isDvFallbackActive = isDvFallbackActive)
			.map { it.text }

	test("returns empty list when there is no media information") {
		PlaybackBadgesHelper.getBadges(item = null, mediaSource = null).shouldBeEmpty()
		PlaybackBadgesHelper.getBadges(streams = emptyList()).shouldBeEmpty()
	}

	test("detects 4K, 1080p and 720p resolutions") {
		texts(videoStream(width = 3840, height = 2160)) shouldBe listOf("4K")
		texts(videoStream(width = 1920, height = 1080)) shouldBe listOf("1080p")
		texts(videoStream(width = 1280, height = 720)) shouldBe listOf("720p")
		// Wide-but-short encodes still count as 4K by width.
		texts(videoStream(width = 3840, height = 1600)) shouldBe listOf("4K")
		// SD gets no resolution badge.
		texts(videoStream(width = 720, height = 576)).shouldBeEmpty()
	}

	test("Dolby Vision alone yields a single Dolby Vision badge") {
		texts(videoStream(videoRangeType = VideoRangeType.DOVI, videoRange = VideoRange.HDR)) shouldBe
			listOf("4K", "Dolby Vision")
	}

	test("Dolby Vision with an HDR base layer only shows the Dolby Vision badge when fallback is inactive") {
		texts(videoStream(videoRangeType = VideoRangeType.DOVI_WITH_HDR10), isDvFallbackActive = false) shouldBe
			listOf("4K", "Dolby Vision")
		texts(videoStream(videoRangeType = VideoRangeType.DOVI_WITH_HDR10_PLUS), isDvFallbackActive = false) shouldBe
			listOf("4K", "Dolby Vision")
		texts(videoStream(videoRangeType = VideoRangeType.DOVI_WITH_ELHDR10_PLUS), isDvFallbackActive = false) shouldBe
			listOf("4K", "Dolby Vision")
		texts(videoStream(videoRangeType = VideoRangeType.DOVI_WITH_HLG), isDvFallbackActive = false) shouldBe
			listOf("4K", "Dolby Vision")
	}

	test("Dolby Vision with HDR10+ base layer respects fallback configuration (Proyecto Salvación)") {
		val stream = videoStream(videoRangeType = VideoRangeType.DOVI_WITH_HDR10_PLUS)

		// When fallback is inactive (TV supports DV), report Dolby Vision
		PlaybackBadgesHelper.getBadges(listOf(stream), isDvFallbackActive = false).map { it.text } shouldBe
			listOf("4K", "Dolby Vision")
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(stream), isDvFallbackActive = false) shouldBe "DOLBY VISION"

		// When fallback is active (TV lacks DV, Proyecto Salvación / HDR10+), report HDR10+
		PlaybackBadgesHelper.getBadges(listOf(stream), isDvFallbackActive = true).map { it.text } shouldBe
			listOf("4K", "HDR10+")
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(stream), isDvFallbackActive = true) shouldBe "HDR10+"
	}

	test("Dolby Vision with HLG and HDR10 base layers respect fallback configuration") {
		val hlgStream = videoStream(videoRangeType = VideoRangeType.DOVI_WITH_HLG)
		PlaybackBadgesHelper.getBadges(listOf(hlgStream), isDvFallbackActive = true).map { it.text } shouldBe
			listOf("4K", "HLG")
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(hlgStream), isDvFallbackActive = true) shouldBe "HLG"

		val hdr10Stream = videoStream(videoRangeType = VideoRangeType.DOVI_WITH_HDR10)
		PlaybackBadgesHelper.getBadges(listOf(hdr10Stream), isDvFallbackActive = true).map { it.text } shouldBe
			listOf("4K", "HDR10")
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(hdr10Stream), isDvFallbackActive = true) shouldBe "HDR10"
	}

	test("exactly one HDR badge is returned per stream") {
		val videoStreams = listOf(
			VideoRangeType.DOVI,
			VideoRangeType.DOVI_WITH_HDR10,
			VideoRangeType.DOVI_WITH_HDR10_PLUS,
			VideoRangeType.DOVI_WITH_EL,
			VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
			VideoRangeType.DOVI_WITH_HLG,
			VideoRangeType.DOVI_WITH_SDR,
			VideoRangeType.HDR10,
			VideoRangeType.HDR10_PLUS,
			VideoRangeType.HLG,
			VideoRangeType.SDR,
		)
		val hdrLabels = setOf("Dolby Vision", "HDR10", "HDR10+", "HLG")

		videoStreams.forEach { rangeType ->
			val hdrBadges = texts(videoStream(videoRangeType = rangeType)).filter { it in hdrLabels }
			(hdrBadges.size <= 1) shouldBe true
		}
	}

	test("active HDR label for the playback toast follows the single-mode priority") {
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(videoStream(videoRangeType = VideoRangeType.DOVI)), isDvFallbackActive = false) shouldBe "DOLBY VISION"
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(videoStream(videoRangeType = VideoRangeType.DOVI_WITH_HDR10)), isDvFallbackActive = false) shouldBe "DOLBY VISION"
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(videoStream(videoRangeType = VideoRangeType.HDR10_PLUS))) shouldBe "HDR10+"
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(videoStream(videoRangeType = VideoRangeType.HLG))) shouldBe "HLG"
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(videoStream(videoRangeType = VideoRangeType.HDR10))) shouldBe "HDR10"
		// SDR shows no toast at all.
		PlaybackBadgesHelper.getActiveHdrLabel(listOf(videoStream(videoRangeType = VideoRangeType.SDR))) shouldBe null
		PlaybackBadgesHelper.getActiveHdrLabel(emptyList()) shouldBe null
	}

	test("standalone HDR formats map to the right badge") {
		texts(videoStream(videoRangeType = VideoRangeType.HDR10_PLUS)) shouldBe listOf("4K", "HDR10+")
		texts(videoStream(videoRangeType = VideoRangeType.HDR10, videoRange = VideoRange.HDR)) shouldBe
			listOf("4K", "HDR10")
		texts(videoStream(videoRangeType = VideoRangeType.HLG, videoRange = VideoRange.HDR)) shouldBe
			listOf("4K", "HLG")
	}

	test("SDR content adds no HDR badge") {
		texts(videoStream(videoRangeType = VideoRangeType.SDR, videoRange = VideoRange.SDR)) shouldBe listOf("4K")
	}

	test("Atmos is highlighted and specialised per codec") {
		val trueHd = PlaybackBadgesHelper.getBadges(
			streams = listOf(
				videoStream(),
				audioStream(codec = "truehd", profile = "Dolby Atmos", channels = 8),
			),
		)
		trueHd.map { it.text } shouldBe listOf("4K", "TrueHD Atmos", "7.1")
		trueHd.first { it.text == "TrueHD Atmos" }.isHighlight shouldBe true

		texts(videoStream(), audioStream(codec = "eac3", profile = "Dolby Atmos", channels = 6)) shouldBe
			listOf("4K", "DD+ Atmos", "5.1")

		// Atmos detected through the stream title rather than the profile.
		texts(videoStream(), audioStream(codec = "ac3", title = "Español Atmos 5.1", channels = 6)) shouldBe
			listOf("4K", "Dolby Atmos", "5.1")
	}

	test("DTS variants map to the right badge") {
		texts(videoStream(), audioStream(codec = "dca", profile = "DTS-HD MA", channels = 8)) shouldBe
			listOf("4K", "DTS-HD MA", "7.1")
		texts(videoStream(), audioStream(codec = "dca", profile = "DTS:X", channels = 8)) shouldBe
			listOf("4K", "DTS:X", "7.1")
		texts(videoStream(), audioStream(codec = "dts")) shouldBe listOf("4K", "DTS")
	}

	test("common audio codecs map to friendly labels") {
		texts(videoStream(), audioStream(codec = "eac3")) shouldBe listOf("4K", "DD+")
		texts(videoStream(), audioStream(codec = "ac3")) shouldBe listOf("4K", "Dolby Digital")
		texts(videoStream(), audioStream(codec = "flac")) shouldBe listOf("4K", "FLAC")
		texts(videoStream(), audioStream(codec = "truehd")) shouldBe listOf("4K", "TrueHD")
		// Unknown codecs fall back to the upper-cased codec name.
		texts(videoStream(), audioStream(codec = "opus")) shouldBe listOf("4K", "OPUS")
	}

	test("channel layout string wins over the channel count") {
		texts(videoStream(), audioStream(codec = "eac3", channelLayout = "5.1(side)", channels = 6)) shouldBe
			listOf("4K", "DD+", "5.1(side)")
		texts(videoStream(), audioStream(codec = "aac", channels = 2)) shouldBe listOf("4K", "AAC", "2.0")
	}

	test("selects the requested audio stream by index") {
		val streams = arrayOf(
			videoStream(),
			audioStream(codec = "ac3", channels = 6, index = 1, isDefault = true),
			audioStream(codec = "truehd", profile = "Dolby Atmos", channels = 8, index = 2),
		)

		texts(*streams, audioIndex = 2) shouldBe listOf("4K", "TrueHD Atmos", "7.1")
		// Without an explicit index the default stream is used.
		texts(*streams) shouldBe listOf("4K", "Dolby Digital", "5.1")
		// An unknown index falls back to the default stream instead of failing.
		texts(*streams, audioIndex = 99) shouldBe listOf("4K", "Dolby Digital", "5.1")
	}

	test("highlight flag is only set for object audio") {
		PlaybackBadgesHelper.getBadges(
			streams = listOf(videoStream(), audioStream(codec = "eac3", channels = 6)),
		).all { !it.isHighlight } shouldBe true

		PlaybackBadgesHelper.getBadges(
			streams = listOf(videoStream(), audioStream(codec = "eac3", profile = "Dolby Atmos", channels = 6)),
		).filter { it.isHighlight }.map { it.text }.shouldContainExactly("DD+ Atmos")

		BadgeInfo("HDR10").isHighlight shouldBe false
	}
})
