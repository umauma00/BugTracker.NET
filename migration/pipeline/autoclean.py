#!/usr/bin/env python3
"""Close every unit in stage `adjudicate` whose two reviews BOTH report zero findings
(HIGH 0 / MEDIUM 0 / LOW 0). Nothing to adjudicate (the adjudicator rules on findings and
disagreements only, prompt 04); writes the NONE confirmed file + done marker via both-clean.sh."""
import re, subprocess, sys
from pathlib import Path
REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "migration/pipeline"))
import queue as q  # noqa
ZERO = re.compile(r"HIGH\W{0,3}0\b.{0,40}MEDIUM\W{0,3}0\b.{0,40}LOW\W{0,3}0\b|0\s+HIGH.{0,20}0\s+MEDIUM.{0,20}0\s+LOW", re.I | re.S)
manifest, units, _ = q.load()
for m in units:
    if q.stage(m, manifest) != "adjudicate":
        continue
    u = q.uid(m)
    rv = REPO / "migration/reviews"
    texts = [(rv / f"{u}.{r}.md").read_text(errors="replace") for r in ("R1", "R2")]
    def zero(t):
        if ZERO.search(t):
            return True
        has_finding = re.search(r"^\s*[-*]?\s*\**F\d+\**\s*[|(:]", t, re.M)
        return (not has_finding) and re.search(r"\bOBEDIENT\b", t) and not re.search(r"DEVIATIONS:\s*[1-9]", t)
    if all(zero(t) for t in texts):
        (REPO / "migration/prompts" / f"{u}.adjudicate.inflight").unlink(missing_ok=True)
        subprocess.run(["bash", str(REPO / "migration/pipeline/both-clean.sh"), u], capture_output=True)
        print("auto-closed", u)
