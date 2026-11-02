package org.jellyfin.androidtv.update

sealed interface UpdateState {
	data object Idle : UpdateState
	data object Checking : UpdateState
	data object Unsupported : UpdateState
	data class UpToDate(val version: String) : UpdateState
	data class Available(val currentVersion: String, val update: AppUpdate) : UpdateState
	data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
	data class Installing(val update: AppUpdate) : UpdateState
	data class NeedsPermission(val update: AppUpdate) : UpdateState
	data class Failed(val message: String, val update: AppUpdate? = null) : UpdateState
}
