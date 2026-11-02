package org.jellyfin.playback.exoplayer.directplay

import android.content.Context
import android.media.MediaCrypto
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
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

	fun isDisplaySupportingDolbyVision(context: Context): Boolean =
		DolbyVisionDisplayHelper.isDisplaySupportingDolbyVision(context)

	fun isFallbackActive(context: Context, mode: Dv7FallbackMode): Boolean =
		DolbyVisionDisplayHelper.isFallbackActive(context, mode)

	fun isDolbyVisionProfile7(format: Format): Boolean =
		DolbyVisionDisplayHelper.isDolbyVisionProfile7(format)

	fun isDolbyVisionProfile8(format: Format): Boolean =
		DolbyVisionDisplayHelper.isDolbyVisionProfile8(format)

	fun shouldFallbackDv7ToHevc(format: Format?): Boolean =
		shouldFallbackToHevc(format)

	fun shouldFallbackToHevc(format: Format?): Boolean {
		if (format == null) return false
		val mime = format.sampleMimeType ?: return false
		if (mime != MimeTypes.VIDEO_DOLBY_VISION) return false

		if (!DolbyVisionDisplayHelper.isHevcBaseLayerDolbyVision(format)) return false

		val mode = fallbackModeProvider()
		val fallbackActive = DolbyVisionDisplayHelper.isFallbackActive(appContext, mode)
		if (fallbackActive) {
			Timber.d(
				"DirectPlayVideoRenderer: DV Profile 7/8 fallback to HEVC triggered (mode=%s, codecs=%s)",
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
		if (shouldFallbackToHevc(format)) {
			return super.supportsFormat(mediaCodecSelector, toHevcFormat(format))
		}
		return super.supportsFormat(mediaCodecSelector, format)
	}

	override fun getDecoderInfos(
		mediaCodecSelector: MediaCodecSelector,
		format: Format,
		requiresSecureDecoder: Boolean,
	): List<MediaCodecInfo> {
		if (shouldFallbackToHevc(format)) {
			val hevcFormat = toHevcFormat(format)
			val alternativeDecoders = super.getDecoderInfos(mediaCodecSelector, hevcFormat, requiresSecureDecoder)
			if (alternativeDecoders.isNotEmpty()) {
				Timber.i(
					"DirectPlayVideoRenderer: DV Profile 7/8 routed to HEVC decoder: %s",
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
		val targetFormat = if (shouldFallbackToHevc(format) && MimeTypes.VIDEO_H265 == codecInfo.mimeType) {
			toHevcFormat(format)
		} else {
			format
		}
		return super.getMediaCodecConfiguration(codecInfo, targetFormat, crypto, codecOperatingRate)
	}

	override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
		val format = formatHolder.format
		if (format != null && shouldFallbackToHevc(format)) {
			formatHolder.format = toHevcFormat(format)
		}
		return super.onInputFormatChanged(formatHolder)
	}
}
