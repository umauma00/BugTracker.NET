# Confirmed findings — src__BugTracker.Web__App_Start__Startup.Auth.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

- **G1 — inventory/rulebook text conflict (finding for the human per §5).** Inventory row `src/BugTracker.Web/App_Start/Startup.Auth.cs L16` prescribes the local as `CookieAuthenticationOptions options = new CookieAuthenticationOptions(); options.… ;` whereas RULEBOOK §2A :133 prescribes `var o = new CookieAuthenticationOptions(); o.…`. The implementer correctly followed the rule (rule rows win, §5); no behavior difference. Proposed: rewrite the inventory row's translation text to the §2A :133 form so the two agree and a future reviewer cannot read the rule-following translation as an inventory miss.
- **G2 — header separator for merged multi-unit partials (minor).** §6.1 gives `+` as the joiner only through a page example (`edit_bug.aspx + edit_bug.aspx.cs + …`) and does not state the order for a partial merged across two *manifest units* (`Startup`). The implementer listed the manifest unit (`Startup.Auth.cs`) first. Proposed: §6.1 add "for a §4 `partial` merge, list the manifest unit first, then the other units in manifest order, joined by ` + `."

1. **Inventory row conflicts with the rule text (§5: a conflict is a finding for the human).** Inventory row `src/BugTracker.Web/App_Start/Startup.Auth.cs L16` prescribes `CookieAuthenticationOptions options = new CookieAuthenticationOptions(); options.AuthenticationType = …; app.UseCookieAuthentication(options);`. The §2A partial-class row (:133) prescribes `var o = new CookieAuthenticationOptions(); o.AuthenticationType = …; app.UseCookieAuthentication(o);`. Behavior is identical, and the implementer correctly followed the rule row (§5: rule rows win). Two agents could still write different text, so this is a gap under the meta-rule. Proposed: rewrite the inventory row's translation to the §2A :133 text (`var o`), tagged as a §5 inventory correction.
2. **Order of members in a merged partial class is not specified.** §2A :133 and §4 "partial" say one shared target but not the order of the members coming from each part. The implementer put `Configuration` (Startup.cs) before `ConfigureAuth` (Startup.Auth.cs). The 148 page+designer partials will face the same choice. Proposed: "Members of a merged partial class appear grouped by source file, in the order the files are listed in the `// Source:` header, each group in source order." For this unit that would mean either changing the header order or the member order to match. Currently the header lists Startup.Auth.cs first but the code puts Startup.cs's member first. This is not a finding, because no rule decides it.
