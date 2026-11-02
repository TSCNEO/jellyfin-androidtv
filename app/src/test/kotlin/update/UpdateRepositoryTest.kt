package update

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.androidtv.update.UpdateRepositoryImpl

class UpdateRepositoryTest : FunSpec({
	test("isNewer compares versions correctly") {
		UpdateRepositoryImpl.isNewer("0.20.1", "0.20.0") shouldBe true
		UpdateRepositoryImpl.isNewer("0.20.0", "0.20.1") shouldBe false
		UpdateRepositoryImpl.isNewer("0.20.0-beta.2", "0.20.0-beta.1") shouldBe true
		UpdateRepositoryImpl.isNewer("0.20.0-beta.4-directplay.2", "0.20.0-beta.4-directplay.1") shouldBe true
		UpdateRepositoryImpl.isNewer("0.20.0-beta.4-directplay.2", "0.20.0-beta.4-fork.1") shouldBe true
		UpdateRepositoryImpl.isNewer("0.20.0-beta.4-fork.1", "0.20.0-beta.4-directplay.2") shouldBe false
		UpdateRepositoryImpl.isNewer("0.20.0", "0.20.0-beta.4-directplay.2") shouldBe true
		UpdateRepositoryImpl.isNewer("0.21.0", "0.20.9") shouldBe true
		UpdateRepositoryImpl.isNewer("0.20.0", "0.20.0") shouldBe false
	}
})
