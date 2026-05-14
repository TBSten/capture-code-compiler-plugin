package me.tbsten.capture.code.compat.k200

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.ServiceLoader
import me.tbsten.capture.code.compat.CompatContext

/**
 * task-065: `:compiler-plugin:compat-k200` 専用の最小 sanity test。
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
 * Sanity test 自体は kctfork を必要としない — 「ServiceLoader 経由で factory が
 * 発見できる」「minVersion が baseline と一致する」 ことだけを verify すれば、
 * compat module が plugin 出荷 artifact 内で正しく振る舞う前提を満たす。
 * 実際の compile + IR transform は `:compiler-plugin:test` と
 * `:integration-test:*` で担保している。
 */
class K200CompatContextSanityTest : StringSpec({

    "K200 CompatContext.Factory is discoverable via ServiceLoader" {
        val k200Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .firstOrNull { it::class.java.name.contains(".compat.k200.") }

        k200Factory.shouldNotBeNull()
    }

    "K200 factory advertises minVersion = 2.0.0 (baseline SSOT)" {
        val k200Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k200.") }

        // baseline minVersion は CompatContextImpl と SSOT で一致していなければならない。
        // ここで drift すると plugin 出荷 artifact の resolveFactory が誤って
        // 高 minor を選択してしまう。
        k200Factory.minVersion shouldStartWith "2.0"
        k200Factory.minVersion shouldBe "2.0.0"
    }

    "K200 factory creates a CompatContextImpl instance" {
        val k200Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k200.") }

        val ctx = k200Factory.create()
        ctx.shouldBeInstanceOf<CompatContextImpl>()
    }
})
