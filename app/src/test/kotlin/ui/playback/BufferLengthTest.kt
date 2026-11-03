package ui.playback

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.preference.constant.BufferLength
import org.jellyfin.androidtv.preference.constant.HIGH_BUFFER_BYTES
import org.jellyfin.androidtv.preference.constant.LOW_BUFFER_BYTES
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BufferLengthTest : FunSpec({

	test("BufferLength AUTO has correct durations and byte budget") {
		BufferLength.AUTO.minBufferDuration shouldBe 25.seconds
		BufferLength.AUTO.maxBufferDuration shouldBe 60.seconds
		BufferLength.AUTO.bufferForPlaybackDuration shouldBe 1500.milliseconds
		BufferLength.AUTO.bufferForPlaybackAfterRebufferDuration shouldBe 3.seconds
		BufferLength.AUTO.targetBufferBytes shouldBe null
		BufferLength.AUTO.prioritizeTimeOverSize shouldBe true
	}

	test("BufferLength HIGH has 256MB budget and time priority") {
		BufferLength.HIGH.minBufferDuration shouldBe 25.seconds
		BufferLength.HIGH.maxBufferDuration shouldBe 60.seconds
		BufferLength.HIGH.targetBufferBytes shouldBe 268435456
		BufferLength.HIGH.targetBufferBytes shouldBe HIGH_BUFFER_BYTES
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
		BufferLength.LOW.targetBufferBytes shouldBe LOW_BUFFER_BYTES
		BufferLength.LOW.prioritizeTimeOverSize shouldBe false
	}

	test("profiles are listed from the largest to the smallest byte budget") {
		BufferLength.entries.map { it.name } shouldBe listOf("AUTO", "HIGH", "STANDARD", "LOW")
		// Every explicit budget stays ordered: HIGH > LOW.
		BufferLength.HIGH.targetBufferBytes!! shouldBe 4 * BufferLength.LOW.targetBufferBytes!!
	}

	test("high capacity profiles prioritize time over size to avoid network hiccups") {
		BufferLength.AUTO.prioritizeTimeOverSize shouldBe true
		BufferLength.HIGH.prioritizeTimeOverSize shouldBe true
		BufferLength.STANDARD.prioritizeTimeOverSize shouldBe false
		BufferLength.LOW.prioritizeTimeOverSize shouldBe false
	}
})
