#!/usr/bin/env bash
# dispatch.sh <max_units>: picks the next units from queue.py, marks them in flight,
# prints one line per agent to launch: <model>\t<prompt file>
repo="$(cd "$(dirname "$0")/../.." && pwd)"
python3 -I "$repo/migration/pipeline/queue.py" next "$1" | while IFS=$'\t' read -r st u m; do
  case $st in implement|fix) model=sonnet;; *) model=opus;; esac
  bash "$repo/migration/pipeline/launch.sh" "$st" "$u" "$m" | while read -r p; do printf '%s\t%s\n' "$model" "${p#$repo/migration/prompts/}"; done
done
