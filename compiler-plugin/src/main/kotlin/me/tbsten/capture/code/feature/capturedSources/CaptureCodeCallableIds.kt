package me.tbsten.capture.code.feature.capturedSources

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * `capturedSources<T>()` / `capturedSource<T>()` 関数 の identity を表す SSOT。
 *
 * FIR checker (Logic G: `ValidateCapturedSourcesCall`) と IR transformer (Logic H:
 * `RewriteCapturedSourcesCall` / `RewriteCapturedSourceCall`) の両方が **同じ 1 つの定義** を
 * 参照することで、 パッケージ名・関数名のミスマッチによる「checker は走るが書き換えは走らない /
 * 逆」のバグを防ぐ。
 *
 * 単数版 [capturedSource] (`me.tbsten.capture.code.capturedSource`) は単一サイト強制を行う API
 * で、 複数版 [capturedSources] と命名上の対称性 (s の有無) を保持している。
 */
public object CaptureCodeCallableIds {

    /** `capturedSources<T>()` / `capturedSource<T>()` の package (= `me.tbsten.capture.code`)。 */
    public val packageFqName: FqName = FqName("me.tbsten.capture.code")

    public val capturedSourcesName: Name = Name.identifier("capturedSources")

    public val capturedSources: CallableId = CallableId(
        packageName = packageFqName,
        callableName = capturedSourcesName,
    )

    public val capturedSourceName: Name = Name.identifier("capturedSource")

    public val capturedSource: CallableId = CallableId(
        packageName = packageFqName,
        callableName = capturedSourceName,
    )

    public val runWithCaptureCodeName: Name = Name.identifier("runWithCaptureCode")

    /**
     * `runWithCaptureCode(Marker::class) { ... }` (= block 起源 site) の identity。
     *
     * [capturedSources] / [capturedSource] と違い IR phase での **書き換えは不要** で、
     * FIR phase の `CollectRunWithCaptureCodeSite` が site を登録するためだけに使う
     * (runtime 実装は `block()` を呼ぶだけの実体を持つ)。
     */
    public val runWithCaptureCode: CallableId = CallableId(
        packageName = packageFqName,
        callableName = runWithCaptureCodeName,
    )
}
