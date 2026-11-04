# Review R2: Startup (src/BugTracker.Web/App_Start/Startup.Auth.cs + src/BugTracker.Web/Startup.cs)

Target: `port/web/src/main/java/btnet/Startup.java` (manifest rows 9-10, a shared partial target per §4).
Templates: none. This is a core-layer unit (§0), so §4 places no template for it.

Read for this review: RULEBOOK §0, §0.1, §2A (General conventions: partial class row :133, `var` :144, object initializer C6 :147), §2B B7 (:1013-1041), §3 security (:1088, :1294-1298), §4, §5, §6; inventory rows `Startup.Auth.cs L16` and `Startup.cs L8`; both sources; the target; the runtime `IAppBuilder`, `CookieAuthenticationOptions`, `PathString` and `SecurityConfig.runStartup()` (to check that the call can be made).

## Findings

None.

Checked and found correct:
- **Members (§6.2).** Both partial halves are present with the C# names and signatures: `public void Configuration(IAppBuilder app)` calls `ConfigureAuth(app)` (Startup.cs:13-16 → target:13-15), and `public void ConfigureAuth(IAppBuilder app)` is there (Startup.Auth.cs:13-21 → target:18-25).
- **Object initializer (C6, §2A :147).** The code constructs the object, then assigns `AuthenticationType` and then `LoginPath`, in source order (Startup.Auth.cs:18-19). It then calls `app.UseCookieAuthentication(o)`. This matches the canonical text at §2A :133 character for character. The literals `"ApplicationCookie"` and `"/default.aspx"` are unchanged.
- **Assembly attribute (§2A :133).** `[assembly: OwinStartup(typeof(btnet.Startup))]` (Startup.cs:8) becomes the exact comment `// [assembly: OwinStartup] → invoked by SecurityConfig`.
- **Runtime API (§3 :1294-1298).** `IAppBuilder.UseCookieAuthentication(CookieAuthenticationOptions)`, the public fields `AuthenticationType` and `LoginPath`, and `PathString(String)` all exist in `btnet.runtime.security`. The imports are explicit single-type imports from §3's package list (§4). Read by eye, the code compiles. The class is public with an implicit public no-arg constructor, and `Configuration(IAppBuilder)` is public, which is what `SecurityConfig.runStartup()` needs for `getDeclaredConstructor().newInstance()` and `getMethod("Configuration", IAppBuilder.class)`.
- **Behavior (B7, J-C14).** `LoginPath` `/default.aspx` is recorded, and SecurityConfig takes the API redirect target from it. The `AuthenticationType` is unchanged. No sanctioned fix applies to this unit and none was applied.
- **Package and path (§4).** Namespace `btnet` → `package btnet;` → `port/web/src/main/java/btnet/Startup.java`, matching the manifest.
- **Header (§6.1).** `// Source: src/BugTracker.Web/App_Start/Startup.Auth.cs + src/BugTracker.Web/Startup.cs` lists both merged files with repo-relative paths.
- **Source comment (§6.2).** "Enable the application to use a cookie to store information for the signed in user" is copied.
- **Inventory citations (§6.5).** `// inventory: src/BugTracker.Web/Startup.cs L8` and `// inventory: src/BugTracker.Web/App_Start/Startup.Auth.cs L16` are both present and in the correct format.
- **Markers and trailer (§6.3, §6.7).** The file has no `TODO(port)` markers, and the trailer `// PORT STATUS: confidence=high todos=0` is the last line, so the count is correct. Confidence `high` is justified.
- **Scope (§6.4).** Only the manifest target was written.

## Rulebook obedience

**OBEDIENT.** I found no deviation from any section. The translation is the §2A :133 canonical text. Header, inventory citations, imports, package/path and trailer all follow §4 and §6.

## Rulebook gaps

1. **Inventory row conflicts with the rule text (§5: a conflict is a finding for the human).** Inventory row `src/BugTracker.Web/App_Start/Startup.Auth.cs L16` prescribes `CookieAuthenticationOptions options = new CookieAuthenticationOptions(); options.AuthenticationType = …; app.UseCookieAuthentication(options);`. The §2A partial-class row (:133) prescribes `var o = new CookieAuthenticationOptions(); o.AuthenticationType = …; app.UseCookieAuthentication(o);`. Behavior is identical, and the implementer correctly followed the rule row (§5: rule rows win). Two agents could still write different text, so this is a gap under the meta-rule. Proposed: rewrite the inventory row's translation to the §2A :133 text (`var o`), tagged as a §5 inventory correction.
2. **Order of members in a merged partial class is not specified.** §2A :133 and §4 "partial" say one shared target but not the order of the members coming from each part. The implementer put `Configuration` (Startup.cs) before `ConfigureAuth` (Startup.Auth.cs). The 148 page+designer partials will face the same choice. Proposed: "Members of a merged partial class appear grouped by source file, in the order the files are listed in the `// Source:` header, each group in source order." For this unit that would mean either changing the header order or the member order to match. Currently the header lists Startup.Auth.cs first but the code puts Startup.cs's member first. This is not a finding, because no rule decides it.
