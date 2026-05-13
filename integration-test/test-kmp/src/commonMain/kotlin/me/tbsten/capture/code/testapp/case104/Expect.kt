package me.tbsten.capture.code.testapp.case104

/**
 * ケース #104: expect 側 (annotation 無し)。
 *
 * `@Platform_KmpCase104` は付けない。これにより compiler plugin は expect 宣言を
 * キャプチャ対象に含めない (Logic B の挙動)。各 target compilation では対応する
 * actual のみが `capturedSources<Platform_KmpCase104>()` の結果に出る。
 *
 * marker は internal なので、expect / actual も `internal` で揃える (case102 / case103
 * と同様の方針 — 同一 module 内に閉じて公開 API として露出させない)。
 */
internal expect fun kmpCase104_platformName(): String
