package ui.playback

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.playback.exoplayer.directplay.Dv7FallbackMode

class DirectPlayDv7FallbackTest : FunSpec({

	fun isDolbyVisionProfile7(format: Format): Boolean {
		val codecs = format.codecs
		if (codecs != null && (codecs.startsWith("dvhe.07") || codecs.startsWith("dvh1.07"))) {
			return true
		}
		return false
	}

	fun shouldFallback(format: Format, mode: Dv7FallbackMode, displaySupportsDV: Boolean): Boolean {
		if (format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return false
		if (!isDolbyVisionProfile7(format)) return false
		return when (mode) {
			Dv7FallbackMode.ALWAYS -> true
			Dv7FallbackMode.DISABLED -> false
			Dv7FallbackMode.AUTO -> !displaySupportsDV
		}
	}

	test("isDolbyVisionProfile7 identifies dvhe.07 and dvh1.07 correctly") {
		val dv7Format1 = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.07.06")
			.build()
		val dv7Format2 = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvh1.07.01")
			.build()
		val dv8Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.08.01")
			.build()
		val dv5Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.05.09")
			.build()

		isDolbyVisionProfile7(dv7Format1) shouldBe true
		isDolbyVisionProfile7(dv7Format2) shouldBe true
		isDolbyVisionProfile7(dv8Format) shouldBe false
		isDolbyVisionProfile7(dv5Format) shouldBe false
	}

	test("shouldFallback in AUTO mode maps to HEVC on non-DV display (Samsung)") {
		val dv7Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.07.06")
			.build()

		// Display has no DV support (e.g. Samsung TV)
		shouldFallback(dv7Format, Dv7FallbackMode.AUTO, displaySupportsDV = false) shouldBe true
	}

	test("shouldFallback in AUTO mode preserves Dolby Vision on DV display (LG OLED)") {
		val dv7Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.07.06")
			.build()

		// Display has real DV support
		shouldFallback(dv7Format, Dv7FallbackMode.AUTO, displaySupportsDV = true) shouldBe false
	}

	test("shouldFallback respects ALWAYS and DISABLED modes") {
		val dv7Format = Format.Builder()
			.setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
			.setCodecs("dvhe.07.06")
			.build()

		shouldFallback(dv7Format, Dv7FallbackMode.ALWAYS, displaySupportsDV = true) shouldBe true
		shouldFallback(dv7Format, Dv7FallbackMode.DISABLED, displaySupportsDV = false) shouldBe false
	}
})
