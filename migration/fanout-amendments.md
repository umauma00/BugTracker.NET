# Rulebook amendment queue during the fan-out (prompt 04)

Collected from reviewer "Rulebook gaps" sections; applied between batches (D-005 mandate). Format: id — source — gap — proposed rule.

- A1 — PageAuthorizeAttribute R1 — §2A attribute/LINQ call-site example `attributes.stream().allMatch(a -> a.OnAuthorize(Page.User))` does not compile (annotation has no OnAuthorize) — use `new PageAuthorizeAttribute.Logic(a.value()).OnAuthorize(Page.User)`; fix inventory row BasePage.cs L30 (ClaimsPrincipal → IPrincipal per J-C16).
- A2 — MailFormat R2 — app-declared enums (not only .NET enums) → Java enum; enum fields without initializer default to the zero member explicitly (C# default ≠ Java null).
- A3 — SQLString R1/R2 — J-C17 vs §2A Database example on ToString() of app types overriding it → `x.toString()` when the static type is an app class that overrides ToString and x is provably non-null; else Strings.ToStringStrict.
- A4 — Startup R1/R2 — merged partial classes: member groups ordered as the `// Source:` header lists the files; header lists files in manifest order.
- A5 — BundleConfig R2 — .NET `Bundle` declared type → `ScriptBundle`; ordinal bundle file order recorded as accepted divergence (no affected files today).
- A6 — PageAllowAnonymous R1/R2 — translated attributes carry exactly @Retention(RUNTIME) @Target(...) [+@Inherited when Inherited=true and the type is a class target, +@Repeatable when AllowMultiple=true]; no @Documented.
- A7 — ClaimsIdentityExtensions R1/R2 — `x is T` + `(T)x` → Java pattern `x instanceof T t`; Convert.ToInt32/ToBoolean(string) map to the same runtime calls (culture en-US).
- A8 — WebApiConfig R1/R2 — §3 table omitted HttpConfiguration/GlobalConfiguration/RouteParameter → fixed by §3.1 index (D-009); add §2A row for WebApiConfig.Register and Global.asax GlobalConfiguration.Configure; /api/<controller>/<id> path handling TODO.
- A9 — BtnetRoles R1 — outputs are UTF-8 without BOM; a source BOM is never copied.
- A10 — IBugSearch R1/R2 — §6.2: C# XML doc comments (///) → Javadoc (/** */ with @param/@return), text copied verbatim.
- A11 — MailPriority R1/R2 — app enums: (int)e → ordinal() when no explicit values, else an int value field; (E)n, ToString(), Enum.Parse mapping; unassigned enum field = first member.
- A12 — WebApiConfig adjudicator — /api/<controller>/<segment> and trailing slash: map like Web API route (optional {id}); anonymous object → LinkedHashMap as a general rule.
- A13 — BugFromEmail R2 — C# using directives are not translated (imports derive from used types); §2A row for model validation attributes ([Required] → @Required).
- A14 — BugFromEmail R1 — // inventory: comment goes on the line just before the declaration/statement the row decides.
- A15 — BasePage R2 — B7 (RULEBOOK ~:1029) wording: [PageAllowAnonymous] is READ in BasePage.cs:23 and APPLIED on default.aspx.cs / forgot.aspx.cs (never on BasePage).
- A16 — BugRelationShip R1 — entity fields keep source order; runtime DbSet must not rely on Class.getFields() order (DbSet.java:56) — RUNTIME FIX.
- A17 — BugPost R1/R2 — Nullable<T> rule count (7) wrong: entity-field Nullable<T> sites (26 in Models/*.cs) need no inventory row (state it); non-nullable DateTime entity fields default: leave null (never read before load) — state as accepted.
- A18 — category R1/R2 (HIGH) — keyword suffix rule is case-sensitive (applied to RULEBOOK §2A immediately); runtime fail-fast on unknown Property(X) (sent to runtime author).
