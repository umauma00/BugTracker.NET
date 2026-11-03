#!/usr/bin/env python3
"""Deterministic dependency mapper for C# / ASP.NET WebForms (BugTracker.NET).

Implements the kit's dependency-map contract (migration-kit/prompts/01-dependency-map.md),
modelled on migration-kit/scripts/depmap_python.py:
  - edges come from a real C# parser (tree-sitter-c-sharp), never from agent judgment
  - in-repo edges only: framework / NuGet types are neither edges nor misses
  - emits edges.tsv, order.txt (SCC-condensed topological order, dependencies first),
    cycles.txt (every SCC > 1 unit), plus units.tsv, package_edges.tsv,
    package_cycles.txt and warnings.txt
  - all output is repo-relative paths; two runs on the same tree are byte-identical

UNIT OF WORK. C# has no per-file imports, and a WebForms page is several files.
  - A markup file (.aspx/.ascx/.Master/.asax) is one unit together with its
    code-behind (<markup>.cs or the CodeBehind= attribute) and <markup>.designer.cs.
    The unit is named by the markup path.
  - Every other .cs file is its own unit.

WHAT COUNTS AS AN EDGE (unit A -> unit B: A needs a type declared in B)
  C# code (all files of the unit, plus inline markup code wrapped into a method):
  T1 a name in a type position (declarations, base lists, generic arguments, new,
     casts, typeof, is/as, attributes incl. the "Attribute" suffix rule, catch,
     foreach, patterns, global::-qualified designer field types)
  T2 a name in an expression position (e.g. Util.get_setting(...), btnet.Util.x)
     -- unless the identifier is declared in the unit as a local, parameter,
     field, property, event, method or designer field (C# simple-name lookup
     finds the member/local before the type; approximated unit-wide)
  T3 a call .M(...) where M is an extension method declared in a namespace
     visible to the unit
  Markup directives:
  M1 MasterPageFile="~/x.Master"    M2 <%@ MasterType TypeName|VirtualPath %>
  M3 <%@ Register Src="~/x.ascx" %> M4 <%@ Reference Control|Page|VirtualPath %>
  M5 Inherits="ns.Type"             (<%@ Import Namespace %> adds a visible namespace)
  Name resolution follows C# order: nested types of the enclosing type and its
  in-repo bases, the enclosing namespace and its parents, using/Import namespaces,
  using aliases; fully qualified names resolve directly.

Usage:
  migration/scripts/.venv/bin/python -I migration/scripts/depmap_csharp.py \
      --repo . --out migration/depmap
  (pip install -r migration/scripts/requirements-depmap.txt into that venv)

Test: see migration/scripts/fixtures/csharp/ and test_depmap_csharp.sh
"""

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

import tree_sitter_c_sharp
from tree_sitter import Language, Parser

# ---------------------------------------------------------------- scope ----
# Roots walked (repo-relative). Everything else in the repo is out of scope
# (screen_capture/ WinForms, firefox/, source_control_hooks/ Python, judge/ ...).
ROOTS = ["src/BugTracker.Web", "src/btnet_service"]
# Directory names never walked: third-party client code and build output.
SKIP_DIRS = {"Scripts", "Content", "edit_area", "datejs", "fonts", "bin", "obj",
             "packages", "Properties", "node_modules"}
# Files never units (build metadata).
SKIP_FILES = {"AssemblyInfo.cs"}
MARKUP_EXT = {".aspx", ".ascx", ".master", ".asax", ".inc"}  # .inc: server-side include fragments

CS = Language(tree_sitter_c_sharp.language())
PARSER = Parser(CS)

TYPE_DECL = {"class_declaration", "interface_declaration", "enum_declaration",
             "struct_declaration", "record_declaration", "delegate_declaration"}
# Nodes whose (identifier / qualified_name / generic_name) children are types.
TYPE_FIELDS = {
    ("variable_declaration", "type"), ("parameter", "type"),
    ("method_declaration", "returns"), ("method_declaration", "type"),
    ("property_declaration", "type"), ("indexer_declaration", "type"),
    ("event_declaration", "type"), ("delegate_declaration", "type"),
    ("operator_declaration", "type"), ("conversion_operator_declaration", "type"),
    ("local_function_statement", "type"),
    ("object_creation_expression", "type"), ("array_creation_expression", "type"),
    ("cast_expression", "type"), ("typeof_expression", "type"),
    ("default_expression", "type"), ("sizeof_expression", "type"),
    ("as_expression", "right"), ("is_expression", "right"),
    ("declaration_pattern", "type"), ("recursive_pattern", "type"),
    ("catch_declaration", "type"), ("foreach_statement", "type"),
    ("declaration_expression", "type"), ("type_pattern", "type"),
    ("array_type", "type"), ("nullable_type", "type"), ("pointer_type", "type"),
    ("ref_type", "type"), ("tuple_element", "type"), ("type_parameter_constraint", "type"),
    ("explicit_interface_specifier", "name"),
}
TYPE_CONTAINERS = {"base_list", "type_argument_list", "type_parameter_constraint",
                   "type_constraint"}
DECL_NAME_PARENTS = {"variable_declarator", "parameter", "property_declaration",
                     "event_declaration", "method_declaration", "local_function_statement",
                     "foreach_statement", "catch_declaration", "single_variable_designation",
                     "enum_member_declaration", "type_parameter", "event_field_declaration",
                     "constructor_declaration", "destructor_declaration",
                     "implicit_parameter", "label_statement", "from_clause",
                     "let_clause", "join_clause", "join_into_clause", "query_continuation"}


# Members inherited from framework base classes. Inside a subclass, C# simple-name
# lookup finds these before any type: `User.Identity` in a Page is Page.User, not
# the in-repo class btnet.User. Keyed by the framework base's simple name.
_CONTROL = {"Controls", "ID", "ClientID", "UniqueID", "Page", "Parent", "Visible",
            "ViewState", "Context", "Request", "Response", "Server", "Session",
            "Application", "Cache", "Trace", "User", "Items", "Site", "NamingContainer",
            "EnableViewState", "TemplateControl", "AppRelativeVirtualPath", "DataBind",
            "FindControl", "ResolveUrl", "ResolveClientUrl", "LoadControl", "Eval", "Bind",
            "IsPostBack", "IsCallback", "ClientScript", "Form", "Header", "Title",
            "Master", "MasterPageFile", "Validators", "IsValid", "PreviousPage",
            "Culture", "UICulture", "ErrorPage", "MaintainScrollPositionOnPostBack",
            "ViewStateUserKey", "Theme", "StyleSheetTheme", "MetaDescription", "MetaKeywords",
            "RouteData", "Model", "ModelState", "Initialize", "Dispose", "Equals", "GetType",
            "ToString", "GetHashCode", "Load", "Init", "PreRender", "Unload"}
FRAMEWORK_MEMBERS = {
    "Page": _CONTROL, "UserControl": _CONTROL, "MasterPage": _CONTROL, "Control": _CONTROL,
    "HttpApplication": {"Application", "Context", "Request", "Response", "Server",
                        "Session", "User", "Modules", "Site", "Equals", "GetType", "ToString"},
    "ApiController": {"User", "Request", "Configuration", "ControllerContext",
                      "ActionContext", "ModelState", "Url", "RequestContext", "Ok",
                      "BadRequest", "NotFound", "StatusCode", "Content", "Json",
                      "Created", "CreatedAtRoute", "InternalServerError", "Redirect",
                      "Conflict", "Unauthorized", "ResponseMessage", "Dispose"},
    "ServiceBase": {"ServiceName", "EventLog", "CanStop", "CanShutdown",
                    "CanPauseAndContinue", "AutoLog", "Run", "Stop", "OnStart", "OnStop"},
}


def rel(p: Path, repo: Path) -> str:
    return p.relative_to(repo).as_posix()


def walk_files(repo: Path):
    out = []
    for root in ROOTS:
        base = repo / root
        for p in sorted(base.rglob("*")):
            if not p.is_file():
                continue
            parts = p.relative_to(base).parts
            if any(x in SKIP_DIRS or x.startswith(".") for x in parts[:-1]):
                continue
            if p.name in SKIP_FILES:
                continue
            if p.suffix.lower() in MARKUP_EXT or p.suffix.lower() == ".cs":
                out.append(p)
    return out


DIRECTIVE = re.compile(r"<%\s*@\s*(\w+)(.*?)%>", re.S)  # also "<% @Import ...%>"
INCLUDE = re.compile(r"<!--\s*#include\s+(file|virtual)\s*=\s*\"([^\"]+)\"\s*-->", re.I)
ATTR = re.compile(r'(\w+)\s*=\s*"([^"]*)"')


def directives(text):
    for m in DIRECTIVE.finditer(text):
        attrs = {k.lower(): v for k, v in ATTR.findall(m.group(2))}
        yield m.group(1).lower(), attrs, text.count("\n", 0, m.start()) + 1


def build_units(files, repo, warnings):
    """Return (units: unit -> [files], file_unit: file -> unit, markup: unit -> text)."""
    by_lower = {rel(p, repo).lower(): p for p in files}
    units, file_unit = {}, {}
    markups = [p for p in files if p.suffix.lower() in MARKUP_EXT]
    # pass 1: naming convention <markup>.cs / <markup>.designer.cs (how the
    # project's .csproj pairs them; DependentUpon)
    for p in markups:
        u = rel(p, repo)
        units[u] = [p]
        file_unit[p] = u
        for c in (Path(str(p) + ".cs"), Path(str(p) + ".designer.cs")):
            q = by_lower.get(rel(c, repo).lower())
            if q is not None and q not in file_unit:
                units[u].append(q)
                file_unit[q] = u
    # pass 2: a CodeBehind= attribute naming a file no markup claimed by
    # convention. A CodeBehind that points at ANOTHER page's code-behind (e.g.
    # LoggedOut.Master -> LoggedIn.master.cs) is a design-time hint only; the
    # runtime dependency is Inherits=, recorded as an M5 edge.
    for p in markups:
        u = rel(p, repo)
        text = p.read_text(encoding="utf-8-sig", errors="replace")
        for kind, attrs, ln in directives(text):
            if kind in ("page", "control", "master", "application"):
                cb = attrs.get("codebehind") or attrs.get("codefile")
                if not cb:
                    continue
                q = by_lower.get(rel(p.parent / cb, repo).lower())
                if q is None:
                    warnings.append(f"W6 CodeBehind file not found {cb!r}: {u}:{ln}")
                elif q not in file_unit:
                    units[u].append(q)
                    file_unit[q] = u
                elif file_unit[q] != u:
                    warnings.append(f"W7 CodeBehind={cb!r} belongs to {file_unit[q]} (not merged): {u}:{ln}")
    for p in files:
        if p in file_unit:
            continue
        if p.name.lower().endswith(".designer.cs"):
            warnings.append(f"W1 orphan designer file (no markup), kept as its own unit: {rel(p, repo)}")
        u = rel(p, repo)
        units[u] = [p]
        file_unit[p] = u
    return units, file_unit


# ------------------------------------------------------------ parsing ------

def text_of(node, src):
    return src[node.start_byte:node.end_byte].decode("utf-8", errors="replace")


def dotted(node, src):
    """Dotted name for identifier / qualified_name / alias_qualified / generic / member access."""
    t = node.type
    if t == "identifier":
        return text_of(node, src)
    if t == "generic_name":
        return text_of(node.child(0), src)
    if t == "qualified_name":
        q, n = node.child_by_field_name("qualifier"), node.child_by_field_name("name")
        a, b = dotted(q, src), dotted(n, src)
        return f"{a}.{b}" if a and b else None
    if t == "alias_qualified_name":  # global::X.Y
        n = node.child_by_field_name("name")
        return dotted(n, src)
    if t == "member_access_expression":
        e, n = node.child_by_field_name("expression"), node.child_by_field_name("name")
        a, b = dotted(e, src), dotted(n, src)
        return f"{a}.{b}" if a and b else None
    return None


class FileInfo:
    def __init__(self):
        self.types = []          # (fqn, kind, bases[list of raw dotted], line)
        self.usings = set()      # namespaces
        self.aliases = {}        # alias -> dotted target
        self.ext_methods = []    # (namespace, method name)
        self.declared = set()    # identifiers declared as members/locals/params
        self.refs = []           # (dotted, kind 'T'|'E'|'X', ns, enclosing type fqn, line)
        self.namespaces = set()
        self.errors = 0


def parse_cs(src: bytes, info: FileInfo, default_ns="", default_type=None, line_map=None):
    tree = PARSER.parse(src)

    def line(n):
        ln = n.start_point[0] + 1
        return line_map(ln) if line_map else ln

    def visit(node, ns, enclosing):
        t = node.type
        if t == "ERROR":
            info.errors += 1
        if t in ("namespace_declaration", "file_scoped_namespace_declaration"):
            name = dotted(node.child_by_field_name("name"), src)
            ns2 = f"{ns}.{name}" if ns else name
            info.namespaces.add(ns2)
            for c in node.children:
                if c.type != "qualified_name" and c.type != "identifier":
                    visit(c, ns2, enclosing)
            return
        if t == "using_directive":
            alias = None
            target = None
            for c in node.named_children:
                if c.type == "identifier" and alias is None and any(
                        x.type == "=" for x in node.children):
                    alias = text_of(c, src)
                elif c.type in ("qualified_name", "identifier", "alias_qualified_name", "generic_name"):
                    target = dotted(c, src)
            if alias and target and target != alias:
                info.aliases[alias] = target
            elif target:
                info.usings.add(target)
            return
        if t in TYPE_DECL:
            name = text_of(node.child_by_field_name("name"), src)
            fqn = f"{enclosing}.{name}" if enclosing else (f"{ns}.{name}" if ns else name)
            bases = []
            for c in node.children:
                if c.type == "base_list":
                    for b in c.named_children:
                        d = dotted(b if b.type != "primary_constructor_base_type" else b.child(0), src)
                        if d:
                            bases.append(d)
            info.types.append((fqn, t, bases, line(node)))
            # extension methods: static class, method whose first parameter has `this`
            if t == "class_declaration":
                for m in node.children:
                    if m.type == "declaration_list":
                        for md in m.named_children:
                            if md.type == "method_declaration":
                                pl = md.child_by_field_name("parameters")
                                if pl and pl.named_child_count:
                                    p0 = pl.named_children[0]
                                    if any(x.type == "modifier" and text_of(x, src) == "this"
                                           for x in p0.children):
                                        info.ext_methods.append(
                                            (ns, text_of(md.child_by_field_name("name"), src)))
            for c in node.children:
                visit(c, ns, fqn)
            return
        # declared names (shadow set for T2)
        if t == "identifier":
            p = node.parent
            if p is not None and p.type in DECL_NAME_PARENTS and p.child_by_field_name("name") == node:
                info.declared.add(text_of(node, src))
                return
            if p is not None and p.type == "variable_declarator":
                info.declared.add(text_of(node, src))
                return
        # references
        if t in ("identifier", "qualified_name", "generic_name", "alias_qualified_name"):
            kind = classify(node)
            if kind:
                d = dotted(node, src)
                if d:
                    info.refs.append((d, kind, ns, enclosing, line(node)))
                if t == "generic_name":
                    for c in node.children:
                        visit(c, ns, enclosing)
                return
        if t == "member_access_expression":
            d = dotted(node, src)
            if d:
                info.refs.append((d, "E", ns, enclosing, line(node)))
                # extension-method candidate: X.M(...)
                if node.parent is not None and node.parent.type == "invocation_expression":
                    info.refs.append((text_of(node.child_by_field_name("name"), src), "X", ns, enclosing, line(node)))
                return
            # left side is something else (call, this, indexer...): descend into it
            e = node.child_by_field_name("expression")
            if e is not None:
                visit(e, ns, enclosing)
            n = node.child_by_field_name("name")
            if n is not None and node.parent is not None and node.parent.type == "invocation_expression":
                info.refs.append((dotted(n, src) or "", "X", ns, enclosing, line(node)))
            return
        if t == "attribute":
            n = node.child_by_field_name("name")
            d = dotted(n, src) if n else None
            if d:
                info.refs.append((d, "A", ns, enclosing, line(node)))
            for c in node.children:
                if c is not n:
                    visit(c, ns, enclosing)
            return
        for c in node.children:
            visit(c, ns, enclosing)

    def classify(node):
        p = node.parent
        if p is None:
            return None
        if p.type in ("qualified_name", "alias_qualified_name"):
            return None  # handled at the outermost qualified_name
        if p.type == "member_access_expression":
            return None
        for (pt, f) in TYPE_FIELDS:
            if p.type == pt and p.child_by_field_name(f) == node:
                return "T"
        if p.type in TYPE_CONTAINERS or (p.type == "generic_name"):
            return "T"
        if p.type == "primary_constructor_base_type":
            return "T"
        if p.type in ("using_directive", "namespace_declaration", "file_scoped_namespace_declaration"):
            return None
        if p.type in ("name_equals", "name_colon", "labeled_statement", "goto_statement"):
            return None
        if p.type == "argument" and p.child_by_field_name("name") == node:
            return None
        return "E"

    visit(tree.root_node, default_ns, default_type)
    return info


# ------------------------------------------------------------- markup ------

CODE_BLOCK = re.compile(r"<%(?!\s*@|--|\$)([=:#]?)(.*?)%>", re.S)
SCRIPT_BLOCK = re.compile(r"<script\b[^>]*\brunat\s*=\s*\"?server\"?[^>]*>(.*?)</script>", re.S | re.I)


def markup_code(text, class_name):
    """Synthetic C#: <script runat=server> bodies as members, inline blocks as
    one method body in document order. Returns (code, line map)."""
    members, body = [], []
    src_lines = []  # synthetic line -> markup line

    def add(lst, chunk, start_line):
        for i, ln in enumerate(chunk.split("\n")):
            lst.append((ln, start_line + i))

    stripped = text
    for m in SCRIPT_BLOCK.finditer(text):
        add(members, m.group(1), text.count("\n", 0, m.start(1)) + 1)
    stripped = SCRIPT_BLOCK.sub(lambda m: "\n" * m.group(0).count("\n"), text)
    for m in CODE_BLOCK.finditer(stripped):
        kind, code = m.group(1), m.group(2)
        ln = stripped.count("\n", 0, m.start(2)) + 1
        if kind in ("=", ":", "#"):
            code = f"__w({code});"
        add(body, code, ln)
    out = []
    # wrapped as another part of the page class so lookups see its members and bases
    out.append((f"partial class {class_name} {{", 0))
    out += members
    out.append(("void __body() {", 0))
    out += body
    out.append(("}}", 0))
    code = "\n".join(l for l, _ in out)
    src_lines = [n for _, n in out]
    return code, (lambda synth: src_lines[synth - 1] if 0 < synth <= len(src_lines) else 0)


# ---------------------------------------------------------- resolution -----

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    ap.add_argument("--explain", default=None, help="print evidence for one unit")
    args = ap.parse_args()
    repo = args.repo.resolve()
    warnings = []

    files = walk_files(repo)
    units, file_unit = build_units(files, repo, warnings)

    # parse every C# file; parse markup inline code
    finfo = {}
    unit_markup = {}
    for u, members in units.items():
        for p in members:
            info = FileInfo()
            raw = p.read_text(encoding="utf-8-sig", errors="replace")
            if p.suffix.lower() == ".cs":
                parse_cs(raw.encode("utf-8"), info)
                if info.errors:
                    warnings.append(f"W2 {info.errors} parse error node(s): {rel(p, repo)}")
            else:
                unit_markup[u] = (p, raw)
            finfo[p] = info

    # global indexes
    type_units = defaultdict(set)     # fqn -> units
    type_bases = defaultdict(list)    # fqn -> raw base names (with resolver context)
    type_ctx = {}                     # fqn -> (ns, usings, aliases)
    ext_index = defaultdict(set)      # method -> {(ns, unit)}
    for p, info in finfo.items():
        u = file_unit[p]
        for fqn, _k, bases, _ln in info.types:
            type_units[fqn].add(u)
            type_bases[fqn] += bases
            ns = fqn.rsplit(".", 1)[0] if "." in fqn else ""
            type_ctx.setdefault(fqn, (ns, set(), {}))
            type_ctx[fqn][1].update(info.usings)
            type_ctx[fqn][2].update(info.aliases)
        for ns, m in info.ext_methods:
            ext_index[m].add((ns, u))
    namespaces = set()
    for info in finfo.values():
        namespaces |= info.namespaces
    for fqn in type_units:
        parts = fqn.split(".")
        for i in range(1, len(parts)):
            namespaces.add(".".join(parts[:i]))

    def resolve(name, ns, usings, aliases, enclosing):
        """C#-order resolution of a (possibly dotted) name to a declared fqn, or None.
        Tries the longest prefix of the dotted chain that names a type."""
        parts = name.split(".")
        if parts[0] in aliases:
            parts = aliases[parts[0]].split(".") + parts[1:]
        for k in range(len(parts), 0, -1):
            head = ".".join(parts[:k])
            # nested in enclosing type chain (and its in-repo bases)
            scopes = []
            e = enclosing
            while e:
                scopes.append(e)
                for b in type_bases.get(e, []):
                    bf = resolve_simple(b, e.rsplit(".", 1)[0] if "." in e else "", type_ctx.get(e, ("", set(), {})))
                    if bf:
                        scopes.append(bf)
                e = e.rsplit(".", 1)[0] if "." in e and e.rsplit(".", 1)[0] in type_units else None
            for s in scopes:
                if f"{s}.{head}" in type_units:
                    return f"{s}.{head}"
            # namespace and parents
            n = ns
            while True:
                cand = f"{n}.{head}" if n else head
                if cand in type_units:
                    return cand
                if not n:
                    break
                n = n.rsplit(".", 1)[0] if "." in n else ""
            hits = sorted({f"{x}.{head}" for x in usings if f"{x}.{head}" in type_units})
            if len(hits) >= 1:
                return hits[0] if len(hits) == 1 else tuple(hits)
        return None

    def resolve_simple(name, ns, ctx):
        r = resolve(name, ns, ctx[1], ctx[2], None)
        return r if isinstance(r, str) else None

    edges = {}  # (from, to) -> first evidence
    def add_edge(a, b, ev):
        if a != b and (a, b) not in edges:
            edges[(a, b)] = ev

    for u, members in sorted(units.items()):
        declared = set()
        unit_types = []
        for p in members:
            declared |= finfo[p].declared
            unit_types += [t[0] for t in finfo[p].types]
        # members inherited from in-repo bases also shadow; so do members of the
        # framework base the chain ends in (FRAMEWORK_MEMBERS)
        def framework_members(fqn, seen):
            out = set()
            if fqn in seen:
                return out
            seen.add(fqn)
            for b in type_bases.get(fqn, []):
                bf = resolve_simple(b, fqn.rsplit(".", 1)[0] if "." in fqn else "", type_ctx[fqn])
                if bf:
                    out |= framework_members(bf, seen)
                else:
                    out |= FRAMEWORK_MEMBERS.get(b.rsplit(".", 1)[-1], set())
            return out
        for fqn in list(unit_types):
            declared |= framework_members(fqn, set())
        for fqn in unit_types:
            for b in type_bases.get(fqn, []):
                bf = resolve_simple(b, fqn.rsplit(".", 1)[0] if "." in fqn else "", type_ctx[fqn])
                if bf:
                    for bu in type_units[bf]:
                        for bp in units[bu]:
                            declared |= finfo[bp].declared

        # markup: directives + inline code
        if u in unit_markup:
            mp, text = unit_markup[u]
            # M6: <!-- #include file|virtual="..." --> is textual inclusion (one
            # level deep is all this repo uses; nested includes expanded too, cycle-safe)
            def expand(t, base, seen):
                def sub(m):
                    kind, path = m.group(1).lower(), m.group(2)
                    q = (repo / ROOTS[0] / path.lstrip("~/")) if kind == "virtual" else (base / path)
                    q = q.resolve()
                    fu = next((x for f, x in file_unit.items() if f.resolve() == q), None)
                    if fu is None or not q.is_file():
                        warnings.append(f"W3 unresolved include {path!r}: {rel(mp, repo)}")
                        return ""
                    add_edge(u, fu, f"M6 {rel(mp, repo)} #include {path}")
                    if q in seen:
                        return ""
                    inner = q.read_text(encoding="utf-8-sig", errors="replace")
                    # keep line numbers of the including file stable: fold to one line
                    return expand(inner, q.parent, seen | {q}).replace("\n", " ")
                return INCLUDE.sub(sub, t)
            text = expand(text, mp.parent, {mp.resolve()})
            imports, page_type = set(), None
            for kind, attrs, ln in directives(text):
                def target_unit(vpath):
                    if not vpath:
                        return None
                    v = vpath.replace("~/", "").lstrip("/")
                    q = (mp.parent / v) if not vpath.startswith("~") else (repo / ROOTS[0] / v)
                    q = q.resolve()
                    for f, fu in file_unit.items():
                        if f.resolve() == q:
                            return fu
                    warnings.append(f"W3 unresolved markup path {vpath!r}: {rel(mp, repo)}:{ln}")
                    return None
                if kind in ("page", "control", "master", "application"):
                    page_type = attrs.get("inherits")
                    t = target_unit(attrs.get("masterpagefile"))
                    if t:
                        add_edge(u, t, f"M1 {rel(mp, repo)}:{ln} MasterPageFile")
                    if page_type:
                        r = resolve(page_type, "", set(), {}, None)
                        for fu in (type_units.get(r, ()) if isinstance(r, str) else ()):
                            add_edge(u, fu, f"M5 {rel(mp, repo)}:{ln} Inherits={page_type}")
                elif kind == "mastertype" or kind == "previouspagetype":
                    if attrs.get("typename"):
                        r = resolve(attrs["typename"], "", set(), {}, None)
                        for fu in (type_units.get(r, ()) if isinstance(r, str) else ()):
                            add_edge(u, fu, f"M2 {rel(mp, repo)}:{ln} MasterType")
                    t = target_unit(attrs.get("virtualpath"))
                    if t:
                        add_edge(u, t, f"M2 {rel(mp, repo)}:{ln} MasterType VirtualPath")
                elif kind == "register":
                    t = target_unit(attrs.get("src"))
                    if t:
                        add_edge(u, t, f"M3 {rel(mp, repo)}:{ln} Register Src")
                    if attrs.get("namespace"):
                        imports.add(attrs["namespace"])  # tag classes resolve like usings
                elif kind == "reference":
                    t = target_unit(attrs.get("control") or attrs.get("page") or attrs.get("virtualpath"))
                    if t:
                        add_edge(u, t, f"M4 {rel(mp, repo)}:{ln} Reference")
                elif kind == "import":
                    imports.add(attrs.get("namespace", ""))
            page_ns = page_type.rsplit(".", 1)[0] if page_type and "." in page_type else ""
            page_cls = page_type.rsplit(".", 1)[-1] if page_type else "__NoCodeBehind"
            code, lmap = markup_code(text, page_cls)
            info = FileInfo()
            parse_cs(code.encode("utf-8"), info, default_ns=page_ns, line_map=lmap)
            info.usings |= imports
            if info.errors:
                warnings.append(f"W2 {info.errors} parse error node(s) in inline code: {rel(mp, repo)}")
            finfo[mp] = info
            declared |= info.declared

        for p in members:
            info = finfo[p]
            ns_usings = info.usings
            for (name, kind, ns, enclosing, ln) in info.refs:
                where = f"{rel(p, repo)}:{ln}"
                if kind == "X":
                    for (ens, eu) in sorted(ext_index.get(name, ())):
                        visible = ens in ns_usings or ens == ns or (ns.startswith(ens + ".")) or ens == ""
                        if visible:
                            add_edge(u, eu, f"T3 {where} extension .{name}()")
                    continue
                if kind == "E":
                    first = name.split(".")[0]
                    if first in declared or first in ("this", "base"):
                        continue
                cands = [name]
                if kind == "A" and not name.endswith("Attribute"):
                    cands = [name + "Attribute", name]
                for c in cands:
                    r = resolve(c, ns, ns_usings, info.aliases, enclosing)
                    if r is None:
                        continue
                    if isinstance(r, tuple):
                        warnings.append(f"W4 ambiguous {c!r} -> {', '.join(r)}: {where}")
                        r_list = list(r)
                    else:
                        r_list = [r]
                    for fq in r_list:
                        for tu in sorted(type_units[fq]):
                            tag = {"T": "T1", "E": "T2", "A": "T1"}[kind]
                            add_edge(u, tu, f"{tag} {where} {c} -> {fq}")
                    break

    nodes = sorted(units)
    edge_list = sorted(edges)
    order, cycles = migration_order(nodes, edge_list)

    # package granularity: C# namespace of the unit's primary type == Java package (1:1)
    unit_pkg = {}
    for u in nodes:
        nss = sorted({t[0].rsplit(".", 1)[0] if "." in t[0] else "" for p in units[u] for t in finfo[p].types
                      if t[0].count(".") >= 1 and t[0].rsplit(".", 1)[0] not in type_units})
        unit_pkg[u] = nss[0] if nss else "(global)"
        if len(nss) > 1:
            warnings.append(f"W5 unit declares types in several namespaces {nss}: {u}")
    pkg_edges = sorted({(unit_pkg[a], unit_pkg[b]) for a, b in edge_list if unit_pkg[a] != unit_pkg[b]})
    pkgs = sorted(set(unit_pkg.values()))
    _porder, pcycles = migration_order(pkgs, pkg_edges)

    out = args.out
    out.mkdir(parents=True, exist_ok=True)
    with open(out / "edges.tsv", "w") as f:
        f.write("from\tto\tevidence\n")
        for a, b in edge_list:
            f.write(f"{a}\t{b}\t{edges[(a, b)]}\n")
    with open(out / "order.txt", "w") as f:
        f.write("# migration order: one batch per line, dependencies first\n")
        for scc in order:
            f.write("\t".join(scc) + "\n")
    with open(out / "cycles.txt", "w") as f:
        f.write(f"# strongly connected components > 1 unit: {len(cycles)}\n")
        for scc in cycles:
            f.write("\t".join(scc) + "\n")
    with open(out / "units.tsv", "w") as f:
        f.write("unit\tpackage\tfiles\n")
        for u in nodes:
            f.write(f"{u}\t{unit_pkg[u]}\t{','.join(rel(p, repo) for p in units[u])}\n")
    with open(out / "package_edges.tsv", "w") as f:
        f.write("from\tto\n")
        for a, b in pkg_edges:
            f.write(f"{a}\t{b}\n")
    with open(out / "package_cycles.txt", "w") as f:
        f.write(f"# package-level SCCs > 1 package: {len(pcycles)}\n")
        for scc in pcycles:
            f.write("\t".join(scc) + "\n")
    with open(out / "warnings.txt", "w") as f:
        for w in sorted(set(warnings)):
            f.write(w + "\n")

    if args.explain:
        for (a, b), ev in sorted(edges.items()):
            if a == args.explain or b == args.explain:
                print(f"{a} -> {b}\t{ev}")

    print(f"files={len(files)} units={len(nodes)} edges={len(edge_list)} batches={len(order)} "
          f"cycles={len(cycles)} largest_cycle={max((len(c) for c in cycles), default=0)} "
          f"packages={len(pkgs)} package_cycles={len(pcycles)} warnings={len(set(warnings))}")


def tarjan_scc(nodes, edges):
    """Iterative Tarjan (as in migration-kit/scripts/depmap_python.py): SCCs in
    reverse topological order of the condensation, i.e. dependencies first."""
    adj = {n: [] for n in nodes}
    for a, b in edges:
        adj[a].append(b)
    for n in adj:
        adj[n].sort()
    counter = [0]
    stack, on_stack = [], set()
    index, low = {}, {}
    sccs = []
    for start in sorted(nodes):
        if start in index:
            continue
        work = [(start, 0)]
        while work:
            node, pi = work[-1]
            if pi == 0:
                index[node] = low[node] = counter[0]
                counter[0] += 1
                stack.append(node)
                on_stack.add(node)
            recurse = False
            for i in range(pi, len(adj[node])):
                s = adj[node][i]
                if s not in index:
                    work[-1] = (node, i + 1)
                    work.append((s, 0))
                    recurse = True
                    break
                elif s in on_stack:
                    low[node] = min(low[node], index[s])
            if recurse:
                continue
            if low[node] == index[node]:
                scc = []
                while True:
                    w = stack.pop()
                    on_stack.discard(w)
                    scc.append(w)
                    if w == node:
                        break
                sccs.append(sorted(scc))
            work.pop()
            if work:
                parent = work[-1][0]
                low[parent] = min(low[parent], low[node])
    return sccs


def migration_order(nodes, edges):
    sccs = tarjan_scc(nodes, edges)
    return sccs, [s for s in sccs if len(s) > 1]


if __name__ == "__main__":
    main()
