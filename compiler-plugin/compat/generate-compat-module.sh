#!/bin/bash

# Generate a Capture-Code compiler compatibility module skeleton for a specific Kotlin version.
# Adapted from Metro's `generate-compat-module.sh` (Apache 2.0).

set -euo pipefail

# Ensure script is run from project root.
if [ ! -f "settings.gradle.kts" ] || [ ! -d "compiler-plugin/compat" ]; then
    echo "Error: This script must be run from the project root directory"
    echo "Example: ./compiler-plugin/compat/generate-compat-module.sh 2.2.0"
    exit 1
fi

show_help() {
    cat <<EOF
Usage: $0 [OPTIONS] <kotlin-version>

Generate a Capture-Code compiler compatibility module skeleton for a specific
Kotlin version.

Arguments:
  <kotlin-version>      Kotlin version to generate compatibility module for
                        (e.g., 2.2.0, 2.4.0-Beta1, 2.5.0-dev-1234)

Options:
  -h, --help            Display this help message and exit

Examples:
  $0 2.2.0
  $0 2.4.0-Beta1
  $0 2.5.0-dev-1234

Naming convention (3-digit minor-level granularity):
  - Dots are removed and digits are collapsed to the minor track:
      2.2.0          -> k220
      2.1.0          -> k210
  - Dashes become underscores and the suffix is lowercased:
      2.4.0-Beta1    -> k240_beta1
      2.5.0-dev-1234 -> k250_dev_1234
EOF
}

if [ $# -eq 0 ]; then
    show_help
    exit 1
fi

KOTLIN_VERSION=""
while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help)
            show_help
            exit 0
            ;;
        -*)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
        *)
            if [ -z "$KOTLIN_VERSION" ]; then
                KOTLIN_VERSION="$1"
            else
                echo "Error: Multiple versions specified"
                exit 1
            fi
            shift
            ;;
    esac
done

if [ -z "$KOTLIN_VERSION" ]; then
    echo "Error: No Kotlin version specified"
    show_help
    exit 1
fi

# Transform the version into a 3-digit-minor-level package suffix.
# 1. Split off pre-release classifier ("-Beta1" / "-dev-1234").
BASE_VERSION="${KOTLIN_VERSION%%-*}"
CLASSIFIER=""
if [ "$BASE_VERSION" != "$KOTLIN_VERSION" ]; then
    CLASSIFIER="${KOTLIN_VERSION#*-}"
fi

# 2. Extract major / minor digits (e.g., "2.2.0" -> "2", "2"). The patch
#    digit is intentionally dropped — Capture-Code uses minor-level
#    granularity (patch differences are handled by Factory.minVersion).
MAJOR="$(echo "$BASE_VERSION" | awk -F. '{print $1}')"
MINOR="$(echo "$BASE_VERSION" | awk -F. '{print $2}')"
PATCH="$(echo "$BASE_VERSION" | awk -F. '{print $3}')"

if [ -z "$MAJOR" ] || [ -z "$MINOR" ] || [ -z "$PATCH" ]; then
    echo "Error: Could not parse '$KOTLIN_VERSION' as a Kotlin version (need major.minor.patch)"
    exit 1
fi

PACKAGE_SUFFIX="${MAJOR}${MINOR}${PATCH}"
if [ -n "$CLASSIFIER" ]; then
    NORMALIZED_CLASSIFIER="$(echo "$CLASSIFIER" | tr '[:upper:]' '[:lower:]' | tr '-' '_')"
    PACKAGE_SUFFIX="${PACKAGE_SUFFIX}_${NORMALIZED_CLASSIFIER}"
fi

MODULE_NAME="k${PACKAGE_SUFFIX}"
MODULE_DIR="compiler-plugin/compat-${MODULE_NAME}"

echo "Generating compatibility module for Kotlin ${KOTLIN_VERSION}"
echo "  Module name:    ${MODULE_NAME}"
echo "  Module dir:     ${MODULE_DIR}"
echo "  Package suffix: ${PACKAGE_SUFFIX}"

if [ -d "${MODULE_DIR}" ]; then
    echo "Error: ${MODULE_DIR} already exists. Aborting to avoid overwriting work."
    exit 1
fi

mkdir -p "${MODULE_DIR}/src/main/kotlin/me/tbsten/capture/code/compat/${MODULE_NAME}"

# build.gradle.kts
cat > "${MODULE_DIR}/build.gradle.kts" <<EOF
plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

dependencies {
    // TODO: add \`kotlin-compiler-embeddable-${MODULE_NAME}\` to libs.versions.toml
    // pointing at Kotlin ${KOTLIN_VERSION}, then enable this line.
    // compileOnly(libs.kotlin.compiler.embeddable.${MODULE_NAME})
    compileOnly(libs.kotlin.compiler.embeddable)
    compileOnly(libs.auto.service.annotations)
    ksp(libs.auto.service.ksp)

    implementation(project(":compiler-plugin:compat"))
}
EOF

# CompatContextImpl.kt
cat > "${MODULE_DIR}/src/main/kotlin/me/tbsten/capture/code/compat/${MODULE_NAME}/CompatContextImpl.kt" <<EOF
package me.tbsten.capture.code.compat.${MODULE_NAME}

import com.google.auto.service.AutoService
import me.tbsten.capture.code.CaptureCodePluginConfig
import me.tbsten.capture.code.compat.CompatContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.name.ClassId

/**
 * Kotlin ${KOTLIN_VERSION} 向けの [CompatContext] 実装スケルトン。
 *
 * 各メソッドの TODO を、 そのバージョンで利用可能な API で実装してください。
 * 参考: \`compiler-plugin/compat-k200/.../CompatContextImpl.kt\` (Kotlin 2.0.x 向け実装)。
 */
public class CompatContextImpl : CompatContext {

    override fun transformIr(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        config: CaptureCodePluginConfig,
    ) {
        TODO("Implement IR transform for Kotlin ${KOTLIN_VERSION}")
    }

    override fun literalValueOrNull(expression: FirExpression): Any? {
        TODO("Implement literalValueOrNull for Kotlin ${KOTLIN_VERSION}")
    }

    override fun isLiteralExpression(expression: FirExpression): Boolean {
        TODO("Implement isLiteralExpression for Kotlin ${KOTLIN_VERSION}")
    }

    override fun toRegularClassSymbolOrNull(
        type: ConeKotlinType,
        session: FirSession,
    ): FirRegularClassSymbol? {
        TODO("Implement toRegularClassSymbolOrNull for Kotlin ${KOTLIN_VERSION}")
    }

    override fun classIdOf(symbol: FirRegularClassSymbol): ClassId? {
        TODO("Implement classIdOf for Kotlin ${KOTLIN_VERSION}")
    }

    @AutoService(CompatContext.Factory::class)
    public class Factory : CompatContext.Factory {
        override val minVersion: String = "${KOTLIN_VERSION}"

        override fun create(): CompatContext = CompatContextImpl()
    }
}
EOF

echo ""
echo "Generated:"
echo "  ${MODULE_DIR}/build.gradle.kts"
echo "  ${MODULE_DIR}/src/main/kotlin/me/tbsten/capture/code/compat/${MODULE_NAME}/CompatContextImpl.kt"
echo ""
echo "Next steps:"
echo "  1. Add \`kotlin-${MODULE_NAME} = \"${KOTLIN_VERSION}\"\` to gradle/libs.versions.toml [versions]."
echo "  2. Add \`kotlin-compiler-embeddable-${MODULE_NAME}\` library entry pointing at it."
echo "  3. Add \`include(\":compiler-plugin:compat-${MODULE_NAME}\")\` to settings.gradle.kts."
echo "  4. Add \`bundled(project(\":compiler-plugin:compat-${MODULE_NAME}\"))\` and"
echo "     \`testImplementation(project(\":compiler-plugin:compat-${MODULE_NAME}\"))\` to"
echo "     compiler-plugin/build.gradle.kts."
echo "  5. Uncomment the \`compileOnly(libs.kotlin.compiler.embeddable.${MODULE_NAME})\` line"
echo "     in ${MODULE_DIR}/build.gradle.kts and remove the temporary fallback."
echo "  6. Implement the TODO() methods in CompatContextImpl.kt using Kotlin ${KOTLIN_VERSION} APIs."
