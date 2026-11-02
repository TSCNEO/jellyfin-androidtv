package org.jellyfin.androidtv.update

import kotlinx.coroutines.flow.StateFlow

interface UpdateRepository {
	val state: StateFlow<UpdateState>
	val promptDismissed: StateFlow<Boolean>

	fun check(manual: Boolean)
	fun downloadAndInstall()
	fun dismissPrompt()
}
