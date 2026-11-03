#!/usr/bin/env python3
"""Deterministic gap sweep (prompt 02): enumerate every site where Java demands a
decision that C# let the original keep in its head. Parser-based (tree-sitter),
never judgment. The output list is the inventory's definition of done: every
site here must get a row in migration/inventory.tsv.

Categories (each one is a decision the rulebook's defaults may not cover):
  REF_OUT        ref/out parameter declarations and ref/out arguments
  STATE          Session[] / Application[] / Cache[] / HttpRuntime.Cache[] keys
  STATIC_MUTABLE static fields that are not const/readonly (shared across requests)
  CONTROL_FLOW   Server.Transfer / Server.Execute / Response.End / Response.Redirect
  CATCH          catch clauses with a specific exception type (Java types differ)
  FORMAT         .ToString("fmt"), String.Format, DateTime.ParseExact/TryParseExact
  REGEX          Regex construction / static Regex calls
  PROCESS        Process / ProcessStartInfo (svn/git/hg shell-outs)
  LINQ           query expressions and LINQ operator calls
  DELEGATE       delegate / event declarations, lambdas, anonymous methods
  NULLABLE       nullable value types (int? ...) and the ?? operator
  THREADING      lock, Thread, ThreadPool, Timer

Usage: migration/scripts/.venv/bin/python -I migration/scripts/gap_sweep.py --repo . --out migration/inventory/sweep.tsv
Two runs on the same tree produce byte-identical output.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import depmap_csharp as d  # noqa: E402

LINQ_OPS = {"Where", "Select", "SelectMany", "OrderBy", "OrderByDescending", "ThenBy",
            "ThenByDescending", "First", "FirstOrDefault", "Single", "SingleOrDefault",
            "Any", "All", "GroupBy", "Sum", "Max", "Min", "Average", "Distinct", "ToList",
            "ToArray", "ToDictionary", "Take", "Skip", "Cast", "OfType", "Last", "LastOrDefault",
            "Aggregate", "Concat", "Union", "Intersect", "Except", "Zip", "Reverse", "AsEnumerable"}
STATE_ROOTS = ("Session", "Application", "Cache", "HttpRuntime.Cache", "HttpContext.Current.Session",
               "HttpContext.Current.Application", "HttpContext.Current.Cache", "Context.Session",
               "Context.Cache", "Page.Session", "Page.Cache")
CONTROL = {"Server.Transfer", "Server.Execute", "Response.End", "Response.Redirect",
           "HttpContext.Current.Response.End", "HttpContext.Current.Response.Redirect",
           "HttpContext.Current.Server.Transfer", "ApplicationInstance.CompleteRequest"}


# Locals/fields that alias an application/session/cache object, found by
# `<name> = <...>.Application|Session|Cache` assignments; filled per file.
STATE_ALIASES = {}


def t(n, src):
    return src[n.start_byte:n.end_byte].decode("utf-8", errors="replace")


def sweep(src: bytes, rel: str, lmap, out):
    tree = d.PARSER.parse(src)
    import re as _re
    for m in _re.finditer(rb"\b([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(?:\([A-Za-z.]+\)\s*)?[A-Za-z_.]*\b(Application|Session|Cache)\b\s*;", src):
        STATE_ALIASES.setdefault(rel, set()).add(m.group(1).decode())
    # locals/params/fields declared with a state type: HttpApplicationState app, ...
    for m in _re.finditer(rb"\b(?:HttpApplicationState|HttpSessionState(?:Base)?|HttpApplicationStateBase|Caching\.Cache)\s+([A-Za-z_][A-Za-z0-9_]*)", src):
        STATE_ALIASES.setdefault(rel, set()).add(m.group(1).decode())

    def line(n):
        ln = n.start_point[0] + 1
        return lmap(ln) if lmap else ln

    def symbol(n):
        names = []
        p = n.parent
        while p is not None:
            if p.type in ("method_declaration", "constructor_declaration", "property_declaration",
                          "class_declaration", "struct_declaration", "local_function_statement",
                          "operator_declaration", "indexer_declaration"):
                nm = p.child_by_field_name("name")
                names.append(t(nm, src) if nm else p.type)
            p = p.parent
        return ".".join(reversed(names)) or "(markup)"

    def add(cat, n, construct):
        c = " ".join(construct.split())
        if len(c) > 160:
            c = c[:157] + "..."
        out.add((cat, rel, line(n), symbol(n), c))

    def visit(n):
        ty = n.type
        if ty == "parameter":
            mods = [t(c, src) for c in n.children if c.type == "modifier"]
            if "ref" in mods or "out" in mods:
                add("REF_OUT", n, t(n, src))
        elif ty == "argument":
            first = n.child(0)
            if first is not None and t(first, src) in ("ref", "out"):
                add("REF_OUT", n, t(n, src))
        elif ty == "element_access_expression":
            e = n.child_by_field_name("expression")
            et = t(e, src) if e is not None else ""
            # direct roots, plus aliases reached through a member (context.Application,
            # HttpContext.Current.Cache, ...) and locals bound to them (app = ...Application)
            if et in STATE_ROOTS or et.rsplit(".", 1)[-1] in ("Application", "Session", "Cache") \
                    or et in STATE_ALIASES.get(rel, ()):
                add("STATE", n, t(n, src))
        elif ty == "field_declaration":
            mods = [t(c, src) for c in n.children if c.type == "modifier"]
            if "static" in mods and "readonly" not in mods and "const" not in mods:
                add("STATIC_MUTABLE", n, t(n, src))
        elif ty == "invocation_expression":
            f = n.child_by_field_name("function")
            ft = t(f, src) if f is not None else ""
            name = ft.rsplit(".", 1)[-1]
            args = n.child_by_field_name("arguments")
            at = t(args, src) if args is not None else ""
            if ft.rsplit(".", 1)[-1] in ("Add", "Insert", "Remove", "Get") and \
                    ft.rsplit(".", 1)[0].rsplit(".", 1)[-1] in ("Cache", "Application", "Session"):
                add("STATE", n, t(n, src))
            elif ft in CONTROL:
                add("CONTROL_FLOW", n, t(n, src))
            elif name == "ToString" and at.strip("() ").startswith(("\"", "@\"")):
                add("FORMAT", n, t(n, src))
            elif ft in ("String.Format", "string.Format", "DateTime.ParseExact", "DateTime.TryParseExact"):
                add("FORMAT", n, t(n, src))
            elif ft.startswith("Regex.") or ft.startswith("System.Text.RegularExpressions.Regex."):
                add("REGEX", n, t(n, src))
            elif ft.startswith(("Process.", "System.Diagnostics.Process.")):
                add("PROCESS", n, t(n, src))
            elif name in LINQ_OPS and f is not None and f.type == "member_access_expression":
                add("LINQ", n, t(n, src))
            elif ft in ("ThreadPool.QueueUserWorkItem",):
                add("THREADING", n, t(n, src))
        elif ty == "object_creation_expression":
            ot = t(n.child_by_field_name("type"), src)
            base = ot.rsplit(".", 1)[-1]
            if base == "Regex":
                add("REGEX", n, t(n, src))
            elif base in ("Process", "ProcessStartInfo"):
                add("PROCESS", n, t(n, src))
            elif base in ("Thread", "Timer"):
                add("THREADING", n, t(n, src))
        elif ty == "catch_clause":
            decl = next((c for c in n.children if c.type == "catch_declaration"), None)
            add("CATCH", n, t(decl, src) if decl is not None else "catch (bare)")
        elif ty == "query_expression":
            add("LINQ", n, t(n, src))
        elif ty in ("delegate_declaration", "event_field_declaration", "event_declaration",
                    "lambda_expression", "anonymous_method_expression"):
            add("DELEGATE", n, t(n, src))
        elif ty == "nullable_type":
            add("NULLABLE", n, t(n, src))
        elif ty == "binary_expression" and any(c.type == "??" for c in n.children):
            add("NULLABLE", n, t(n, src))
        elif ty == "lock_statement":
            add("THREADING", n, "lock(" + t(n.child(2), src) + ")")
        for c in n.children:
            visit(c)

    visit(tree.root_node)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    a = ap.parse_args()
    repo = a.repo.resolve()
    files = d.walk_files(repo)
    out = set()
    for p in files:
        rel = d.rel(p, repo)
        raw = p.read_text(encoding="utf-8-sig", errors="replace")
        if p.suffix.lower() == ".cs":
            sweep(raw.encode("utf-8"), rel, None, out)
        else:
            code, lmap = d.markup_code(raw, "__Page")
            sweep(code.encode("utf-8"), rel, lmap, out)
    rows = sorted(out)
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with open(a.out, "w", encoding="utf-8") as f:
        f.write("category\tfile\tline\tsymbol\tconstruct\n")
        for r in rows:
            f.write("\t".join(str(x) for x in r) + "\n")
    counts = {}
    for r in rows:
        counts[r[0]] = counts.get(r[0], 0) + 1
    print(f"sites={len(rows)} " + " ".join(f"{k}={v}" for k, v in sorted(counts.items())))


if __name__ == "__main__":
    main()
