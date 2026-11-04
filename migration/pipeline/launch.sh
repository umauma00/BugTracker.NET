#!/usr/bin/env bash
# launch.sh <stage> <uid> <comma-separated members>  → renders prompts (once), marks the stage in flight,
# prints the prompt file(s) the orchestrator hands to the agent(s).   land.sh <uid> <stage> clears the mark.
set -euo pipefail
st=$1; uid=$2; members=$3
repo="$(cd "$(dirname "$0")/../.." && pwd)"
p="$repo/migration/prompts"
if [ ! -f "$p/$uid.implementer.md" ]; then
  args=(); IFS=',' read -ra ms <<< "$members"; for m in "${ms[@]}"; do args+=(--unit "$m"); done
  python3 -I "$repo/migration/pipeline/render.py" "${args[@]}" --out "$repo" >/dev/null
fi
touch "$p/$uid.$st.inflight"
case $st in
  implement) echo "$p/$uid.implementer.md";;
  review) echo "$p/$uid.reviewer.R1.md"; echo "$p/$uid.reviewer.R2.md";;
  adjudicate) echo "$p/$uid.adjudicator.md";;
  fix) echo "$p/$uid.fixer.md";;
esac
