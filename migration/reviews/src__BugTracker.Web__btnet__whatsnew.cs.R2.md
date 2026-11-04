# Review R2 — src/BugTracker.Web/btnet/whatsnew.cs → port/web/src/main/java/btnet/WhatsNew.java

Reviewer: R2 (adversarial, read-only). Inputs read: RULEBOOK.md (all 1,884 lines, incl. §3.1),
inventory rows for `src/BugTracker.Web/btnet/whatsnew.cs` (L17, L18, L42, L44, L45, L51) plus the
related rows for `Global.asax.cs` L30 and `whatsnew.aspx` L69, the C# source, the Java target,
the runtime classes it calls (`Convert`, `DateTimes`, `IIdentity`, `HttpRuntime`, `Cache`,
`HttpApplicationState`), `btnet/Util.java` (`get_setting(String,String)` exists), and the reader
`src/BugTracker.Web/whatsnew.aspx:55-90`. Not a page/control, so no template; no judge spec
covers this unit (judge/specs/S1.md only mentions `view_whatsnew.aspx` as a menu link).

Checked and found correct: header (§6.1); `const` → `public static final long` (§2A); default-private
`mylock`/`prev_seconds` → `private` (§2A Access modifiers [S2-12], inventory L17); string `==` →
`Objects.equals` (§2A Strings); `DateTime.Now.Ticks` → `DateTimes.ticks(LocalDateTime.now())`
(§2A Dates [C20]); `Convert.ToString`/`Convert.ToInt32` (runtime `Object` overloads, §2A Convert);
`identity.Name` → `identity.Name()` (§2A runtime-facade property; `IIdentity.Name()` exists);
`lock` → `synchronized (mylock)` (§2A, inventory L42); `HttpRuntime.Cache["Application"]` →
`HttpRuntime.Cache().get("Application")` and the cast (§2A Web intrinsics, inventory L44);
`app["whatsnew"]` get/set (§2A indexer, inventory L45/L51); `List<T>` → `ArrayList`, `Add`/`RemoveAt(0)`/
`Count` → `add`/`remove(0)`/`size()` (§2A collections, inventory L45); same list instance published
(inventory L51); evaluation order (setting read before the lock, `max` read inside the lock after
`add`, `identity.Name` dereferenced before the lock — NRE/NPE at the same point); second top-level
type `BugNews` → `public static` nested class, same members (§4 [S1-4]); source comments copied
(§6.2); `// inventory:` citations in the S1-24 format (§6.5); imports single-type, all in §3.1;
`@SuppressWarnings` on a local declaration is legal Java; status trailer last line, `todos=0`
matches zero `TODO(port)` markers (§6.7).

## Findings

F1 | severity LOW | port/web/src/main/java/btnet/WhatsNew.java:25,35-40 | src/BugTracker.Web/btnet/whatsnew.cs:27-32 (reader src/BugTracker.Web/whatsnew.aspx:77 `news.seconds > since`) | RULEBOOK §2A "The BUG rule"; §3 marker format | The source's de-dup is defective even on a single thread, and the port leaves that unmarked: the only `BUG(port)` attributes duplicate `seconds` solely to concurrency ("two concurrent add_news calls within the same second"). With one thread, three calls in the same wall-clock second give seconds `S, S+1, S` (the third sees `S != prev_seconds (S+1)`, so it is not bumped), i.e. a duplicate **and** a non-monotonic value; a `whatsnew.aspx` client that already polled with `since = S+1` never receives the third item. Three calls in one second are reachable (e.g. `edit_bug.aspx.cs:697` "added" then `:789` "updated" on concurrent/quick saves, `send_email.aspx.cs:602`, `edit_comment.aspx.cs:174`, `BugFromEmailController.cs:175,237`). Code is behaviorally faithful; only the marker is missing/incomplete. | Keep the code exactly as is; add (or extend the existing marker to) `// BUG(port): de-dup only compares with the previous value, so 3+ add_news calls in one second yield seconds S, S+1, S (duplicate, non-monotonic; whatsnew.aspx:77 'seconds > since' then skips the later item) — repro: three add_news calls within the same second on one thread` at whatsnew.cs:28 (Java line 36). The existing concurrency marker stays.

Counts: HIGH 0, MEDIUM 0, LOW 1.

## Rulebook obedience

OBEDIENT. Every construct in the unit is translated by the §2A/§4/§6 rule that governs it (list
above). Two choices go beyond what the rulebook text itself says, but both are dictated by the
unit's own inventory rows (§5: implementers look up per-site decisions there; no rule contradicts
them), so they are not deviations:

- `prev_seconds` declared `volatile` — from inventory row `whatsnew.cs L18` (STATIC_SHARED_UNSYNCHRONIZED,
  "static volatile long prev_seconds; BUG(port) note kept"). No §2A rule mentions `volatile`
  (see gap G1).
- `mylock` declared `final` although the C# field is not `readonly` (§2A maps only `readonly` →
  `final`) — from inventory row `whatsnew.cs L17` (LOCK_OBJECT, "private static final Object mylock").
  Unobservable (never reassigned). See gap G2.

The `BUG(port)` marker present is in the §3 format and is required by inventory L18; its
incompleteness is F1 (a marker defect, not a disobeyed rule).

## Rulebook gaps

G1 — `volatile` on unsynchronized static mutable fields. The rulebook has no row for a C# static
field that is read/written from request threads without a lock; only the inventory (whatsnew.cs L18)
decides `volatile`. Two implementers of a similar site (any other STATIC_SHARED_UNSYNCHRONIZED row)
could differ. Proposed rule (§2A General): "A non-`readonly` static field written outside any lock
from request threads (inventory tag `STATIC_SHARED_UNSYNCHRONIZED`) → `private static volatile`
(Java `long`/`double` writes are not atomic without it, JLS 17.7; .NET x64 writes are); the race
itself is reproduced and marked `BUG(port)`." Evidence: whatsnew.cs:18,27-32; WhatsNew.java:26.

G2 — `final` on lock objects. §2A says `readonly` → `final` but is silent on a never-reassigned,
non-`readonly` static used only as a `lock` target; the inventory adds `final` here
(whatsnew.cs L17). Proposed: "`static object x = new Object()` used only as a `lock (x)` target and
never reassigned → `private static final Object x` (lock row of §2A)", or explicitly forbid adding
`final` where C# has no `readonly`. Evidence: whatsnew.cs:17,42; bug.cs:1276 (the other lock site).

G3 — Cross-unit name of a nested second top-level type. §4 makes `BugNews` the nested
`btnet.WhatsNew.BugNews`, but no rule says how other units that use the C# top-level name
(`whatsnew.aspx:69,74` `List<BugNews>`, `BugNews news`) must refer to it. Proposed (§4): "References
from other units to a second top-level type are written `<Primary>.<Name>` (`WhatsNew.BugNews`),
imported as `btnet.WhatsNew.BugNews` only where needed." Evidence: whatsnew.cs:72; whatsnew.aspx:69-74.
