# Confirmed findings — src__BugTracker.Web__btnet__sortable_html_table.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

1. **Raw `ColumnName` in heading cells** (`sortable_html_table.cs:151,162`): column names (SQL aliases, including
   custom-field and query-author aliases) are written without encoding even when `html_encode == true`. The rulebook does
   not say whether this counts as a source defect requiring a `BUG(port)` marker (The BUG rule) or is intended
   behavior (the `html_encode` flag only covers cell values). The implementer reproduced it unmarked; given the flag's
   evident intent this is defensible. Proposed rule: "Unencoded output of database-derived identifiers (column names)
   that the source never encodes is reproduced unmarked; a `BUG(port)` marker is required only where the source
   encodes the same value elsewhere on the same path." Evidence: `:151,162` vs `:243-245`.
2. **Inventory coverage for files without per-site decisions**: §5 says "a site missing from the inventory is an
   inventory bug", but this unit has zero rows and no construct that needs one. Proposed clarification (cf. S1-36 for
   element-content regions): "A unit whose constructs are all decided by rule rows needs no inventory row; zero rows is
   not an inventory bug." Evidence: `grep sortable_html_table migration/inventory.tsv` → empty.

1. **Core units with zero inventory rows.** §5 says "a site missing from the inventory is
   an inventory bug", but this unit has no rows at all, and it needs none. Proposed: add to §5
   "a core unit whose every construct is decided by a §2A table needs no inventory row;
   absence of rows for such a unit is not an inventory bug" (as S1-36 already says for
   element-content regions). Evidence: `grep sortable_html_table migration/inventory.tsv` → empty.
2. **`HttpUtility.HtmlEncode` overload choice.** The runtime has both `HtmlEncode(String)`
   and `HtmlEncode(Object)` (HttpUtility.java:37,82). The rulebook does not say which one a
   translator must target when the C# argument is `x.ToString()` and the Java argument is
   `Convert.ToString(x)` (statically `String`). Today both give the same result for non-null
   input. Proposed: "the overload is the one Java picks from the translated argument's
   static type; never cast to force the other". Evidence: target :252, source :245.
3. **Unencoded output that the source leaves raw** (column headings written without
   `HtmlEncode` even when `html_encode` is true, source :151,162; unquoted `href=` + raw
   id value, source :207-213). It is not decided whether this counts as a "defect" that needs
   `BUG(port)` under the BUG rule or as intended behavior. A second translator might add a
   marker. Proposed: "raw (unencoded) output of developer-controlled values (SQL column
   aliases, row ids) is not a BUG(port) site; only user-controlled data written unencoded
   needs a marker". Evidence: callers pass SQL with fixed aliases (e.g. users.aspx, reports.aspx).
