package me.tbsten.capture.code

import com.google.auto.service.AutoService
import me.tbsten.capture.code.compat.CaptureCodeCompatHolder
import me.tbsten.capture.code.compat.CaptureCodeMessageCollectorHolder
import me.tbsten.capture.code.compat.CaptureCodePluginConfigHolder
import me.tbsten.capture.code.feature.capturedSources.CaptureCodeExpressionSiteRegistry
import me.tbsten.capture.code.feature.markerDefinition.CaptureCodeMarkerRegistry
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(CompilerPluginRegistrar::class)
public class CaptureCodeCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val supportsK2: Boolean = true

    // task-078: Kotlin 2.3.0 以降の `CompilerPluginRegistrar` には
    // `abstract val pluginId: String` が追加された。 main module は 2.0.0 baseline
    // (`kotlin-compiler-embeddable-k200`) で compile されるため、 super に
    // `pluginId` property は **存在せず** `override` キーワードは付けられない。
    //
    // ただし JVM の resolution は signature ベースなので、 2.0.0 baseline で
    // compile した bytecode に `public java.lang.String getPluginId()` という
    // method が単に **生えていれば** 、 2.3+ runtime での abstract method
    // resolution は満たされる (`AbstractMethodError` を回避できる)。
    //
    // そのため `override` を付けず、 同名 / 同 signature の通常 property として
    // 宣言する。 結果として 2.0.0 baseline では noop な追加 method、 2.3.0+
    // runtime では abstract 実装として作用する。
    //
    // ※ `final` 修飾子は付けない。 2.3+ で super の `abstract val pluginId`
    //   が見えた場合に compiler が automatic override 解決をできるようにするため
    //   ( `override` 無しでも JVM resolution が一致すれば runtime load は成立)。
    @Suppress("RedundantVisibilityModifier")
    public open val pluginId: String = CAPTURE_CODE_PLUGIN_ID

    // task-078: FIR / IR 拡張の登録は compat layer に委譲する。
    //
    // 旧実装:
    // ```
    // FirExtensionRegistrarAdapter.registerExtension(CaptureCodeFirExtensionRegistrar())
    // IrGenerationExtension.registerExtension(CaptureCodeIrExtension(config))
    // ```
    //
    // Kotlin 2.3.0 で `FirExtensionRegistrarAdapter.Companion` と
    // `IrGenerationExtension.Companion` の親 class が
    // `ProjectExtensionDescriptor` → `ExtensionPointDescriptor` に置き換わり、
    // 対応する `ExtensionStorage.registerExtension(descriptor, T)` の引数型も同様に
    // drift した (D10)。 main module は 2.0.0 baseline で compile されるため、
    // 上記 2 行はそのままだと 2.3+ runtime で `ClassCastException` / `NoSuchMethodError`
    // を引き起こす。 compat layer (compat-kXXX) は consumer Kotlin に合致する
    // 1 module だけが ServiceLoader 経由で選ばれ、 その module は対応する
    // `kotlin-compiler-embeddable-kXXX` でビルドされているため、 ここを通せば
    // 各 Kotlin runtime で正しい signature が解決される。
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // bug-007: registry を compile の入口で必ず reset する。
        //
        // CaptureCodeMarkerRegistry / CaptureCodeExpressionSiteRegistry は process-global object
        // で、 従来は `CaptureCodeIrExtension.generate` の finally でのみ reset していた。 しかし
        // **FIR error で IR phase に到達しなかった compile は finally を通らず**、 marker FqN /
        // expression site の残骸が registry に残る。 同一 ClassLoader で次の compile が走ると
        // (kctfork の連続 compile 等)、 同一 file path の残骸 site による二重 capture や
        // duplicate marker FQN warning の false positive を引き起こす。
        //
        // `registerExtensions` は 1 compile につき 1 回、 FIR phase 開始前に呼ばれる compile の
        // 入口なので、 ここで reset すれば前回 compile の残骸は必ず消える。
        //
        // 既知の制約: registry は依然 process-global のため、 同一 JVM (= 同一 ClassLoader) 内での
        // **並行** compile 同士の汚染 (相手の registry を消してしまう / 相手の site を読んでしまう)
        // はこの reset では解消しない。 実 Gradle build では Kotlin daemon が compile ごとに plugin
        // ClassLoader を分離するため顕在化しないが、 compiler を embed する環境で並行 compile を
        // 行う場合は注意 (registry の compile 単位化は将来 task)。
        CaptureCodeMarkerRegistry.reset()
        CaptureCodeExpressionSiteRegistry.reset()

        val config = configuration.captureCodePluginConfig
        // task-123: FIR checkers (specifically the WarnIfOverrideNoEffect logic
        // in Logic F) need read access to the plugin config but there is no FIR-
        // session-bound channel for it on the 2.0 baseline. Publish the freshly
        // resolved config to a process-scoped holder so the FIR side can pick it
        // up from `CaptureCodePluginConfigHolder.get()`.
        CaptureCodePluginConfigHolder.set(config)
        // task-120-B Phase 7: publish the IR-phase MessageCollector resolved
        // from the active CompilerConfiguration so `WarnIfNoMarkerFound` (driven
        // by `RewriteCapturedSourcesCall` in IR phase) can report
        // `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` warnings without going through
        // a compat SPI. See the holder KDoc for the K2.4-RC drift rationale.
        CaptureCodeMessageCollectorHolder.setFrom(configuration)
        CaptureCodeCompatHolder.context.registerExtensions(
            extensionStorage = this,
            configuration = configuration,
            firRegistrar = CaptureCodeFirExtensionRegistrar(),
            irExtension = CaptureCodeIrExtension(config),
        )
    }
}
