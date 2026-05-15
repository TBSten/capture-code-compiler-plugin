# Publishing to Maven Central

Capture Code compiler plugin を Maven Central に publish するための手順をまとめる。
local 開発時の dry-run / 実 release / CI からの自動化を網羅する。

> 実 release は GitHub Actions workflow から行うことを推奨。手動 release
> は緊急 hotfix 等の例外運用に限定する。

## Publish 対象 module

| Module                     | groupId                       | artifactId                              | 用途                                                          |
|----------------------------|-------------------------------|-----------------------------------------|---------------------------------------------------------------|
| `:annotation`              | `me.tbsten.capture.code`      | `annotation` + platform-specific suffix | ユーザが `implementation(...)` する KMP runtime API                |
| `:compiler-plugin`         | `me.tbsten.capture.code`      | `compiler-plugin`                       | shadow JAR (compat-k200 / compat-k210 同梱)。Gradle plugin が classpath に乗せる |
| `:gradle-plugin`           | `me.tbsten.capture.code`      | `gradle-plugin`                         | `plugins { id("me.tbsten.capture.code") }` のラッパー              |
| `:gradle-plugin` (marker)  | `me.tbsten.capture.code`      | `me.tbsten.capture.code.gradle.plugin`  | plugin marker (自動生成、Gradle plugin DSL が version resolve に使う) |

publish しない (internal) module:

- `:compiler-plugin:compat` / `:compat-k200` / `:compat-k210` — shadow JAR に同梱されるため independent publish 不要
- `:integration-test:*` / `:samples:*` / `:buildSrc` — テスト / sample / build infra

## 設計

- バージョン SSOT: ルート `gradle.properties` の `VERSION_NAME` (例: `0.1.2`)。 詳細は [docs/versioning.md](versioning.md) 参照。
- groupId SSOT: ルート `gradle.properties` の `GROUP` (`me.tbsten.capture.code`)。
- ルート `build.gradle.kts` で `allprojects { group = GROUP; version = VERSION_NAME }` として全 subproject に伝搬。
- 共通 POM (license / developer / scm) と signing 条件は `buildSrc/src/main/kotlin/buildsrc/convention/publish.gradle.kts` で集約。
- 各 publish 対象 module は `id("buildsrc.convention.publish")` を適用し、`mavenPublishing { pom { name / description ... } }` のみ override。
- `:compiler-plugin` の main artifact は **shadow JAR** (上書きずみの `runtimeElements` / `apiElements` outgoing artifacts を vanniktech plugin がそのまま使用)。
- shadowed (bundled) module は POM の `<dependencies>` に出ない (`bundled` configuration が `runtimeElements` に紐づかないため)。 `:compat` も同様に `compileOnly + bundled` の組み合わせで POM 流出を防いでいる。

## 事前準備 (初回のみ)

### 1. Sonatype Central Portal アカウント作成と namespace verification

1. https://central.sonatype.com/ にサインアップ。
2. namespace `me.tbsten.capture.code` を登録 (verification を済ませる)。
   - `tbsten.me` ドメインがあれば DNS TXT verification、無ければ GitHub username `tbsten` 経由の verification (Sonatype が自動 fallback) を利用。
3. 「Generate User Token」から `username` / `password` を発行。

### 2. GPG key 生成 (artifact 署名用)

```shell
# key 生成 (RSA 4096)
gpg --full-generate-key

# 既存 key の確認
gpg --list-secret-keys --keyid-format=long

# 公開 key を keyserver に upload (Maven Central が verify する)
gpg --keyserver hkps://keys.openpgp.org --send-keys <KEY_ID>
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <KEY_ID>

# private key を ASCII-armored で export (CI 用)
gpg --armor --export-secret-keys <KEY_ID> > private.key
```

> `private.key` は **絶対に commit しない**。CI には GitHub Secrets として登録する。

### 3. local credentials の配置

`~/.gradle/gradle.properties` (グローバル) に以下を追加:

```properties
# Sonatype Central Portal
mavenCentralUsername=<token-username>
mavenCentralPassword=<token-password>

# Signing (in-memory)
signingInMemoryKey=<ASCII-armored private key の中身、改行は \n に置換 or heredoc>
signingInMemoryKeyId=<KEY_ID の下 8 桁、任意>
signingInMemoryKeyPassword=<passphrase、任意>
```

> 本リポジトリの `gradle.properties` には credentials を**書かない**。`~/.gradle/gradle.properties` または環境変数 `ORG_GRADLE_PROJECT_*` を使う。

## Local dry-run

credentials なしでも `publishToMavenLocal` は通る (signing は条件分岐で skip)。

```shell
# 全 publishable module を ~/.m2/repository/ に publish
./gradlew publishToMavenLocal

# 個別
./gradlew :annotation:publishToMavenLocal
./gradlew :compiler-plugin:publishToMavenLocal
./gradlew :gradle-plugin:publishToMavenLocal

# 出力確認
ls ~/.m2/repository/me/tbsten/capture/code/
# annotation/ annotation-jvm/ annotation-js/ ... compiler-plugin/ gradle-plugin/
# me.tbsten.capture.code.gradle.plugin/  (plugin marker)
```

> KMP の native target (iOS / macOS) の publish には Xcode フル版が必要。CommandLineTools のみの環境では `iosArm64` 等が SDK lookup error で失敗する。CI Mac runner では問題ない。

## バージョン管理方針 (no SNAPSHOT)

本プロジェクトは **`-SNAPSHOT` 接尾辞を使わない**。 main branch の
`gradle.properties:VERSION_NAME` は **常に「次にリリースする予定の version」**
を保持する (詳細は [docs/versioning.md](versioning.md) の "Between releases"
section)。

- 通常開発中: `VERSION_NAME = 次の MINOR 予定` (例: `0.2.0`)
- 直近 release に対して unexpected bug fix が必要なとき: `VERSION_NAME = PATCH` (例: `0.1.2`)
- release は **tag push** が trigger。 ローカルでの `publishAndReleaseToMavenCentral`
  は credentials 持たない通常開発では走らない設計

## 本番 release

1. `gradle.properties` の `VERSION_NAME` が release したい version になっていることを確認 (例: `0.1.2`)。
2. tag を打つ (例: `v0.1.2`)、 main に push:

   ```shell
   git tag v0.1.2
   git push origin v0.1.2
   ```

3. GitHub Actions の **Release** workflow が自動起動、 smoke test → Maven Central publish → GitHub Release 作成。
4. https://central.sonatype.com/ で release status を確認。
5. release 後、 `VERSION_NAME` を **次の planned version** に bump して commit:
   - 次が機能追加サイクル → `0.2.0`
   - 次が更なる patch (= さらにバグ修正用) → `0.1.3`

`--no-configuration-cache` は vanniktech plugin の制約 (HTTP request task が CC 非対応) による回避策。

## CI からの自動 release

`.github/workflows/release.yml` に **tag push を契機とする自動 release workflow** を整備してある。 `vX.Y.Z` 形式の tag を push すると以下が自動実行される:

1. checkout (tag 履歴付き)
2. JDK 21 + Gradle wrapper setup
3. release 前 smoke test (`:compiler-plugin:test` / `:gradle-plugin:test` / `:integration-test:test-*` の JVM 系)
4. Maven Central publish (`:annotation` / `:compiler-plugin` / `:gradle-plugin` × `publishAndReleaseToMavenCentral`)
5. GitHub Release 作成 (`generate_release_notes: true` で commit log から自動生成)

### GitHub Secrets 設定

GitHub repository の **Settings → Secrets and variables → Actions → New repository secret** から以下を設定する。 secret 名は workflow が直接 `ORG_GRADLE_PROJECT_*` を Gradle に env 経由で渡す設計 (= mapping を介さず SSOT 一致)。

| Secret 名                                              | 値                                          | 必須 |
|--------------------------------------------------------|---------------------------------------------|------|
| `ORG_GRADLE_PROJECT_mavenCentralUsername`              | Sonatype Central Portal User Token username | 必須 |
| `ORG_GRADLE_PROJECT_mavenCentralPassword`              | Sonatype Central Portal User Token password | 必須 |
| `ORG_GRADLE_PROJECT_signingInMemoryKey`                | ASCII-armored GPG private key 全体          | 必須 |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyId`              | GPG key id 下 8 桁                          | 任意 |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`        | GPG passphrase                              | 任意 |

> GPG private key は改行を含むため、 `gpg --armor --export-secret-keys <KEY_ID>` の **出力全体をそのまま貼り付ける** (`-----BEGIN PGP PRIVATE KEY BLOCK-----` から `-----END PGP PRIVATE KEY BLOCK-----` まで)。 GitHub Secrets は multi-line 値をサポートする。

### Release 手順

実 release は以下の手順で実施する:

1. **`gradle.properties` の `VERSION_NAME` が release したい version になっていることを確認**
   (例: `0.1.2`)。 release 中の bug fix を出す場合は patch version へ書き換える
   (`0.2.0` → `0.1.2` 等)。
2. **release commit を main にマージ** (PR 経由推奨)
3. **tag を push**:

   ```shell
   git tag v0.1.2
   git push origin v0.1.2
   ```

4. GitHub Actions の **Release** workflow が起動し、 上記 1〜5 のステップを自動実行する。
5. **完了後**: `gradle.properties` の `VERSION_NAME` を **次の planned version** に bump して commit
   (次が機能追加サイクル → `0.2.0`、 さらに patch 予定 → `0.1.3`)。

### Dry-run (動作確認)

実 release をせず workflow の挙動だけ確認したい場合は **Actions タブ → Release → Run workflow** から手動起動できる。 `dryRun: true` (default) の状態では smoke test のみ実行され、 publish / GitHub Release 作成は skip される。

### Tag 命名規約

- 形式: `vMAJOR.MINOR.PATCH` (例: `v0.1.0`, `v1.0.0`, `v0.1.0-rc1`)
- `v` prefix を含む tag のみが release workflow を trigger する (`on.push.tags: ['v*.*.*']`)
- `-` を含む tag (例: `v0.1.0-rc1`, `v1.0.0-beta`) は GitHub Release で **prerelease flag が自動で立つ**

### Trouble shooting (release workflow)

- **`Could not resolve all files for configuration ':classpath'`**: gradle/actions/setup-gradle@v3 の cache 不整合。 Actions UI の Re-run all jobs で解消することが多い
- **publish step が `401 Unauthorized`**: `ORG_GRADLE_PROJECT_mavenCentralUsername` / `ORG_GRADLE_PROJECT_mavenCentralPassword` が User Token (Bearer) と一致していない。 Sonatype Central Portal で再発行
- **signing step で `BAD_PASSPHRASE`**: `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` が key 生成時の passphrase と不一致
- **GitHub Release が作成されない**: `permissions: contents: write` が job に付与されているか確認 (workflow の `permissions:` block)

## Gradle Plugin Portal

`gradle-plugin` を [Gradle Plugin Portal](https://plugins.gradle.org/) にも publish する場合は `com.gradle.plugin-publish` plugin を追加する (現状は未対応)。 Maven Central published plugin marker でも `pluginManagement { repositories { mavenCentral() } }` 経由なら `plugins {}` syntax は動くので、 Plugin Portal は optional。

## トラブルシューティング

- **`The request for this plugin could not be satisfied because the plugin is already on the classpath with an unknown version`**:
  ルート `build.gradle.kts` の `plugins { alias(libs.plugins.maven.publish).apply(false) }` を**書かない**。 buildSrc convention plugin が classpath に乗せるため重複する。
- **`The value for extension 'mavenPublishing' property 'groupId$plugin' is final`**:
  convention plugin で `publishToMavenCentral()` が呼ばれた後に module 側で `coordinates(...)` を呼ぶと finalize 済 property への mutation で失敗する。 module 側では `coordinates(...)` を呼ばず、 `project.group` / `project.name` / `project.version` の自動値に任せる (SSOT)。
- **POM に internal module (compat) 依存が漏れる**:
  `:compiler-plugin` の `dependencies` で `implementation(project(":compiler-plugin:compat"))` にすると runtime variant 経由で POM の `<dependencies>` に出る。 `compileOnly(...)` + `bundled(...)` の組合せに切り替えること。
- **iOS target で `xcrun ... non-zero exit code: 72`**:
  Xcode フル版 (Command Line Tools ではなく) が必要。 `xcode-select --install` ではなく App Store から Xcode をインストール → `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`。
