package me.tbsten.capture.code.utils.ir

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.ir.declarations.IrFile

/**
 * IR 側の `(IrFile, offset)` から [CompilerMessageLocation] を作る domain 非依存 helper。
 *
 * FIR 側の相当物は
 * [me.tbsten.capture.code.utils.fir.compilerMessageLocationOf] (`utils/fir/`)。
 * こちらは `MessageCollector.report(...)` に位置情報を渡す IR phase の diagnostic
 * (`CC_CAPTUREDSOURCES_MARKER_NOT_REGISTERED` / `CC_CAPTUREDSOURCES_NO_MARKER_FOUND` 等)
 * が共通で使う。
 *
 * ## なぜ file を引数で受けるのか
 *
 * `IrCall` は expression であり parent pointer を持たないため、 call 単体から containing file
 * を辿ることはできない。 caller は [me.tbsten.capture.code.compat.CompatContext.transformCallsInFile]
 * で file 単位に transform を回し、 その file を渡す責務を持つ。
 *
 * ## 失敗時の挙動
 *
 * `fileEntry` の line / column 解決は offset が範囲外 (UNDEFINED_OFFSET = -1 等) のときに
 * 例外を投げ得るため `runCatching` で握り、 その場合は `-1` を入れて **location 自体は返す**
 * (= 少なくとも file path はユーザーに届く)。 [file] が `null` のときのみ `null` を返す。
 *
 * @param file 対象要素が属する IR file。 `null` なら location なし。
 * @param offset file 内 offset (通常は `IrElement.startOffset`)。
 * @return `file:line:column` を持つ location、 [file] が `null` なら `null`。
 */
public fun compilerMessageLocationOf(file: IrFile?, offset: Int): CompilerMessageLocation? =
    file?.let { irFile ->
        val path = irFile.fileEntry.name
        val line = runCatching { irFile.fileEntry.getLineNumber(offset) + 1 }.getOrDefault(-1)
        val column = runCatching { irFile.fileEntry.getColumnNumber(offset) + 1 }.getOrDefault(-1)
        CompilerMessageLocation.create(path, line, column, null)
    }
