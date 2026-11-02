package org.jellyfin.androidtv.ui.settings.screen.about

import android.content.ClipData
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.settings.util.copyAction

@Composable
fun SettingsAboutScreen(launchedFromLogin: Boolean = false) {
	val router = LocalRouter.current

	SettingsColumn {
		if (launchedFromLogin) item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_login).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_about_title)) },
			)
		} else item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.settings).uppercase()) },
				headingContent = { Text(stringResource(R.string.pref_about_title)) },
			)
		}

		item {
			val heading = "Jellyfin app version"
			val caption = "jellyfin-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}"
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_jellyfin), contentDescription = null) },
				headingContent = { Text(heading) },
				captionContent = { Text(caption) },
				onClick = copyAction(ClipData.newPlainText(heading, caption)),
				modifier = Modifier.focusKey("version")
			)
		}

		item {
			val updateRepository = org.koin.compose.koinInject<org.jellyfin.androidtv.update.UpdateRepository>()
			val updateState by updateRepository.state.collectAsState()
			val caption = when (val state = updateState) {
				is org.jellyfin.androidtv.update.UpdateState.Idle,
				is org.jellyfin.androidtv.update.UpdateState.Unsupported -> stringResource(R.string.pref_check_updates_title)
				is org.jellyfin.androidtv.update.UpdateState.Checking -> stringResource(R.string.loading)
				is org.jellyfin.androidtv.update.UpdateState.UpToDate -> stringResource(R.string.pref_update_up_to_date)
				is org.jellyfin.androidtv.update.UpdateState.Available -> "${stringResource(R.string.pref_update_available)}: v${state.update.version}"
				is org.jellyfin.androidtv.update.UpdateState.Downloading -> "${stringResource(R.string.pref_update_downloading)} ${(state.progress * 100).toInt()}%"
				is org.jellyfin.androidtv.update.UpdateState.Installing -> stringResource(R.string.pref_update_installing)
				is org.jellyfin.androidtv.update.UpdateState.NeedsPermission -> stringResource(R.string.pref_update_permission_required)
				is org.jellyfin.androidtv.update.UpdateState.Failed -> state.message
			}
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_guide), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_check_updates_title)) },
				captionContent = { Text(caption) },
				onClick = {
					when (updateState) {
						is org.jellyfin.androidtv.update.UpdateState.Available,
						is org.jellyfin.androidtv.update.UpdateState.NeedsPermission,
						is org.jellyfin.androidtv.update.UpdateState.Failed -> updateRepository.downloadAndInstall()
						else -> updateRepository.check(manual = true)
					}
				},
				modifier = Modifier.focusKey("check_updates")
			)
		}

		item {
			val heading = stringResource(R.string.pref_device_model)
			val caption = "${Build.MANUFACTURER} ${Build.MODEL}"
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_tv), contentDescription = null) },
				headingContent = { Text(heading) },
				captionContent = { Text(caption) },
				onClick = copyAction(ClipData.newPlainText(heading, caption)),
				modifier = Modifier.focusKey("device_model")
			)
		}

		item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_guide), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.licenses_link)) },
				onClick = { router.push(Routes.LICENSES) },
				modifier = Modifier.focusKey(Routes.LICENSES)
			)
		}

		if (!launchedFromLogin) item {
			ListButton(
				leadingContent = { Icon(painterResource(R.drawable.ic_flask), contentDescription = null) },
				headingContent = { Text(stringResource(R.string.pref_developer_link)) },
				onClick = { router.push(Routes.DEVELOPER) },
				modifier = Modifier.focusKey(Routes.DEVELOPER)
			)
		}
	}
}
