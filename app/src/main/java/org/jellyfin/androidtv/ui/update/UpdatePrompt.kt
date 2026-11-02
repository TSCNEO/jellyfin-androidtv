package org.jellyfin.androidtv.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.Button
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.update.UpdateRepository
import org.jellyfin.androidtv.update.UpdateState
import org.koin.compose.koinInject

/**
 * In-app update prompt. Visible while an update is available (or being applied) and the user
 * has not dismissed the prompt for the current check.
 */
@Composable
fun UpdatePrompt() {
	val updateRepository = koinInject<UpdateRepository>()
	val state by updateRepository.state.collectAsState()
	val promptDismissed by updateRepository.promptDismissed.collectAsState()

	val visible = !promptDismissed && when (state) {
		is UpdateState.Available,
		is UpdateState.Downloading,
		is UpdateState.Installing,
		is UpdateState.NeedsPermission -> true

		else -> false
	}

	DialogBase(
		visible = visible,
		onDismissRequest = { updateRepository.dismissPrompt() },
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.widthIn(min = 320.dp, max = 560.dp)
				.background(JellyfinTheme.colorScheme.surface, RoundedCornerShape(8.dp))
				.padding(24.dp)
		) {
			UpdatePromptContent(
				state = state,
				onDismiss = { updateRepository.dismissPrompt() },
				onUpdate = {
					// Never dead-end on the install permission gate: if "install unknown apps"
					// is not granted, send the user straight to the system settings screen.
					if (!updateRepository.checkPermission()) {
						updateRepository.openInstallPermissionSettings()
					} else {
						updateRepository.downloadAndInstall()
					}
				},
				onOpenSettings = {
					updateRepository.checkPermission()
					updateRepository.openInstallPermissionSettings()
				},
			)
		}
	}
}

@Composable
private fun UpdatePromptContent(
	state: UpdateState,
	onDismiss: () -> Unit,
	onUpdate: () -> Unit,
	onOpenSettings: () -> Unit,
) {
	val focusRequester = remember { FocusRequester() }

	val title: String
	val message: String
	var primaryLabel: String? = null
	var primaryAction: (() -> Unit)? = null

	when (state) {
		is UpdateState.Available -> {
			title = stringResource(R.string.pref_update_prompt_title)
			message = stringResource(R.string.pref_update_prompt_message, state.update.version)
			primaryLabel = stringResource(R.string.pref_update_action_update)
			primaryAction = onUpdate
		}

		is UpdateState.Downloading -> {
			title = stringResource(R.string.pref_update_prompt_title)
			message = "${stringResource(R.string.pref_update_downloading)} ${(state.progress * 100).toInt()}%"
		}

		is UpdateState.Installing -> {
			title = stringResource(R.string.pref_update_prompt_title)
			message = stringResource(R.string.pref_update_installing)
		}

		is UpdateState.NeedsPermission -> {
			title = stringResource(R.string.pref_update_prompt_title)
			message = stringResource(R.string.pref_update_permission_desc)
			primaryLabel = stringResource(R.string.pref_update_action_settings)
			primaryAction = onOpenSettings
		}

		else -> {
			title = stringResource(R.string.pref_update_prompt_title)
			message = ""
		}
	}

	Column {
		Text(
			text = title,
			fontSize = 20.sp,
			modifier = Modifier.padding(bottom = 12.dp)
		)
		Text(
			text = message,
			fontSize = 15.sp,
			modifier = Modifier.padding(bottom = 24.dp)
		)

		if (primaryAction != null) {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Button(
					onClick = primaryAction,
					modifier = Modifier.focusRequester(focusRequester)
				) {
					Text(text = primaryLabel.orEmpty())
				}
				Button(onClick = onDismiss) {
					Text(text = stringResource(R.string.pref_update_action_later))
				}
			}

			LaunchedEffect(Unit) {
				runCatching { focusRequester.requestFocus() }
			}
		}
	}
}
