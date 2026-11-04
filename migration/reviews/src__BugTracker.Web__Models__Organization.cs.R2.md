# Review R2 — src/BugTracker.Web/Models/Organization.cs

Target: `port/web/src/main/java/btnet/Models/Organization.java` (manifest row 36).
No template applies (core-layer entity, RULEBOOK §0 / §4).

## Inputs checked

- Source `src/BugTracker.Web/Models/Organization.cs:1-37`: `public partial class Organization` in
  namespace `btnet.Models`, 28 auto-properties (`int` ×26, `string` ×2), no
  initializers, no other members.
- Target `Organization.java:1-35`.
- Inventory: `grep "Organization.cs" migration/inventory.tsv` returns no rows. The unit has no
  per-site constructs (no ref/out, nullable, `??`, SQL, regions), so a row was not needed.
  This is not an inventory bug (§5).
- Rulebook §0 (core layer posture), §1 EF6 row (line 108), §2A General conventions
  (partial, access modifiers, auto-property, `T?`), §2A EF6 (lines 554-559), §2A JSON
  (declaration-order public fields, lines 689-692), §4 naming/package, §6 obligations.
- Cross-checks: `setup.sql:90-121` (`orgs` columns: every one is `int not null` apart from
  `og_name`/`og_domain` nvarchar), so the Java primitive `int` matches the C# non-nullable `int`.
  No other `partial class Organization` exists in `src/` (one source unit, one target, §4).

## Verification

- Member-by-member, checked by script (name, type, declaration order): all 28 fields match
  exactly. Names are verbatim, including the source typo `CategoryFieldPErmissionLevel`
  (§4: "nothing else is renamed"; `OrganizationMap` refers to it by string per §2A EF6).
- `string` → `String`, `int` → `int`. No property was nullable, so no boxing was required (§2A `T?` row).
- Auto-properties → `public` fields with the same names and no initializers (§2A auto-property row; §2A EF6).
- `partial class` → one plain Java class (§2A partial row). `public` kept (§2A access modifiers).
- Package `btnet.Models` = the namespace verbatim (§2A, §4). The file path matches the manifest.
- The unused `using System; using System.Collections.Generic;` have no Java counterpart and need no imports. Nothing else is imported (§4).
- Header `// Source: src/BugTracker.Web/Models/Organization.cs` on line 1 (§6.1).
- Trailer `// PORT STATUS: confidence=high todos=0` is the last line. The file has 0 `TODO(port)` markers, so the count is correct (§6.7).
- No source comments to copy (§6.2). No markers needed (§6.3).
- JSON shape: public fields in declaration order match Newtonsoft's output for this POCO (§2A JSON, S2-34).
- Compile-level: valid Java 21. It uses no runtime API (§3.1 not exercised).

## Findings

None.

## Rulebook obedience

OBEDIENT. Every applicable rule (§2A partial, access modifiers, auto-property, EF6
entities; §4 package/path/names; §6.1 header, §6.2 completeness, §6.7 trailer) was
followed. No deviations, including deviations with a harmless result.

## Rulebook gaps

- G1 (minor): §2A/§4 do not say what to do with C# `using` directives that need no Java
  import (here `System`, `System.Collections.Generic`, both unused). The implementer dropped
  them, which is the only sensible reading of §4's "explicit single-type imports". Proposed
  rule: "`using` directives are not translated; imports follow from the types the Java code
  actually references (§4)." Evidence: `Organization.cs:1-2`.
