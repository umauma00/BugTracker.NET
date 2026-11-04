# Review R1 — src/BugTracker.Web/Security/BtnetClaimTypes.cs

Target: `port/web/src/main/java/btnet/Security/BtnetClaimTypes.java` (manifest row 3; matches §4 path rule: module `port/web`, namespace `btnet.Security` as path, primary type = file stem).
Inventory: no rows for this source path (`grep` of `migration/inventory.tsv`). The unit has no constructs that need per-site decisions: it is 29 `public const string` fields. That is not an inventory bug.
Templates: none. It is not a page, master or control, so §4 places no template and the judge contract does not apply.

## Findings

None.

What I checked, assuming the translation was wrong:
- Completeness (§6.2): all 29 constants are present, in source order, with names and casing unchanged (§4 "Members keep C# names"). Source lines 5-35 map one-to-one to target lines 6-36, offset by one for the header. No Java keyword collisions.
- Values: the string literals are identical; a mechanical diff of every quoted literal in source and target showed no differences. The duplicated value `"og_can_edit_and_delete_posts"` (CanEditAndDeleteBugs at source :16 and CanEditAndDeletePosts at :22) is kept as-is. It is a source quirk, not a sanctioned §0.1 fix, so keeping it is correct.
- Construct mapping: `public const string X = v` becomes `public static final String X = v;`, per the §2A const row [S1-39]. The access modifiers are kept (§2A Access modifiers [S2-12]). Both are compile-time constants, so inlining semantics match.
- Type: `public class BtnetClaimTypes` keeps the same access and is non-final and non-static, like the C#. The implicit public no-arg constructor is the same in both languages.
- Package (§4): `package btnet.Security;` uses the C# namespace verbatim. No imports are needed and none are present.
- Header (§6.1): line 1 is `// Source: src/BugTracker.Web/Security/BtnetClaimTypes.cs`, the correct repo-relative path. The C# source has no comments to copy (§6.2).
- Markers (§6.3) and trailer (§6.7): the last line is `// PORT STATUS: confidence=high todos=0`. The file has 0 `TODO(port)` markers, so the count is correct, and there are no other marker styles.
- Scope (§6.4): only the manifest target is in play. Nothing about this unit needs another file.
- Compile-level: there are no references to any other type, so nothing outside §3 is called.

## Rulebook obedience

OBEDIENT. I found no deviations from §2A (const row, access modifiers), §4 (path, package, naming, single file) or §6 (header, completeness, markers, trailer).

## Rulebook gaps

None found for this unit. One observation, for information only: §6.2 says nothing about whether blank lines that group members in the source must be preserved. The implementer preserved them (target :22, :28). This has no effect on behavior, so no rule is proposed.

Counts: HIGH 0 / MEDIUM 0 / LOW 0. Obedience: OBEDIENT.
