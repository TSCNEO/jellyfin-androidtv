package ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.androidtv.preference.constant.BufferLength
import kotlin.time.Duration.Companion.seconds

class BufferLengthTest : FunSpec({

	test("BufferLength AUTO has correct durations and byte budget") {
		BufferLength.AUTO.minBufferDuration shouldBe 25.seconds
		BufferLength.AUTO.maxBufferDuration shouldBe 60.seconds
		BufferLength.AUTO.targetBufferBytes shouldBe null
		BufferLength.AUTO.prioritizeTimeOverSize shouldBe true
	}

	test("BufferLength HIGH has 256MB budget and time priority") {
		BufferLength.HIGH.minBufferDuration shouldBe 25.seconds
		BufferLength.HIGH.maxBufferDuration shouldBe 60.seconds
		BufferLength.HIGH.targetBufferBytes shouldBe 268435456
		BufferLength.HIGH.prioritizeTimeOverSize shouldBe true
	}

	test("BufferLength STANDARD has default null durations and targetBufferBytes") {
		BufferLength.STANDARD.minBufferDuration shouldBe null
		BufferLength.STANDARD.maxBufferDuration shouldBe null
		BufferLength.STANDARD.targetBufferBytes shouldBe null
		BufferLength.STANDARD.prioritizeTimeOverSize shouldBe false
	}

	test("BufferLength LOW has 64MB budget and 15s-30s window") {
		BufferLength.LOW.minBufferDuration shouldBe 15.seconds
		BufferLength.LOW.maxBufferDuration shouldBe 30.seconds
		BufferLength.LOW.targetBufferBytes shouldBe 67108864
		BufferLength.LOW.prioritizeTimeOverSize shouldBe false
	}
})
