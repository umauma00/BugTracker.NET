#!/usr/bin/env bash
# both-clean.sh <uid>: both reviewers reported zero findings → nothing to adjudicate (the adjudicator
# rules only on findings/disagreements, prompt 04). Writes the confirmed file as NONE and the done marker.
repo="$(cd "$(dirname "$0")/../.." && pwd)"; rv="$repo/migration/reviews"; u=$1
[ -f "$rv/$u.R1.md" ] && [ -f "$rv/$u.R2.md" ] || { echo "missing review"; exit 1; }
{ echo "# Confirmed findings — $u"; echo; echo "NONE"; echo; echo "Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed."; echo; echo "## Not confirmed"; echo; echo "## Rulebook gaps"; sed -n '/^## Rulebook gaps/,$p' "$rv/$u.R1.md" | tail -n +2; sed -n '/^## Rulebook gaps/,$p' "$rv/$u.R2.md" | tail -n +2; } > "$rv/$u.confirmed.md"
python3 -I "$repo/migration/pipeline/queue.py" done "$u"; python3 -I "$repo/migration/pipeline/queue.py" status
