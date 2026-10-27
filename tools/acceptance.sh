#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT/tools/acceptance-part1.sh"
source "$ROOT/tools/acceptance-part2.sh"
