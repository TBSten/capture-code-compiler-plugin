package me.tbsten.capture.code.sample.jvm

import me.tbsten.capture.code.CaptureCode
import me.tbsten.capture.code.CaptureKind
import me.tbsten.capture.code.Source
import me.tbsten.capture.code.SourceLocation

// ============================================================================
// このファイルは Capture Code の **marker annotation** をまとめて宣言するための
// cookbook 例です。 marker は `@CaptureCode` をメタアノテーションとして付けた
// annotation class であり、 「キャプチャしたソースをどこに埋めるか」 を property
// (filler) で指定します。
//
// 「filler」 とは Capture Code が compile 時に値を埋め込む property の総称で、
// 用意されているのは現在以下の 3 種:
//   - Source           : source code 文字列
//   - SourceLocation   : packageName / filePath / startLine / endLine
//   - CaptureKind      : EXPRESSION / PROPERTY / CLASS / OBJECT / FUNCTION / TYPEALIAS / FILE
//
// 各 marker は **必要な filler だけ宣言** すれば良く、 user-defined parameter
// (string などの好きな値) も一緒に持つことができます。
// ============================================================================

/**
 * [Snippet]: 最小構成の marker。 Source filler のみを宣言。
 *
 * これだけで marker を付けた宣言の本文 (source string) が plugin によって
 * 埋め込まれる。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Snippet(
    val source: Source = Source(),
)

/**
 * [DetailedSnippet]: 3 種の filler を全て使う marker。
 *
 * Source + SourceLocation + CaptureKind を同時に取得することで、
 * 「どこに書かれた何の宣言だったのか」 まで含めて記録できる。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class DetailedSnippet(
    val source: Source = Source(),
    val location: SourceLocation = SourceLocation(),
    val kind: CaptureKind = CaptureKind(),
)

/**
 * [Route]: user-defined parameter を持つ marker。
 *
 * REST endpoint 一覧の自動生成等で典型的に使うパターン。 `method` と `path` は
 * ユーザが渡し、 `source` は plugin が埋める。
 */
@CaptureCode
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Route(
    val method: String,
    val path: String,
    val source: Source = Source(),
)

/**
 * [TypeDoc]: typealias / class / object など 「宣言系すべて」 を見出す marker。
 *
 * ALL_DECL ターゲットを示すため AnnotationTarget を広めに宣言。
 */
@CaptureCode
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.SOURCE)
internal annotation class TypeDoc(
    val source: Source = Source(),
    val kind: CaptureKind = CaptureKind(),
)

/**
 * [FileTopic]: file-level annotation 用の marker。
 *
 * `@file:FileTopic("...")` のように file annotation で使い、 ファイル全体の
 * source code を取得する。
 */
@CaptureCode
@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
internal annotation class FileTopic(
    val topic: String,
    val source: Source = Source(),
)
