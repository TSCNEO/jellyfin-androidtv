package org.jellyfin.androidtv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class UpdateInstallReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
			PackageInstaller.STATUS_PENDING_USER_ACTION -> {
				@Suppress("DEPRECATION")
				val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
				confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				runCatching { context.startActivity(confirm) }
					.onFailure { onFailure?.invoke(PackageInstaller.STATUS_FAILURE, "Couldn't open installer.") }
			}
			PackageInstaller.STATUS_SUCCESS -> Log.i("Update", "Update installed successfully")
			else -> {
				val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
				Log.w("Update", "Installation failed ($status): $message")
				onFailure?.invoke(
					status,
					if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "Installation cancelled." else "Couldn't install update."
				)
			}
		}
	}

	companion object {
		@Volatile
		var onFailure: ((Int, String) -> Unit)? = null
	}
}
