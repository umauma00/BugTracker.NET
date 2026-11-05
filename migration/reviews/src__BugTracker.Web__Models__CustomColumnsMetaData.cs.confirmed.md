# Confirmed findings — src__BugTracker.Web__Models__CustomColumnsMetaData.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

None that affect this unit. (Observation only, no change proposed: §2A "Access modifiers" and the EF6 entity
bullet together fully decide plain entity classes; the absence of inventory rows for `Models/*.cs` entity files
is consistent with §5, since no per-site decision exists.)

1. **Unused C# `using` directives in core units.** The rulebook settles this only for markup (§2B B3 "Static/`<%@ Import %>` directives → nothing"). §4 only implies that a core `.cs` `using` with no counterpart is dropped, and does not say whether it should be copied as a comment. Proposed rule for §2A General conventions: "`using X;` directives → no Java statement; Java imports are derived from the types actually referenced (§4)." Evidence: `Models/CustomColumnsMetaData.cs:1-2` (and every `Models/*.cs`), where the translation dropped them, which is the reading every reviewer will expect. Low impact.
2. **Entity nullability vs. the map.** `SortSequence` is `Nullable<int>` and correctly becomes `Integer`. `Order` is a non-nullable `int` primary key, so a SQL NULL in `ccm_colorder` would fail in EF6 with `InvalidOperationException` ("The 'Order' property … could not be set to a 'null' value"). §3 does not say what `DbSet` materialization does when it writes `DBNull` into a primitive field. Proposed rule for §2A EF6 / §3 `DbSet`: "materializing SQL NULL into a primitive (`int`, `boolean`, …) field throws `InvalidOperationException("The '<Prop>' property on '<Type>' could not be set to a 'null' value. …")`, as EF6." This belongs to the runtime author; translators do nothing. Latent: `ccm_colorder` is a key column.
