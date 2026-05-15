package me.tbsten.capture.code.compat.k240rc

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.ServiceLoader
import me.tbsten.capture.code.compat.CompatContext

/**
 * task-076: `:compiler-plugin:compat-k240rc` 専用の最小 sanity test。
 *
 * ## なぜ kctfork を使わないか
 *
 * kctfork は `kotlin-compiler-embeddable` を **transitive runtime dependency** として
 * 引き込むため、 baseline (Kotlin 2.0.0) と CI matrix の bumped Kotlin (2.4.0-RC+) を
 * 同一 testClasspath で両立できない (NoSuchMethodError 多発)。 task-065 で
 * `compat-kXXX` 各 module の test source set を独立化することで、 各々が
 * **自身の baseline Kotlin (kotlin-compiler-embeddable-kXXX)** に固定された
 * test classpath を持てる構造にした。
 *
 * `:compat-k240rc:test` は **CI matrix bumped to 2.4.0-RC+ の cell でのみ実行** される
 * 設計 (ci.yml 側の case 文で切替)。 sanity の範囲では kctfork-based KotlinCompilation
 * は走らせず、 ServiceLoader による factory discovery + minVersion 整合のみを
 * 確認する。
 *
 * ## pre-release tier の動作確認
 *
 * 本 module の Factory は `minVersion = "2.4.0-RC"` を宣言する。 これは
 * `KotlinToolingVersion` における **RC tier (Maturity.RC)** に分類される
 * pre-release バージョンであり、 stable 2.3.x との比較では major.minor.patch
 * が優先されるため `2.4.0-RC > 2.3.0` となる。 また `2.4.0-RC < 2.4.0` (STABLE)
 * となる点も `CompatContext.Companion.resolveFactory()` の `findHighestCompatibleFactory`
 * の選択ロジックで正しく扱われる前提。
 */
class K240RcCompatContextSanityTest : StringSpec({

    "K240Rc CompatContext.Factory is discoverable via ServiceLoader" {
        val k240rcFactory = ServiceLoader.load(CompatContext.Factory::class.java)
            .firstOrNull { it::class.java.name.contains(".compat.k240rc.") }

        k240rcFactory.shouldNotBeNull()
    }

    "K240Rc factory advertises minVersion = 2.4.0-RC (compat-k240rc baseline SSOT)" {
        val k240rcFactory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k240rc.") }

        // ここを bump する際は CompatContextImpl 側 minVersion も同時に bump する
        // (SSOT は CompatContextImpl.Factory.minVersion)。
        k240rcFactory.minVersion shouldStartWith "2.4"
        k240rcFactory.minVersion shouldBe "2.4.0-RC"
    }

    "K240Rc factory creates a CompatContextImpl instance" {
        val k240rcFactory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k240rc.") }

        val ctx = k240rcFactory.create()
        ctx.shouldBeInstanceOf<CompatContextImpl>()
    }
})
