package me.tbsten.capture.code

import me.tbsten.capture.code.compat.CaptureCodeCompatHolder
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * Capture Code compiler plugin の FIR 拡張点エントリ。
 *
 * task-072 で **FIR checker class を compat layer (compat-k200 / compat-k210) に全面移管** した。
 *
 * ## 移管の動機 (runtime drift D9)
 *
 * Kotlin 2.0.x → 2.2.x で `FirDeclarationChecker.check(...)` / `FirExpressionChecker.check(...)` の
 * 抽象メソッドの **引数順** が `check(declaration, context, reporter)` から
 * `check(context, reporter, declaration)` に変わった。 2.0.0 baseline で compile した checker class の
 * bytecode は 2.2.x runtime で abstract method を実装していない判定となり、 **AbstractMethodError**
 * を引き起こす。
 *
 * task-071 で main module を `kotlin-compiler-embeddable-k200` (2.0.0 固定) で compile することにより
 * `compileKotlin` レベルの drift は解消したが、 上記 runtime drift は残存していた。 本 ticket で
 * checker class を **各 compat-kXXX module の baseline で compile される箇所** に移すことで、
 * main module が runtime drift から完全に隔離される。
 *
 * ## 現在のエントリ構成
 *
 * - **Compat-provided checkers**: `CompatContext.firAdditionalCheckersExtensions()` が
 *   選択された compat module (= 現在の Kotlin compiler version に対応する compat-kXXX) から
 *   FIR additional checker extension factory のリストを返す。 これを `+::` 構文で登録する。
 *
 *   提供 logic:
 *   - Logic A: `@CaptureCode` メタ付き annotation class の発見 / registry 登録
 *   - Logic F: marker annotation の制約違反診断 (visibility / @Retention / @Target / parameter 型 etc.)
 *   - Logic G: `capturedSources<T>()` の型引数 `T` 検査
 *   - Logic B-fir: `@Marker (expr)` 式 annotation の site collection
 *
 * 詳細は `compiler-plugin-design.md` §5 / §6 を参照。
 */
public class CaptureCodeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // compat layer から FIR checker extension factory のリストを取得し、 同一 session で
        // 構築されるように `+(FirSession) -> FirAdditionalCheckersExtension` 形で登録する。
        //
        // ※ ServiceLoader 経由で resolveFactory() が走るのは、 本 plugin が JVM 上で初めて
        //   インスタンス化される時 (= compile session 開始時)。 一度キャッシュされた CompatContext
        //   は process 寿命の間有効。
        val compat = CaptureCodeCompatHolder.context
        for (factory in compat.firAdditionalCheckersExtensions()) {
            +factory
        }
    }
}
