package org.jellyfin.playback.exoplayer.directplay

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCrypto
import android.os.Build
import android.os.Handler
import android.view.Display
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.CodecSpecificDataUtil
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import timber.log.Timber

open class DirectPlayVideoRenderer(
	private val appContext: Context,
	mediaCodecSelector: MediaCodecSelector,
	allowedJoiningTimeMs: Long,
	enableDecoderFallback: Boolean,
	eventHandler: Handler?,
	eventListener: VideoRendererEventListener?,
	maxDroppedFramesToNotify: Int,
	private val fallbackModeProvider: () -> Dv7FallbackMode,
) : MediaCodecVideoRenderer(
	appContext,
	mediaCodecSelector,
	allowedJoiningTimeMs,
	enableDecoderFallback,
	eventHandler,
	eventListener,
	maxDroppedFramesToNotify,
) {

	fun isDisplaySupportingDolbyVision(context: Context): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
		val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
		val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
		if (!display.isHdr) return false
		val hdrCapabilities = display.hdrCapabilities ?: return false
		val supportedTypes = hdrCapabilities.supportedHdrTypes ?: return false
		return supportedTypes.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
	}

	fun isDolbyVisionProfile7(format: Format): Boolean {
		val codecs = format.codecs
		if (codecs != null && (codecs.startsWith("dvhe.07") || codecs.startsWith("dvh1.07"))) {
			return true
		}
		val profileAndLevel = CodecSpecificDataUtil.getMediaCodecProfileAndLevel(format)
		if (profileAndLevel != null) {
			val profile = profileAndLevel.profile
			// 128 = CodecProfileLevel.DolbyVisionProfileDvheDtb
			// 64  = CodecProfileLevel.DolbyVisionProfileDvheDth
			if (profile == 128 || profile == 64) {
				return true
			}
		}
		return false
	}

	fun shouldFallbackDv7ToHevc(format: Format?): Boolean {
		if (format == null) return false
		val mime = format.sampleMimeType ?: return false
		if (mime != MimeTypes.VIDEO_DOLBY_VISION) return false

		if (!isDolbyVisionProfile7(format)) return false

		val mode = fallbackModeProvider()
		val fallbackActive = when (mode) {
			Dv7FallbackMode.ALWAYS -> true
			Dv7FallbackMode.DISABLED -> false
			Dv7FallbackMode.AUTO -> !isDisplaySupportingDolbyVision(appContext)
		}
		if (fallbackActive) {
			Timber.d(
				"DirectPlayVideoRenderer: DV Profile 7 fallback to HEVC triggered (mode=%s, codecs=%s)",
				mode,
				format.codecs,
			)
		}
		return fallbackActive
	}

	private fun toHevcFormat(format: Format): Format {
		return format.buildUpon()
			.setSampleMimeType(MimeTypes.VIDEO_H265)
			.setCodecs(null)
			.build()
	}

	override fun supportsFormat(
		mediaCodecSelector: MediaCodecSelector,
		format: Format,
	): Int {
		if (shouldFallbackDv7ToHevc(format)) {
			return super.supportsFormat(mediaCodecSelector, toHevcFormat(format))
		}
		return super.supportsFormat(mediaCodecSelector, format)
	}

	override fun getDecoderInfos(
		mediaCodecSelector: MediaCodecSelector,
		format: Format,
		requiresSecureDecoder: Boolean,
	): List<MediaCodecInfo> {
		if (shouldFallbackDv7ToHevc(format)) {
			val hevcFormat = toHevcFormat(format)
			val alternativeDecoders = super.getDecoderInfos(mediaCodecSelector, hevcFormat, requiresSecureDecoder)
			if (alternativeDecoders.isNotEmpty()) {
				Timber.i(
					"DirectPlayVideoRenderer: DV Profile 7 routed to HEVC decoder: %s",
					alternativeDecoders.firstOrNull()?.name,
				)
				return alternativeDecoders
			}
		}
		return super.getDecoderInfos(mediaCodecSelector, format, requiresSecureDecoder)
	}

	override fun getMediaCodecConfiguration(
		codecInfo: MediaCodecInfo,
		format: Format,
		crypto: MediaCrypto?,
		codecOperatingRate: Float,
	): MediaCodecAdapter.Configuration {
		val targetFormat = if (shouldFallbackDv7ToHevc(format) && MimeTypes.VIDEO_H265 == codecInfo.mimeType) {
			toHevcFormat(format)
		} else {
			format
		}
		return super.getMediaCodecConfiguration(codecInfo, targetFormat, crypto, codecOperatingRate)
	}

	override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
		val format = formatHolder.format
		if (format != null && shouldFallbackDv7ToHevc(format)) {
			formatHolder.format = toHevcFormat(format)
		}
		return super.onInputFormatChanged(formatHolder)
	}
}
