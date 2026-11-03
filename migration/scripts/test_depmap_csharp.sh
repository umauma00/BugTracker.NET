#!/usr/bin/env bash
# Regression test for depmap_csharp.py. Fixture covers: textual #include + its
# Import making an extension method visible (T3), Page.User shadowing the
# in-repo class User (only the fully qualified btnet.User is an edge), attribute
# "Attribute" suffix, designer global:: control field, Register Src, and a local
# variable shadowing a type name (Shadow.cs has no edges).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$(mktemp -d)"
"$here/.venv/bin/python" -I "$here/depmap_csharp.py" --repo "$here/fixtures/csharp" --out "$out" >/dev/null
diff <(cut -f1,2 "$out/edges.tsv") "$here/fixtures/csharp/expected_edges.tsv"
# determinism on the real tree
repo="$(cd "$here/../.." && pwd)"
a="$(mktemp -d)"; b="$(mktemp -d)"
"$here/.venv/bin/python" -I "$here/depmap_csharp.py" --repo "$repo" --out "$a" >/dev/null
"$here/.venv/bin/python" -I "$here/depmap_csharp.py" --repo "$repo" --out "$b" >/dev/null
diff -r "$a" "$b"
echo "depmap_csharp: OK"
