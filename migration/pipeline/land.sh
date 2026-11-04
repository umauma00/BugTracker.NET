#!/usr/bin/env bash
# land.sh <uid> <stage> [done]  → clears the in-flight mark; with "done" also writes the done marker.
repo="$(cd "$(dirname "$0")/../.." && pwd)"
rm -f "$repo/migration/prompts/$1.$2.inflight"
[ "${3:-}" = done ] && python3 -I "$repo/migration/pipeline/queue.py" done "$1"
python3 -I "$repo/migration/pipeline/queue.py" status
