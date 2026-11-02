package org.jellyfin.androidtv.ui.player.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.playback.BadgeInfo
import org.jellyfin.androidtv.ui.playback.PlaybackBadgesHelper
import org.jellyfin.androidtv.ui.player.base.PlayerHeader
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
@Stable
fun VideoPlayerHeader(
	item: BaseItemDto?,
) {
	PlayerHeader {
		if (item != null) {
			Text(
				text = item.name.orEmpty(),
				overflow = TextOverflow.Ellipsis,
				maxLines = 1,
				style = LocalTextStyle.current.copy(
					color = Color.White,
					fontSize = 22.sp
				)
			)

			Text(
				text = item.seriesName.orEmpty(),
				overflow = TextOverflow.Ellipsis,
				maxLines = 1,
				style = LocalTextStyle.current.copy(
					color = Color.White,
					fontSize = 18.sp
				)
			)

			val badges = PlaybackBadgesHelper.getBadges(
				item = item,
				mediaSource = item.mediaSources?.firstOrNull(),
			)
			if (badges.isNotEmpty()) {
				PlayerBadgesRow(badges)
			}
		}
	}
}

@Composable
private fun PlayerBadgesRow(badges: List<BadgeInfo>) {
	Row(
		modifier = Modifier.padding(top = 6.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		badges.forEach { PlayerBadge(it) }
	}
}

@Composable
private fun PlayerBadge(badge: BadgeInfo) {
	val shape = RoundedCornerShape(4.dp)

	Text(
		text = badge.text,
		overflow = TextOverflow.Ellipsis,
		maxLines = 1,
		style = LocalTextStyle.current.copy(
			color = Color.White,
			fontSize = 12.sp,
			fontWeight = if (badge.isHighlight) FontWeight.Bold else FontWeight.Medium,
		),
		modifier = Modifier
			.background(if (badge.isHighlight) Color(0x3300A4DC) else Color(0x26FFFFFF), shape)
			.border(1.dp, if (badge.isHighlight) Color(0xFF00A4DC) else Color(0x40FFFFFF), shape)
			.padding(horizontal = 8.dp, vertical = 3.dp)
	)
}
