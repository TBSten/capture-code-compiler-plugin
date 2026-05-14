package me.tbsten.capture.code.sample.jvm.cookbook

import me.tbsten.capture.code.sample.jvm.Route

// ============================================================================
// user-defined parameter を持つ marker のサンプル
//
// REST endpoint カタログを作るユースケース。 `method` と `path` は呼び出し側で
// 指定し、 `source` は plugin が埋める。 こうした user parameter は filler と
// 共存して残るので、 「メタデータ + 実装ソース」 のペアとして集めて利用できる。
// ============================================================================

@Route(method = "GET", path = "/users")
internal fun listUsers(): String = "[user1, user2]"

@Route(method = "POST", path = "/users")
internal fun createUser(): String = "created"

@Route(method = "DELETE", path = "/users/{id}")
internal fun deleteUser(): String = "deleted"
