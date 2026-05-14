package me.tbsten.capture.code.compat.k210

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.ServiceLoader
import me.tbsten.capture.code.compat.CompatContext

/**
 * task-065: `:compiler-plugin:compat-k210` 専用の最小 sanity test。
 *
 * ## なぜ kctfork を使わないか
 *
 * kctfork は `kotlin-compiler-embeddable` を **transitive runtime dependency** として
 * 引き込むため、 baseline (Kotlin 2.0.0) と CI matrix の bumped Kotlin (2.1.x+) を
 * 同一 testClasspath で両立できない (NoSuchMethodError 多発)。 本 ticket では
 * `compat-kXXX` 各 module の test source set を独立化することで、 各々が
 * **自身の baseline Kotlin (kotlin-compiler-embeddable-kXXX)** に固定された
 * test classpath を持てる構造にした。
 *
 * `:compat-k210:test` は **CI matrix bumped to 2.1.x+ の cell でのみ実行** される
 * 設計 (ci.yml 側の case 文で切替)。 sanity の範囲では kctfork-based KotlinCompilation
 * は走らせず、 ServiceLoader による factory discovery + minVersion 整合のみを
 * 確認する。 これによって 2.1.x bump + experimental tier 解除に向けた構造的土台が
 * 整う。
 */
class K210CompatContextSanityTest : StringSpec({

    "K210 CompatContext.Factory is discoverable via ServiceLoader" {
        val k210Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .firstOrNull { it::class.java.name.contains(".compat.k210.") }

        k210Factory.shouldNotBeNull()
    }

    "K210 factory advertises minVersion = 2.1.0 (compat-k210 baseline SSOT)" {
        val k210Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k210.") }

        // ここを bump する際は CompatContextImpl 側 minVersion も同時に bump する
        // (SSOT は CompatContextImpl.Factory.minVersion)。
        k210Factory.minVersion shouldStartWith "2.1"
        k210Factory.minVersion shouldBe "2.1.0"
    }

    "K210 factory creates a CompatContextImpl instance" {
        val k210Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k210.") }

        val ctx = k210Factory.create()
        ctx.shouldBeInstanceOf<CompatContextImpl>()
    }
})
