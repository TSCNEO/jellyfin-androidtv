package org.jellyfin.androidtv.ui.settings.screen.playback

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.playback.exoplayer.directplay.Dv7FallbackMode
import org.koin.compose.koinInject

private val Dv7FallbackMode.headingRes: Int
	get() = when (this) {
		Dv7FallbackMode.AUTO -> R.string.pref_dv7_fallback_auto
		Dv7FallbackMode.ALWAYS -> R.string.pref_dv7_fallback_always
		Dv7FallbackMode.DISABLED -> R.string.pref_dv7_fallback_disabled
	}

private val Dv7FallbackMode.summaryRes: Int
	get() = when (this) {
		Dv7FallbackMode.AUTO -> R.string.pref_dv7_fallback_auto_summary
		Dv7FallbackMode.ALWAYS -> R.string.pref_dv7_fallback_always_summary
		Dv7FallbackMode.DISABLED -> R.string.pref_dv7_fallback_disabled_summary
	}

@Composable
fun SettingsPlaybackDv7FallbackScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()
	var dv7FallbackMode by rememberPreference(userPreferences, UserPreferences.dv7FallbackMode)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_playback_advanced).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_dv7_fallback_title)) },
			)
		}

		items(Dv7FallbackMode.entries) { entry ->
			ListButton(
				headingContent = { Text(stringResource(entry.headingRes)) },
				captionContent = { Text(stringResource(entry.summaryRes)) },
				trailingContent = { RadioButton(checked = dv7FallbackMode == entry) },
				onClick = {
					dv7FallbackMode = entry
					router.back()
				},
				modifier = Modifier
					.focusKey("dv7_fallback_${entry.name}", initialFocus = dv7FallbackMode == entry)
			)
		}
	}
}
