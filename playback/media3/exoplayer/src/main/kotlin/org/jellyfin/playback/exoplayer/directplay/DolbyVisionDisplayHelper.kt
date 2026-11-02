package org.jellyfin.playback.exoplayer.directplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.CodecSpecificDataUtil

/**
 * Dolby Vision capability detection and fallback policy shared by the renderer and the UI.
 *
 * The renderer rewrites a Dolby Vision stream to its HEVC base layer when the fallback is active,
 * so anything that describes the active HDR mode to the user (OSD badges, startup toast) must use
 * exactly the same decision. Keeping the policy here avoids the classic mismatch where playback is
 * really HDR10+ while the OSD still claims Dolby Vision.
 *
 * Profiles 7 and 8 both carry an HEVC base layer (HDR10, HDR10+ or HLG), which is what makes the
 * rewrite safe: the enhancement layer/RPU is dropped and the base layer bitstream is decoded by the
 * regular HEVC decoder. Profile 5 (single layer, IPTPQc2) has no HDR base layer and is never
 * rewritten.
 */
object DolbyVisionDisplayHelper {
	/**
	 * General purpose Amlogic HEVC decoder. Preferred when a Dolby Vision stream is demoted to its
	 * HEVC base layer, because the vendor Dolby Vision decoder only advertises a subset of profiles
	 * and rejects the rewritten stream on several devices.
	 */
	const val AMLOGIC_HEVC_DECODER = "c2.amlogic.hevc.decoder"

	/**
	 * Dolby Vision profile numbers reported through the codec specific data, mapped to the
	 * `CodecProfileLevel.DolbyVisionProfile*` constants. Only the profiles with an HEVC base layer
	 * are listed, which is why they can be rewritten without losing HDR.
	 */
	private val PROFILE_7_NUMBERS = setOf(64, 128)
	private val PROFILE_8_NUMBERS = setOf(256, 512)
	private val HEVC_BASE_LAYER_PROFILE_NUMBERS = PROFILE_7_NUMBERS + PROFILE_8_NUMBERS

	private val PROFILE_7_CODEC_PREFIXES = listOf("dvhe.07", "dvh1.07")
	private val PROFILE_8_CODEC_PREFIXES = listOf("dvhe.08", "dvh1.08")

	/** Whether the currently connected display advertises native Dolby Vision support. */
	fun isDisplaySupportingDolbyVision(context: Context): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
		val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
		val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
		if (!display.isHdr) return false
		val hdrCapabilities = display.hdrCapabilities ?: return false
		val supportedTypes = hdrCapabilities.supportedHdrTypes ?: return false
		return supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
	}

	/**
	 * Resolves the fallback policy for [mode] against the real display capabilities.
	 * ALWAYS/DISABLED ignore the display, AUTO only falls back when it lacks Dolby Vision.
	 */
	fun isFallbackActive(context: Context, mode: Dv7FallbackMode): Boolean = when (mode) {
		Dv7FallbackMode.ALWAYS -> true
		Dv7FallbackMode.DISABLED -> false
		Dv7FallbackMode.AUTO -> !isDisplaySupportingDolbyVision(context)
	}

	/** Same policy as [isFallbackActive] with the display capability resolved by the caller. */
	fun isFallbackActive(mode: Dv7FallbackMode, displaySupportsDolbyVision: Boolean): Boolean = when (mode) {
		Dv7FallbackMode.ALWAYS -> true
		Dv7FallbackMode.DISABLED -> false
		Dv7FallbackMode.AUTO -> !displaySupportsDolbyVision
	}

	/** Dolby Vision profile 7 (`dvhe.07` / `dvh1.07`, UHD Blu-Ray dual layer). */
	fun isDolbyVisionProfile7(format: Format?): Boolean = matchesProfile(format, PROFILE_7_CODEC_PREFIXES, PROFILE_7_NUMBERS)

	/** Dolby Vision profile 8 (`dvhe.08` / `dvh1.08`, single layer with an HDR10/HDR10+/HLG base). */
	fun isDolbyVisionProfile8(format: Format?): Boolean = matchesProfile(format, PROFILE_8_CODEC_PREFIXES, PROFILE_8_NUMBERS)

	/** Whether the format is a Dolby Vision profile 7 or 8 stream, both of which have an HEVC base layer. */
	fun isHevcBaseLayerDolbyVision(format: Format?): Boolean =
		isDolbyVisionProfile7(format) || isDolbyVisionProfile8(format)

	/**
	 * Full fallback decision for [format]: only Dolby Vision profile 7/8 streams are rewritten, and
	 * only while the configured [mode] says the fallback is active.
	 */
	fun shouldFallbackToHevc(
		format: Format?,
		mode: Dv7FallbackMode,
		displaySupportsDolbyVision: Boolean,
	): Boolean {
		if (format == null) return false
		if (format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION) return false
		if (!isHevcBaseLayerDolbyVision(format)) return false
		return isFallbackActive(mode, displaySupportsDolbyVision)
	}

	/**
	 * Sort key used to promote [AMLOGIC_HEVC_DECODER] above the other HEVC decoders. Higher wins.
	 * Devices without that decoder simply keep their natural order.
	 */
	fun hevcDecoderSortKey(decoderName: String?): Int = if (decoderName == AMLOGIC_HEVC_DECODER) 1 else 0

	private fun matchesProfile(
		format: Format?,
		codecPrefixes: List<String>,
		profileNumbers: Set<Int>,
	): Boolean {
		if (format == null) return false

		val codecs = format.codecs?.lowercase()
		if (codecs != null && codecPrefixes.any { codecs.startsWith(it) }) return true

		val profile = codecProfile(format) ?: return false
		return profile in profileNumbers
	}

	/**
	 * Dolby Vision profile reported by the codec specific data, when present. Parsing is best effort:
	 * formats without CSD (or an unexpected box layout) simply report no profile.
	 */
	private fun codecProfile(format: Format): Int? = runCatching {
		CodecSpecificDataUtil.getMediaCodecProfileAndLevel(format)?.profile
	}.getOrNull()
}
