package org.jellyfin.androidtv.ui.settings.screen.playback

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.preference.constant.BufferLength
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject

/**
 * Minimum total RAM (2.8 GiB) required to offer the [BufferLength.AGGRESSIVE] profile. The ~200 MiB
 * of slack absorbs the gap between the marketed capacity and `totalMem` on 3 GB boxes (Homatics
 * reports ~3.0 GB) while still excluding real 2 GB devices (Xiaomi TV reports ~1.8-2.0 GB).
 */
private const val AGGRESSIVE_MIN_TOTAL_MEM_BYTES: Long = (2.8 * 1024 * 1024 * 1024).toLong()

internal fun totalMemoryBytes(context: Context): Long {
	val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
		?: return 0L
	return ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo).totalMem
}

/**
 * Whether the device can hold the 512 MB sample budget of [BufferLength.AGGRESSIVE] without the low
 * memory killer evicting the player process.
 */
internal fun supportsAggressiveBuffer(totalMemoryBytes: Long): Boolean =
	totalMemoryBytes >= AGGRESSIVE_MIN_TOTAL_MEM_BYTES

/**
 * Buffer profiles this device is allowed to select. [BufferLength.AGGRESSIVE] is hidden on devices
 * with less than 3 GB of RAM.
 */
internal fun availableBufferLengths(totalMemoryBytes: Long): List<BufferLength> {
	val supportsAggressive = supportsAggressiveBuffer(totalMemoryBytes)
	return BufferLength.entries.filter { it != BufferLength.AGGRESSIVE || supportsAggressive }
}

@Composable
fun SettingsPlaybackBufferLengthScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var bufferLength by rememberPreference(userPreferences, UserPreferences.bufferLength)

	val context = LocalContext.current
	val totalMem = remember(context) { totalMemoryBytes(context) }
	val entries = remember(totalMem) { availableBufferLengths(totalMem) }
	val supportsAggressive = BufferLength.AGGRESSIVE in entries

	// A profile stored on another box (or set before this filter existed) must not stay selected
	// where it cannot fit: fall back to the adaptive profile instead of rendering no radio selected.
	LaunchedEffect(supportsAggressive) {
		if (!supportsAggressive && bufferLength == BufferLength.AGGRESSIVE) {
			bufferLength = BufferLength.AUTO
		}
	}

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback_advanced).uppercase()) },
				headingContent = { Text(stringResource(R.string.playback_buffer_length)) },
			)
		}

		items(entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.nameRes)) },
				captionContent = { Text(stringResource(entry.descriptionRes)) },
				trailingContent = { RadioButton(checked = bufferLength == entry) },
				onClick = {
					bufferLength = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("buffer_length_${entry.name}", initialFocus = bufferLength == entry)
			)
		}
	}
}
