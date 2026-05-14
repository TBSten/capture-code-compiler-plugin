#!/usr/bin/env bash
#
# Extract the release notes for a given version from CHANGELOG.md.
#
# Usage:
#   scripts/extract-changelog.sh <version>
#   scripts/extract-changelog.sh 0.1.0
#
# Output:
#   Writes the body of the matching `## [<version>] - <date>` section
#   (or `## [<version>]` without date) to stdout, excluding the heading
#   line itself and excluding any trailing reference-link definitions.
#
# Exit codes:
#   0  success
#   1  usage error
#   2  version section not found
#
# Intended caller:
#   The GitHub Actions release workflow (.github/workflows/release.yml,
#   added by task-038) passes the resulting file to `softprops/action-gh-release`
#   via `body_path`.
#

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "usage: $0 <version>" >&2
    echo "example: $0 0.1.0" >&2
    exit 1
fi

VERSION="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHANGELOG="${SCRIPT_DIR}/../CHANGELOG.md"

if [[ ! -f "${CHANGELOG}" ]]; then
    echo "error: CHANGELOG.md not found at ${CHANGELOG}" >&2
    exit 2
fi

# awk extracts lines between the matching `## [VERSION]` heading and
# the next `## ` heading (exclusive). The version match is done by
# literal prefix comparison via `index()`, so regex metacharacters in
# the version string (e.g. the dots) need no escaping. Reference-style
# link definitions at the bottom (`[VERSION]: https://...`) are also
# excluded by stopping at the first such line.
BODY="$(
    awk -v prefix="## [${VERSION}]" '
        index($0, prefix) == 1 { capture = 1; next }
        capture && /^## \[/ { capture = 0 }
        capture && /^\[[^]]+\]:[[:space:]]/ { capture = 0 }
        capture { print }
    ' "${CHANGELOG}"
)"

# Trim leading and trailing blank lines.
BODY="$(printf '%s\n' "${BODY}" | awk '
    NF { found = 1 }
    found { lines[++n] = $0 }
    END {
        # Drop trailing blank lines.
        while (n > 0 && lines[n] ~ /^[[:space:]]*$/) n--
        for (i = 1; i <= n; i++) print lines[i]
    }
')"

if [[ -z "${BODY}" ]]; then
    echo "error: no changelog entry found for version ${VERSION}" >&2
    exit 2
fi

printf '%s\n' "${BODY}"
