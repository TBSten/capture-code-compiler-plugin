package me.tbsten.capture.code.feature.capturedSources.ir.normalize

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [findKDocExtendedStartOffset] の pure function unit test。
 *
 * bug-003 (直前のプレーン block comment / star-slash で終わる行コメントを KDoc 終端と誤検出し、
 * 手前の無関係な宣言まで capture される) の regression を固定する:
 *
 * - 正常系: 直前 KDoc (+ 空白行) は従来どおり拡張される
 * - 誤検出系: プレーン block comment / star-slash を含む行コメントでは拡張されない
 * - idempotency: 既に KDoc を含む offset を渡しても再拡張されない
 */
class KDocLookupTest : StringSpec({

    "直前の KDoc は開始位置まで拡張される" {
        val fullText = """
            /**
             * KDoc of target.
             */
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe fullText.indexOf("/**")
    }

    "KDoc と宣言の間に空白行があっても拡張される" {
        val fullText = """
            /** KDoc of target. */

            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe fullText.indexOf("/**")
    }

    "直前がプレーン block comment なら file 手前に KDoc があっても拡張されない" {
        val fullText = """
            /** KDoc of previous declaration. */
            internal fun previous() = "previous body"

            /* plain block comment, not kdoc */
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe startOffset
    }

    "star-slash で終わる行コメント (block comment 記号入り) では拡張されない" {
        val fullText = """
            /** kdoc far above */
            internal fun previous() = 1

            // see /* and */
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe startOffset
    }

    "star-slash で終わる行コメント (slash-star 無し) では拡張されない" {
        val fullText = """
            /** kdoc far above */
            internal fun previous() = 1

            // closes with */
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe startOffset
    }

    "行コメント内の KDoc もどき (slash-slash foo slash-star-star bar) では拡張されない" {
        val fullText = """
            // foo /** bar */
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe startOffset
    }

    "KDoc の無い file では offset がそのまま返る" {
        val fullText = """
            internal fun previous() = "previous body"

            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe startOffset
    }

    "URL (https スキーム) を含む 1 行 KDoc は行コメントと誤判定されず拡張される" {
        val fullText = """
            /** see https://example.com */
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe fullText.indexOf("/**")
    }

    "KDoc と宣言の間に行コメントが混在する場合は拡張されない (保守的 skip)" {
        val fullText = """
            /** KDoc of target. */
            // note between kdoc and declaration
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe startOffset
    }

    "既に KDoc を含む offset を渡しても再拡張されない (idempotent)" {
        val fullText = """
            internal fun previous() = "previous body"

            /** KDoc of target. */
            internal fun target() = "target body"
        """.trimIndent()
        val extendedOffset = fullText.indexOf("/**")
        findKDocExtendedStartOffset(fullText, extendedOffset) shouldBe extendedOffset
    }

    "空 block comment (slash-star-star-slash) は KDoc と見なされない" {
        val fullText = """
            /**/
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe startOffset
    }

    "直前がプレーン block comment のみ (手前に KDoc 無し) でも拡張されない" {
        val fullText = """
            /* not kdoc */
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe startOffset
    }

    "file 途中の宣言の直前 KDoc も拡張される" {
        val fullText = """
            internal fun previous() = 1
            /** KDoc of target. */
            internal fun target() = "target body"
        """.trimIndent()
        val startOffset = fullText.indexOf("internal fun target")
        findKDocExtendedStartOffset(fullText, startOffset) shouldBe fullText.indexOf("/**")
    }

    "offset が 0 以下または範囲外ならそのまま返る" {
        findKDocExtendedStartOffset("fun target() = 1", 0) shouldBe 0
        findKDocExtendedStartOffset("fun x() = 1", 999) shouldBe 999
    }
})
