package me.tbsten.capture.code.compat.k230

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.ServiceLoader
import me.tbsten.capture.code.compat.CompatContext

/**
 * task-075: `:compiler-plugin:compat-k230` 専用の最小 sanity test。
 *
 * ## なぜ kctfork を使わないか
 *
 * kctfork は `kotlin-compiler-embeddable` を **transitive runtime dependency** として
 * 引き込むため、 baseline (Kotlin 2.0.0) と CI matrix の bumped Kotlin (2.3.x+) を
 * 同一 testClasspath で両立できない (NoSuchMethodError 多発)。 task-065 で
 * `compat-kXXX` 各 module の test source set を独立化することで、 各々が
 * **自身の baseline Kotlin (kotlin-compiler-embeddable-kXXX)** に固定された
 * test classpath を持てる構造にした。
 *
 * `:compat-k230:test` は **CI matrix bumped to 2.3.x+ の cell でのみ実行** される
 * 設計 (ci.yml 側の case 文で切替)。 sanity の範囲では kctfork-based KotlinCompilation
 * は走らせず、 ServiceLoader による factory discovery + minVersion 整合のみを
 * 確認する。
 */
class K230CompatContextSanityTest : StringSpec({

    "K230 CompatContext.Factory is discoverable via ServiceLoader" {
        val k230Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .firstOrNull { it::class.java.name.contains(".compat.k230.") }

        k230Factory.shouldNotBeNull()
    }

    "K230 factory advertises minVersion = 2.3.0 (compat-k230 baseline SSOT)" {
        val k230Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k230.") }

        // ここを bump する際は CompatContextImpl 側 minVersion も同時に bump する
        // (SSOT は CompatContextImpl.Factory.minVersion)。
        k230Factory.minVersion shouldStartWith "2.3"
        k230Factory.minVersion shouldBe "2.3.0"
    }

    "K230 factory creates a CompatContextImpl instance" {
        val k230Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k230.") }

        val ctx = k230Factory.create()
        ctx.shouldBeInstanceOf<CompatContextImpl>()
    }
})
