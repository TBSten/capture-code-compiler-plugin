package me.tbsten.capture.code

import com.google.auto.service.AutoService
import me.tbsten.capture.code.compat.CaptureCodeCompatHolder
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
        val config = configuration.captureCodePluginConfig
        CaptureCodeCompatHolder.context.registerExtensions(
            extensionStorage = this,
            configuration = configuration,
            config = config,
            firRegistrar = CaptureCodeFirExtensionRegistrar(),
            irExtension = CaptureCodeIrExtension(config),
        )
    }
}
