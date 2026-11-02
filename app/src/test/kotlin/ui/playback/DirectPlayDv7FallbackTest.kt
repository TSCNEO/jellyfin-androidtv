package ui.playback

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.playback.exoplayer.directplay.DolbyVisionDisplayHelper
import org.jellyfin.playback.exoplayer.directplay.Dv7FallbackMode

class DirectPlayDv7FallbackTest : FunSpec({

	test("DolbyVisionDisplayHelper identifies profile 7 and 8 correctly") {
		val dv7Format1 = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.07.06")
			.build()
		val dv7Format2 = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvh1.07.01")
			.build()
		val dv8Format1 = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.08.01")
			.build()
		val dv8Format2 = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvh1.08.06")
			.build()
		val dv5Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.05.09")
			.build()

		DolbyVisionDisplayHelper.isDolbyVisionProfile7(dv7Format1) shouldBe true
		DolbyVisionDisplayHelper.isDolbyVisionProfile7(dv7Format2) shouldBe true
		DolbyVisionDisplayHelper.isDolbyVisionProfile8(dv8Format1) shouldBe true
		DolbyVisionDisplayHelper.isDolbyVisionProfile8(dv8Format2) shouldBe true
		DolbyVisionDisplayHelper.isHevcBaseLayerDolbyVision(dv7Format1) shouldBe true
		DolbyVisionDisplayHelper.isHevcBaseLayerDolbyVision(dv8Format1) shouldBe true
		DolbyVisionDisplayHelper.isHevcBaseLayerDolbyVision(dv5Format) shouldBe false
	}

	test("shouldFallbackToHevc in AUTO mode maps Profile 7 and 8 to HEVC on non-DV display (Samsung)") {
		val dv7Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.07.06")
			.build()
		val dv8Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.08.01")
			.build()

		DolbyVisionDisplayHelper.shouldFallbackToHevc(dv7Format, Dv7FallbackMode.AUTO, displaySupportsDolbyVision = false) shouldBe true
		DolbyVisionDisplayHelper.shouldFallbackToHevc(dv8Format, Dv7FallbackMode.AUTO, displaySupportsDolbyVision = false) shouldBe true
	}

	test("shouldFallbackToHevc in AUTO mode preserves Dolby Vision on DV display (LG OLED)") {
		val dv7Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.07.06")
			.build()
		val dv8Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.08.01")
			.build()

		DolbyVisionDisplayHelper.shouldFallbackToHevc(dv7Format, Dv7FallbackMode.AUTO, displaySupportsDolbyVision = true) shouldBe false
		DolbyVisionDisplayHelper.shouldFallbackToHevc(dv8Format, Dv7FallbackMode.AUTO, displaySupportsDolbyVision = true) shouldBe false
	}

	test("shouldFallbackToHevc respects ALWAYS and DISABLED modes") {
		val dv7Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.07.06")
			.build()
		val dv8Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.08.01")
			.build()

		DolbyVisionDisplayHelper.shouldFallbackToHevc(dv7Format, Dv7FallbackMode.ALWAYS, displaySupportsDolbyVision = true) shouldBe true
		DolbyVisionDisplayHelper.shouldFallbackToHevc(dv8Format, Dv7FallbackMode.ALWAYS, displaySupportsDolbyVision = true) shouldBe true
		DolbyVisionDisplayHelper.shouldFallbackToHevc(dv7Format, Dv7FallbackMode.DISABLED, displaySupportsDolbyVision = false) shouldBe false
		DolbyVisionDisplayHelper.shouldFallbackToHevc(dv8Format, Dv7FallbackMode.DISABLED, displaySupportsDolbyVision = false) shouldBe false
	}
})
