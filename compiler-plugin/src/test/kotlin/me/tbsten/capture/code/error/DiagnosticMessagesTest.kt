package me.tbsten.capture.code.error

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * 診断メッセージの bilingual SSOT に対する snapshot test。
 *
 * [CaptureCodeDiagnosticMessages] の各 [BilingualMessage] について、英語 / 日本語 / 併記
 * モードで rendering 結果を固定化する。将来 phrasing を変更する際に「うっかり test の
 * assertion が変わって既存 test が壊れる」regression を検出する。
 *
 * **保証する性質**:
 *
 * 1. 既存 checker test (`MarkerAnnotationCheckerTest` / `CapturedSourcesCallCheckerTest`)
 *    が `shouldContain` で期待している phrase が、BILINGUAL 表示 (default) でも EN 単独
 *    表示でも引き続き含まれている (退行ゼロ)
 * 2. 日本語表示時に「修正方法:」 prefix の hint 行が含まれる (`Suggested fix:` ペア)
 * 3. BILINGUAL 表示時は英語 → `日本語:` の prefix → 日本語の順で連結される
 * 4. `CAPTURECODE_LOCALE` 環境変数の解釈が大文字小文字を問わない
 *
 * 文面の整形ルール (英文・敬語・FQN) は [CaptureCodeDiagnosticMessages] の KDoc を参照。
 */
class DiagnosticMessagesTest : FunSpec({

    // ------------------------------------------------------------------
    // Locale resolution
    // ------------------------------------------------------------------

    context("CaptureCodeMessageLocale.fromEnv") {
        test("returns EN for 'en' or 'english' (case insensitive)") {
            CaptureCodeMessageLocale.fromEnv { "en" } shouldBe CaptureCodeMessageLocale.EN
            CaptureCodeMessageLocale.fromEnv { "EN" } shouldBe CaptureCodeMessageLocale.EN
            CaptureCodeMessageLocale.fromEnv { "English" } shouldBe CaptureCodeMessageLocale.EN
        }
        test("returns JA for 'ja' / 'japanese' / 'jp' (case insensitive)") {
            CaptureCodeMessageLocale.fromEnv { "ja" } shouldBe CaptureCodeMessageLocale.JA
            CaptureCodeMessageLocale.fromEnv { "JA" } shouldBe CaptureCodeMessageLocale.JA
            CaptureCodeMessageLocale.fromEnv { "Japanese" } shouldBe CaptureCodeMessageLocale.JA
            CaptureCodeMessageLocale.fromEnv { "jp" } shouldBe CaptureCodeMessageLocale.JA
        }
        test("returns BILINGUAL for unset / unknown values") {
            CaptureCodeMessageLocale.fromEnv { null } shouldBe CaptureCodeMessageLocale.BILINGUAL
            CaptureCodeMessageLocale.fromEnv { "" } shouldBe CaptureCodeMessageLocale.BILINGUAL
            CaptureCodeMessageLocale.fromEnv { "zh" } shouldBe CaptureCodeMessageLocale.BILINGUAL
            CaptureCodeMessageLocale.fromEnv { "fr" } shouldBe CaptureCodeMessageLocale.BILINGUAL
        }
    }

    // ------------------------------------------------------------------
    // BilingualMessage.render
    // ------------------------------------------------------------------

    context("BilingualMessage.render") {
        val sample = BilingualMessage(en = "Hello.", ja = "こんにちは。")

        test("EN mode returns English only") {
            sample.render(CaptureCodeMessageLocale.EN) shouldBe "Hello."
        }
        test("JA mode returns Japanese only") {
            sample.render(CaptureCodeMessageLocale.JA) shouldBe "こんにちは。"
        }
        test("BILINGUAL mode joins English -> '日本語:' -> Japanese") {
            sample.render(CaptureCodeMessageLocale.BILINGUAL) shouldBe "Hello.\n日本語: こんにちは。"
        }
    }

    // ------------------------------------------------------------------
    // Snapshot: backward-compatible phrases (英語) — 既存 checker test の assertion
    // を割らないことを確認
    // ------------------------------------------------------------------

    context("English phrases — backward compatibility with existing checker tests") {
        val msg = CaptureCodeDiagnosticMessages

        test("MARKER_VISIBILITY_VIOLATION contains \"must be 'internal' or 'private'\"") {
            msg.MARKER_VISIBILITY_VIOLATION.en shouldContain "must be 'internal' or 'private'"
        }
        test("MARKER_RETENTION_VIOLATION mentions @Retention(AnnotationRetention.SOURCE)") {
            msg.MARKER_RETENTION_VIOLATION.en shouldContain "@Retention(AnnotationRetention.SOURCE)"
        }
        test("MARKER_TARGET_EMPTY mentions @Target site") {
            msg.MARKER_TARGET_EMPTY.en shouldContain "@Target site"
        }
        test("MARKER_PARAMETER_TYPE_INVALID mentions 'has an unsupported type'") {
            msg.MARKER_PARAMETER_TYPE_INVALID.en shouldContain "has an unsupported type"
        }
        test("MARKER_FILLER_REQUIRES_DEFAULT mentions 'must have a default value'") {
            msg.MARKER_FILLER_REQUIRES_DEFAULT.en shouldContain "must have a default value"
        }
        test("MARKER_IS_EXPECT mentions \"cannot be declared as 'expect'\"") {
            msg.MARKER_IS_EXPECT.en shouldContain "cannot be declared as 'expect'"
        }
        test("CAPTUREDSOURCES_T_NOT_CAPTURE_CODE mentions 'must be annotated with @CaptureCode'") {
            msg.CAPTUREDSOURCES_T_NOT_CAPTURE_CODE.en shouldContain "must be annotated with @CaptureCode"
        }
    }

    // ------------------------------------------------------------------
    // Snapshot: 'Suggested fix:' hint があらゆる診断にある
    // ------------------------------------------------------------------

    context("Suggested fix hints (English)") {
        val msg = CaptureCodeDiagnosticMessages
        val all = listOf(
            "MARKER_VISIBILITY_VIOLATION" to msg.MARKER_VISIBILITY_VIOLATION,
            "MARKER_RETENTION_VIOLATION" to msg.MARKER_RETENTION_VIOLATION,
            "MARKER_TARGET_EMPTY" to msg.MARKER_TARGET_EMPTY,
            "MARKER_PARAMETER_TYPE_INVALID" to msg.MARKER_PARAMETER_TYPE_INVALID,
            "MARKER_FILLER_REQUIRES_DEFAULT" to msg.MARKER_FILLER_REQUIRES_DEFAULT,
            "MARKER_IS_EXPECT" to msg.MARKER_IS_EXPECT,
            "CAPTUREDSOURCES_T_NOT_CAPTURE_CODE" to msg.CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
        )

        all.forEach { (name, m) ->
            test("$name English message contains 'Suggested fix:'") {
                m.en shouldContain "Suggested fix:"
            }
            test("$name Japanese message contains '修正方法:'") {
                m.ja shouldContain "修正方法:"
            }
        }
    }

    // ------------------------------------------------------------------
    // Snapshot: BILINGUAL rendering — 英語と日本語の両方を含み、'日本語:' prefix が入る
    // ------------------------------------------------------------------

    context("BILINGUAL rendering for each diagnostic") {
        val msg = CaptureCodeDiagnosticMessages
        val cases = mapOf(
            "MARKER_VISIBILITY_VIOLATION" to msg.MARKER_VISIBILITY_VIOLATION,
            "MARKER_RETENTION_VIOLATION" to msg.MARKER_RETENTION_VIOLATION,
            "MARKER_TARGET_EMPTY" to msg.MARKER_TARGET_EMPTY,
            "MARKER_PARAMETER_TYPE_INVALID" to msg.MARKER_PARAMETER_TYPE_INVALID,
            "MARKER_FILLER_REQUIRES_DEFAULT" to msg.MARKER_FILLER_REQUIRES_DEFAULT,
            "MARKER_IS_EXPECT" to msg.MARKER_IS_EXPECT,
            "CAPTUREDSOURCES_T_NOT_CAPTURE_CODE" to msg.CAPTUREDSOURCES_T_NOT_CAPTURE_CODE,
        )

        cases.forEach { (name, m) ->
            val rendered = m.render(CaptureCodeMessageLocale.BILINGUAL)
            test("$name BILINGUAL contains English + Japanese with separator") {
                rendered shouldContain m.en
                rendered shouldContain m.ja
                rendered shouldContain "\n日本語: "
            }
            test("$name EN mode does NOT contain '日本語:' prefix") {
                m.render(CaptureCodeMessageLocale.EN) shouldNotContain "日本語:"
            }
            test("$name JA mode does NOT contain 'Suggested fix:' (English-only phrase)") {
                m.render(CaptureCodeMessageLocale.JA) shouldNotContain "Suggested fix:"
            }
        }
    }

    // ------------------------------------------------------------------
    // Snapshot: 完全文面 (regression detector)
    //
    // 文面を意図せず書き換えた場合に検出する。`shouldBe` で完全一致比較。
    // 文面を変更する場合は本 snapshot も同時に更新する。
    // ------------------------------------------------------------------

    context("Full English snapshot (regression detector)") {
        val msg = CaptureCodeDiagnosticMessages

        test("MARKER_VISIBILITY_VIOLATION English snapshot") {
            msg.MARKER_VISIBILITY_VIOLATION.en shouldBe (
                "@CaptureCode marker annotation must be 'internal' or 'private'. " +
                    "Cross-module capture is not supported in v1.\n" +
                    "Suggested fix: change the visibility modifier to 'internal' or 'private'."
                )
        }
        test("MARKER_VISIBILITY_VIOLATION Japanese snapshot") {
            msg.MARKER_VISIBILITY_VIOLATION.ja shouldBe (
                "@CaptureCode marker annotation は 'internal' または 'private' で宣言する必要があります。" +
                    "v1 ではモジュール跨ぎのキャプチャはサポートしていません。\n" +
                    "修正方法: visibility modifier を 'internal' または 'private' に変更してください。"
                )
        }
        test("CAPTUREDSOURCES_T_NOT_CAPTURE_CODE English snapshot uses {0} placeholder") {
            msg.CAPTUREDSOURCES_T_NOT_CAPTURE_CODE.en shouldContain "{0}"
        }
        test("MARKER_PARAMETER_TYPE_INVALID uses MessageFormat ''{0}'' (doubled-quote) placeholder") {
            // KtDiagnosticRenderers.TO_STRING 経由で MessageFormat に渡される。''→' に展開。
            msg.MARKER_PARAMETER_TYPE_INVALID.en shouldContain "''{0}''"
        }
    }
})
