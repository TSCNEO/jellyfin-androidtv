package org.jellyfin.androidtv.data.compat

class VideoOptions : AudioOptions() {
	var audioStreamIndex: Int? = null
	var subtitleStreamIndex: Int? = null
	var alwaysBurnInSubtitleWhenTranscoding: Boolean = false
	/** Only for experimental internal on-demand video playback. */
	var forceDirectPlay: Boolean = false
}
