# Confirmed findings — src__BugTracker.Web__Security__BtnetRoles.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

None that affect this unit. One minor point, not a defect here: §6 does not say whether a source UTF-8 BOM must be stripped from the Java output. javac rejects a leading U+FEFF, and many `src/` files start with one (`BtnetRoles.cs:1`). Proposed rule for §6.1: "Java and template outputs are UTF-8 **without** BOM; a source BOM is never copied." Evidence: `src/BugTracker.Web/Security/BtnetRoles.cs` byte 0 is U+FEFF; this target is correctly BOM-free.

None. The §2A `const` row decides this unit, and that row cites `BtnetRoles.Admin` as its example.
