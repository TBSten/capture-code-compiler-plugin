# Publishing to Maven Central

Capture Code compiler plugin を Maven Central に publish するための手順をまとめる。
local 開発時の dry-run / 実 release / CI からの自動化を網羅する。

> 実 release は GitHub Actions workflow (task-038) から行うことを推奨。手動 release
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

- バージョン SSOT: ルート `gradle.properties` の `VERSION_NAME` (例: `0.1.0-SNAPSHOT`)。
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

## SNAPSHOT release

```shell
# VERSION_NAME=0.1.0-SNAPSHOT のまま
./gradlew publishAndReleaseToMavenCentral
```

`-SNAPSHOT` が末尾にあるため `publish.gradle.kts` の `isSnapshot` ロジックで `publishToMavenCentral(automaticRelease = false)` が選択され、Sonatype の SNAPSHOT repository (`https://central.sonatype.com/repository/maven-snapshots/`) に publish される。

## 本番 release

1. `gradle.properties` の `VERSION_NAME` から `-SNAPSHOT` を外す (例: `0.1.0`)。
2. tag を打つ (例: `v0.1.0`)。
3. 以下を実行:

```shell
# release publish (vanniktech plugin が staging repo の close / release まで自動化)
./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
```

`--no-configuration-cache` は vanniktech plugin の制約 (HTTP request task が CC 非対応) による回避策。

4. https://central.sonatype.com/ で release status を確認。
5. release 後、 `VERSION_NAME` を次の SNAPSHOT (例: `0.2.0-SNAPSHOT`) に bump して commit。

## CI からの自動 release

GitHub Actions から release する場合、 以下の Secret を設定:

| Secret 名                                              | 値                                          |
|--------------------------------------------------------|---------------------------------------------|
| `ORG_GRADLE_PROJECT_mavenCentralUsername`              | Sonatype User Token username                |
| `ORG_GRADLE_PROJECT_mavenCentralPassword`              | Sonatype User Token password                |
| `ORG_GRADLE_PROJECT_signingInMemoryKey`                | ASCII-armored GPG private key 全体          |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyId`              | (任意) GPG key id 下 8 桁                    |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`        | (任意) GPG passphrase                       |

workflow 内では:

```yaml
- name: Publish to Maven Central
  run: ./gradlew publishAndReleaseToMavenCentral --no-configuration-cache
  env:
    ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.ORG_GRADLE_PROJECT_mavenCentralUsername }}
    ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.ORG_GRADLE_PROJECT_mavenCentralPassword }}
    ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.ORG_GRADLE_PROJECT_signingInMemoryKey }}
    ORG_GRADLE_PROJECT_signingInMemoryKeyId: ${{ secrets.ORG_GRADLE_PROJECT_signingInMemoryKeyId }}
    ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.ORG_GRADLE_PROJECT_signingInMemoryKeyPassword }}
```

(release workflow の完全形は task-038 で実装予定。)

## Gradle Plugin Portal

`gradle-plugin` を [Gradle Plugin Portal](https://plugins.gradle.org/) にも publish する場合は `com.gradle.plugin-publish` plugin を追加する (本 ticket スコープ外、task-038 か別 ticket で対応)。 Maven Central published plugin marker でも `pluginManagement { repositories { mavenCentral() } }` 経由なら `plugins {}` syntax は動くので、 Plugin Portal は optional。

## トラブルシューティング

- **`The request for this plugin could not be satisfied because the plugin is already on the classpath with an unknown version`**:
  ルート `build.gradle.kts` の `plugins { alias(libs.plugins.maven.publish).apply(false) }` を**書かない**。 buildSrc convention plugin が classpath に乗せるため重複する。
- **`The value for extension 'mavenPublishing' property 'groupId$plugin' is final`**:
  convention plugin で `publishToMavenCentral()` が呼ばれた後に module 側で `coordinates(...)` を呼ぶと finalize 済 property への mutation で失敗する。 module 側では `coordinates(...)` を呼ばず、 `project.group` / `project.name` / `project.version` の自動値に任せる (SSOT)。
- **POM に internal module (compat) 依存が漏れる**:
  `:compiler-plugin` の `dependencies` で `implementation(project(":compiler-plugin:compat"))` にすると runtime variant 経由で POM の `<dependencies>` に出る。 `compileOnly(...)` + `bundled(...)` の組合せに切り替えること。
- **iOS target で `xcrun ... non-zero exit code: 72`**:
  Xcode フル版 (Command Line Tools ではなく) が必要。 `xcode-select --install` ではなく App Store から Xcode をインストール → `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`。
