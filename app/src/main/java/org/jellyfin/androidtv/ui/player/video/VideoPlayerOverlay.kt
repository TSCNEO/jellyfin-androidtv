package org.jellyfin.androidtv.ui.player.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.rememberQueueEntry
import org.jellyfin.androidtv.ui.playback.PlaybackBadgesHelper
import org.jellyfin.androidtv.ui.player.base.PlayerOverlayLayout
import org.jellyfin.androidtv.ui.player.base.rememberPlayerOverlayVisibility
import org.jellyfin.androidtv.ui.player.base.toast.MediaToastRegistry
import org.jellyfin.androidtv.ui.player.base.toast.MediaToasts
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.exoplayer.directplay.DolbyVisionDisplayHelper
import org.jellyfin.playback.jellyfin.queue.baseItem
import org.jellyfin.playback.jellyfin.queue.baseItemFlow
import org.koin.compose.koinInject

@Composable
fun VideoPlayerOverlay(
	modifier: Modifier = Modifier,
	playbackManager: PlaybackManager = koinInject(),
	mediaToastRegistry: MediaToastRegistry,
) {
	val context = LocalContext.current
	val userPreferences = koinInject<UserPreferences>()
	val dv7FallbackMode = userPreferences[UserPreferences.dv7FallbackMode]
	val isDvFallbackActive = DolbyVisionDisplayHelper.isFallbackActive(context, dv7FallbackMode)

	val visibilityState = rememberPlayerOverlayVisibility()
	var showPlaybackInfo by remember { mutableStateOf(false) }

	val entry by rememberQueueEntry(playbackManager)
	val item = entry?.run { baseItemFlow.collectAsState(baseItem) }?.value

	val hdrLabel = remember(item, isDvFallbackActive) {
		PlaybackBadgesHelper.getActiveHdrLabel(
			item = item,
			mediaSource = item?.mediaSources?.firstOrNull(),
			context = context,
			isDvFallbackActive = isDvFallbackActive,
		)
	}
	var showHdrToast by remember(hdrLabel) { mutableStateOf(hdrLabel != null) }
	LaunchedEffect(hdrLabel) {
		if (hdrLabel != null) {
			showHdrToast = true
			delay(3500)
			showHdrToast = false
		} else {
			showHdrToast = false
		}
	}

	Box(modifier = modifier) {
		PlayerOverlayLayout(
			visibilityState = visibilityState,
			header = {
				Column {
					VideoPlayerHeader(
						item = item,
					)
				}
			},
			controls = {
				VideoPlayerControls(
					playbackManager = playbackManager,
					onPlaybackInfoClick = { showPlaybackInfo = !showPlaybackInfo },
				)
			},
		)

		// Playback info overlay - positioned below header area, always visible when enabled
		if (showPlaybackInfo) {
			PlaybackInfoOverlay(
				playbackManager = playbackManager,
				modifier = Modifier
					.align(Alignment.TopStart)
					.padding(start = 48.dp, top = 100.dp)
			)
		}

		AnimatedVisibility(
			visible = showHdrToast && hdrLabel != null,
			enter = fadeIn(animationSpec = tween(200)),
			exit = fadeOut(animationSpec = tween(500)),
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = 28.dp, end = 52.dp)
		) {
			if (hdrLabel != null) {
				Box(
					modifier = Modifier
						.background(Color(0xCC101418), RoundedCornerShape(6.dp))
						.border(1.dp, Color(0x6600A4DC), RoundedCornerShape(6.dp))
						.padding(horizontal = 14.dp, vertical = 7.dp)
				) {
					Text(
						text = hdrLabel,
						color = Color.White,
						fontSize = 16.sp,
						fontWeight = FontWeight.Medium,
						letterSpacing = 1.sp,
					)
				}
			}
		}

		MediaToasts(mediaToastRegistry)
	}
}
