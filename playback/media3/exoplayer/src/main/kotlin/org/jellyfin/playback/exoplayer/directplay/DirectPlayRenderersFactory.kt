package org.jellyfin.playback.exoplayer.directplay

import android.content.Context
import android.os.Handler
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import java.util.ArrayList

open class DirectPlayRenderersFactory(
	private val context: Context,
	private val fallbackModeProvider: () -> Dv7FallbackMode,
) : DefaultRenderersFactory(context) {

	override fun buildVideoRenderers(
		context: Context,
		extensionRendererMode: Int,
		mediaCodecSelector: MediaCodecSelector,
		enableDecoderFallback: Boolean,
		eventHandler: Handler,
		eventListener: VideoRendererEventListener,
		allowedVideoJoiningTimeMs: Long,
		out: ArrayList<Renderer>,
	) {
		val wrappedSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
			val decoders = mediaCodecSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
			if (mimeType == MimeTypes.VIDEO_H265) {
				decoders.sortedByDescending { DolbyVisionDisplayHelper.hevcDecoderSortKey(it.name) }
			} else {
				decoders
			}
		}

		val renderer = DirectPlayVideoRenderer(
			appContext = context,
			mediaCodecSelector = wrappedSelector,
			allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
			enableDecoderFallback = enableDecoderFallback,
			eventHandler = eventHandler,
			eventListener = eventListener,
			maxDroppedFramesToNotify = 50,
			fallbackModeProvider = fallbackModeProvider,
		)
		out.add(renderer)

		if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
			return
		}
		var extensionRendererIndex = out.size
		if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
			extensionRendererIndex--
		}

		try {
			val clazz = Class.forName("androidx.media3.decoder.vp9.LibvpxVideoRenderer")
			val constructor = clazz.getConstructor(
				Long::class.javaPrimitiveType,
				Handler::class.java,
				VideoRendererEventListener::class.java,
				Int::class.javaPrimitiveType,
			)
			val vp9Renderer = constructor.newInstance(
				allowedVideoJoiningTimeMs,
				eventHandler,
				eventListener,
				50,
			) as Renderer
			out.add(extensionRendererIndex++, vp9Renderer)
		} catch (_: Exception) {
		}

		try {
			val clazz = Class.forName("androidx.media3.decoder.av1.Libgav1VideoRenderer")
			val constructor = clazz.getConstructor(
				Long::class.javaPrimitiveType,
				Handler::class.java,
				VideoRendererEventListener::class.java,
				Int::class.javaPrimitiveType,
			)
			val av1Renderer = constructor.newInstance(
				allowedVideoJoiningTimeMs,
				eventHandler,
				eventListener,
				50,
			) as Renderer
			out.add(extensionRendererIndex++, av1Renderer)
		} catch (_: Exception) {
		}

		try {
			val clazz = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegVideoRenderer")
			val constructor = clazz.getConstructor(
				Long::class.javaPrimitiveType,
				Handler::class.java,
				VideoRendererEventListener::class.java,
				Int::class.javaPrimitiveType,
			)
			val ffmpegRenderer = constructor.newInstance(
				allowedVideoJoiningTimeMs,
				eventHandler,
				eventListener,
				50,
			) as Renderer
			out.add(extensionRendererIndex++, ffmpegRenderer)
		} catch (_: Exception) {
		}
	}
}
