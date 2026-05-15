package me.tbsten.capture.code.compat.k220

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.ServiceLoader
import me.tbsten.capture.code.compat.CompatContext

/**
 * task-074: `:compiler-plugin:compat-k220` 専用の最小 sanity test。
 *
 * ## なぜ kctfork を使わないか
 *
 * kctfork は `kotlin-compiler-embeddable` を **transitive runtime dependency** として
 * 引き込むため、 baseline (Kotlin 2.0.0) と CI matrix の bumped Kotlin (2.2.x+) を
 * 同一 testClasspath で両立できない (NoSuchMethodError 多発)。 task-065 で
 * `compat-kXXX` 各 module の test source set を独立化することで、 各々が
 * **自身の baseline Kotlin (kotlin-compiler-embeddable-kXXX)** に固定された
 * test classpath を持てる構造にした。
 *
 * `:compat-k220:test` は **CI matrix bumped to 2.2.x+ の cell でのみ実行** される
 * 設計 (ci.yml 側の case 文で切替)。 sanity の範囲では kctfork-based KotlinCompilation
 * は走らせず、 ServiceLoader による factory discovery + minVersion 整合のみを
 * 確認する。
 */
class K220CompatContextSanityTest : StringSpec({

    "K220 CompatContext.Factory is discoverable via ServiceLoader" {
        val k220Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .firstOrNull { it::class.java.name.contains(".compat.k220.") }

        k220Factory.shouldNotBeNull()
    }

    "K220 factory advertises minVersion = 2.2.0 (compat-k220 baseline SSOT)" {
        val k220Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k220.") }

        // ここを bump する際は CompatContextImpl 側 minVersion も同時に bump する
        // (SSOT は CompatContextImpl.Factory.minVersion)。
        k220Factory.minVersion shouldStartWith "2.2"
        k220Factory.minVersion shouldBe "2.2.0"
    }

    "K220 factory creates a CompatContextImpl instance" {
        val k220Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k220.") }

        val ctx = k220Factory.create()
        ctx.shouldBeInstanceOf<CompatContextImpl>()
    }
})
