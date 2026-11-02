package util.profile.codec

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.util.profile.createExperimentalDirectPlayProfile

class ExperimentalDirectPlayProfileTest : FunSpec({
	test("createExperimentalDirectPlayProfile creates profile with unlimited bitrate and all codecs") {
		val profile = createExperimentalDirectPlayProfile()
		profile.name shouldBe "AndroidTV-ExperimentalDirectPlay"
		profile.maxStreamingBitrate shouldBe Int.MAX_VALUE
		profile.maxStaticBitrate shouldBe Int.MAX_VALUE
		profile.directPlayProfiles.size shouldBe 2
		profile.directPlayProfiles[0].videoCodec shouldBe "av1,h264,hevc,mpeg,mpeg2video,vc1,vp8,vp9"
	}
})
