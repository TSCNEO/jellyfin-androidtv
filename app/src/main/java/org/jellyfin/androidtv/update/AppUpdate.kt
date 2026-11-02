package org.jellyfin.androidtv.update

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdate(
	val version: String,
	val notes: String,
	val apkUrl: String,
	val sizeBytes: Long,
)
