package me.tbsten.capture.code.compat.k202

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.ServiceLoader
import me.tbsten.capture.code.compat.CompatContext

/**
 * task-081: `:compiler-plugin:compat-k202` 専用の最小 sanity test。
 *
 * ## なぜ kctfork を使わないか
 *
 * kctfork は `kotlin-compiler-embeddable` を **transitive runtime dependency** として
 * 引き込むため、 各 baseline (本 module は 2.0.21) と CI matrix の bumped Kotlin を
 * 同一 testClasspath で両立できない (NoSuchMethodError 多発)。 そこで `compat-kXXX` 各
 * module の test source set を独立化することで、 各々が **自身の baseline Kotlin
 * (kotlin-compiler-embeddable-kXXX)** に固定された test classpath を持てる構造にしている。
 *
 * Sanity test 自体は kctfork を必要としない — 「ServiceLoader 経由で factory が
 * 発見できる」「minVersion が baseline と一致する」 ことだけを verify すれば、
 * compat module が plugin 出荷 artifact 内で正しく振る舞う前提を満たす。
 * 実際の compile + IR transform は `:integration-test:*` で担保している。
 */
class K202CompatContextSanityTest : StringSpec({

    "K202 CompatContext.Factory is discoverable via ServiceLoader" {
        val k202Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .firstOrNull { it::class.java.name.contains(".compat.k202.") }

        k202Factory.shouldNotBeNull()
    }

    "K202 factory advertises minVersion = 2.0.20 (SSOT)" {
        val k202Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k202.") }

        // compat-k200 (minVersion 2.0.0) より大きく compat-k210 (minVersion 2.1.0)
        // より小さい値である必要がある。 ここで drift すると plugin 出荷 artifact の
        // resolveFactory が誤って 2.0.0 / 2.1.0 cell を選択してしまう。
        // 2.0.10 では 2.0.21 native binary が依存する `BuildersKt` が未導入のため、
        // 本 module の dispatch 範囲は 2.0.20 / 2.0.21 patch のみ。
        k202Factory.minVersion shouldStartWith "2.0"
        k202Factory.minVersion shouldBe "2.0.20"
    }

    "K202 factory creates a CompatContextImpl instance" {
        val k202Factory = ServiceLoader.load(CompatContext.Factory::class.java)
            .first { it::class.java.name.contains(".compat.k202.") }

        val ctx = k202Factory.create()
        ctx.shouldBeInstanceOf<CompatContextImpl>()
    }
})
