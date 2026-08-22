package me.tbsten.capture.code.feature.capturedSources

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import me.tbsten.capture.code.feature.capturedSources.ir.collectDeclarationSite.stripEnclosingBlock

/**
 * [stripEnclosingBlock] の契約を pure function として直接 pin する。
 *
 * task-149 の post-mortem: 「緩和的な matcher / 抽出 helper は positive test では絶対に落ちない」。
 * そのため **マッチしてはいけない入力 (= null を返すべき入力)** を必ず併記する。
 */
class StripEnclosingBlockTest : FunSpec({

    test("returns the body between the first '{' and the last '}'") {
        stripEnclosingBlock("f(M::class) {\n  a()\n}") shouldBe "\n  a()\n"
    }

    test("returns an empty string for an empty block") {
        stripEnclosingBlock("f(M::class) {}") shouldBe ""
    }

    test("keeps nested braces intact") {
        stripEnclosingBlock("f(M::class) { if (x) { a() } else { b() } }") shouldBe
            " if (x) { a() } else { b() } "
    }

    test("returns null when there is no opening brace") {
        stripEnclosingBlock("f(M::class)") shouldBe null
    }

    test("returns null when there is no closing brace") {
        stripEnclosingBlock("f(M::class) { a()") shouldBe null
    }

    test("returns null when the closing brace precedes the opening brace") {
        stripEnclosingBlock("}{") shouldBe null
    }

    test("returns null for an empty input") {
        stripEnclosingBlock("") shouldBe null
    }
})
