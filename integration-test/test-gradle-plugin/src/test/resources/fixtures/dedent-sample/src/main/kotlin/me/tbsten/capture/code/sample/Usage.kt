package me.tbsten.capture.code.sample

/**
 * Use site は class 内のメンバ関数として宣言し、 元ソース上 4 spaces のインデントを
 * 持つように配置する。 dedent=true なら 4 spaces が除去された source string が、
 * dedent=false ならインデントが残ったままの source string が `Source.value` に
 * 埋め込まれる。
 */
internal class Outer {
    @IndentedMarker
    fun indentedMember(): String {
        return "hello"
    }
}
