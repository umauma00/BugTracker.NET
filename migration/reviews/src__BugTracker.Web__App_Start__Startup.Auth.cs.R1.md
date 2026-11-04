# Review R1 — src/BugTracker.Web/App_Start/Startup.Auth.cs (+ src/BugTracker.Web/Startup.cs)

Target: `port/web/src/main/java/btnet/Startup.java` (28 lines). No template: the unit is core layer
(§0, `App_Start/`, `Startup.cs`), so §4 places no Thymeleaf file and the judge contract does not apply.

Read: RULEBOOK §0, §0.1, §1, §2A rows `partial class` (:133), `var` (:144), object initializer [C6] (:147),
§2B B7 (:1025-1037), §3 security package list (:1088) and OWIN types (:1294-1299), §4, §5, §6;
inventory rows `Startup.Auth.cs L16` and `Startup.cs L8`; both sources; runtime
`btnet.runtime.security.{IAppBuilder,CookieAuthenticationOptions,PathString,SecurityConfig}`.

## Findings

None.

Checked and found correct (the claims I tried to break):

- `Startup.java:13-15` vs `Startup.cs:13-16`: `public void Configuration(IAppBuilder app) { ConfigureAuth(app); }` — name, signature, body as §2A :133 / inventory `Startup.cs L8`.
- `Startup.java:18-25` vs `Startup.Auth.cs:13-20`: options built per C6 in source textual order (AuthenticationType, then LoginPath), literals `"ApplicationCookie"` and `"/default.aspx"` byte-identical; text matches the §2A :133 rule verbatim (`var o = …`).
- Runtime API (§3 :1294-1297): `IAppBuilder.UseCookieAuthentication(CookieAuthenticationOptions)`, public fields `AuthenticationType` (String) / `LoginPath` (PathString), `PathString(String)` — all exist with these types in `port/runtime/.../security/`. `"/default.aspx"` starts with `/`, so `PathString`'s Katana check does not throw.
- Boot path: `SecurityConfig.runStartup` (`SecurityConfig.java:81-87`) needs a public no-arg constructor and public `Configuration(IAppBuilder)`; class is `public` with the implicit public constructor, so reflection succeeds; the recorded `LoginPath` feeds the B7 API redirect (§2B :1036).
- `[assembly: OwinStartup(typeof(btnet.Startup))]` (`Startup.cs:8`) → exact comment `// [assembly: OwinStartup] → invoked by SecurityConfig` (`Startup.java:8`), per §2A :133.
- Partial class merged into one target per §4 `partial` line; package `btnet` = namespace (§4).
- Imports: explicit single-type, no wildcards, no statics, no unused imports (§4). `using` directives of the source have no Java counterpart needed.
- Source comment `// Enable the application to use a cookie …` copied (§6.2). No TODO/FIXME in source.
- Header `// Source: src/BugTracker.Web/App_Start/Startup.Auth.cs + src/BugTracker.Web/Startup.cs` lists both merged sources with repo-relative paths (§6.1, S1-41).
- Inventory citations in §6.5 format: `// inventory: src/BugTracker.Web/Startup.cs L8` (:9), `// inventory: src/BugTracker.Web/App_Start/Startup.Auth.cs L16` (:20).
- Trailer `// PORT STATUS: confidence=high todos=0` is the last non-empty line; file has 0 `TODO(port)` markers, so no undercount (§6.7). No markers are owed: no quirk, no unknown, no §0.1 fix touches this unit.
- Completeness (§6.2): the only members of both partials are `Configuration` and `ConfigureAuth`; both present. No extra members invented.
- Scope (§6.4): only the manifest target was written.

## Rulebook obedience

OBEDIENT. Every rule applicable to this unit (§2A partial class/:133, C6 object initializer, `var` for an initialized local, §3 OWIN types, §4 naming/package/imports, §6.1 header, §6.2 completeness and comment copying, §6.3 markers, §6.4 scope, §6.5 inventory citations, §6.7 trailer) was followed. No deviations found.

## Rulebook gaps

- **G1 — inventory/rulebook text conflict (finding for the human per §5).** Inventory row `src/BugTracker.Web/App_Start/Startup.Auth.cs L16` prescribes the local as `CookieAuthenticationOptions options = new CookieAuthenticationOptions(); options.… ;` whereas RULEBOOK §2A :133 prescribes `var o = new CookieAuthenticationOptions(); o.…`. The implementer correctly followed the rule (rule rows win, §5); no behavior difference. Proposed: rewrite the inventory row's translation text to the §2A :133 form so the two agree and a future reviewer cannot read the rule-following translation as an inventory miss.
- **G2 — header separator for merged multi-unit partials (minor).** §6.1 gives `+` as the joiner only through a page example (`edit_bug.aspx + edit_bug.aspx.cs + …`) and does not state the order for a partial merged across two *manifest units* (`Startup`). The implementer listed the manifest unit (`Startup.Auth.cs`) first. Proposed: §6.1 add "for a §4 `partial` merge, list the manifest unit first, then the other units in manifest order, joined by ` + `."
