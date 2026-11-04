# Review R1 — src/BugTracker.Web/btnet/whatsnew.cs

Target: `port/web/src/main/java/btnet/WhatsNew.java` (core unit, no template).
Read: RULEBOOK.md (all 1,884 lines), inventory rows `whatsnew.cs` L17, L18, L42, L44, L45, L51 (+ related rows Global.asax.cs L30, whatsnew.aspx L69), source, target, runtime signatures (`Convert`, `DateTimes.ticks`, `HttpRuntime.Cache()`, `Cache.get`, `HttpApplicationState.get/set`, `IIdentity.Name()`), callers of `add_news` (edit_bug.aspx.cs:697,789, send_email.aspx.cs:602, edit_comment.aspx.cs:174, BugFromEmailController.cs:175,237, tropo_recording.aspx:151) and the reader whatsnew.aspx:40-90.

Checked and found correct: header line (§6.1); license comment copied (§6.2); every member present (`mylock`, `prev_seconds`, `ten_million`, `add_news`, `BugNews` with all six fields) (§6.2); `private` on the default-private `mylock`/`prev_seconds` (§2A S2-12, which wins over the L18 row's form); `public const long` → `public static final long` (§2A const); string `==` → `Objects.equals` (§2A Strings); `DateTime.Now.Ticks` → `DateTimes.ticks(LocalDateTime.now())` (§2A C20); `lock` → `synchronized` (§2A); `HttpRuntime.Cache["Application"]` → `HttpRuntime.Cache().get("Application")` (§2A, inventory L44); `app["whatsnew"]` get/set, `List<T>` → `ArrayList`, `Add/RemoveAt(0)/Count` → `add/remove(0)/size()`, same list identity published (inventory L45, L51); `identity.Name` → `identity.Name()` evaluated before the lock as in C#; `Convert.ToString(long)`/`Convert.ToString(int)`/`Convert.ToInt32(String)` resolve to the runtime `Object` overloads with .NET semantics; null `app` → NPE ≈ NRE as in C#; second top-level type nested as `public static class BugNews` (§4); single-type imports, all used, all in §3.1 (§4); `// inventory:` citations correct (§6.5); trailer `todos=0` matches zero `TODO(port)` markers (§6.7); BUG marker format (§3).

## Findings

F1 | severity LOW | port/web/src/main/java/btnet/WhatsNew.java:35-40 | src/BugTracker.Web/btnet/whatsnew.cs:27-32 | §2A "The BUG rule" | The "prevent dupes" logic only bumps by one relative to the *last* value, so it does not prevent duplicates even on a single thread: three calls inside the same wall-clock second T give `T`, `T+1`, `T` (the third sees `prev_seconds == T+1`, keeps `T`, and resets `prev_seconds` to `T`). The list then holds two items with `seconds == T`, out of order. whatsnew.aspx:77 filters `news.seconds > since`, so a client that already polled with `since >= T` misses the third item. This is a source defect, reproduced correctly, but it is not marked. The only `BUG(port)` marker (line 25) describes the cross-thread race, which is a different defect: this one needs no concurrency. | Keep the code as it is and add a marker at the bump site, e.g. `// BUG(port): dedupe only compares with the previous value, so a third add_news in the same second reuses an earlier seconds value (non-monotonic, duplicate) — repro: EnableWhatsNewPage=1, three bug saves within one second`.

Counts: HIGH 0, MEDIUM 0, LOW 1.

## Rulebook obedience

The implementer followed the rulebook on every construct in the unit, with one exception:

1. §2A "The BUG rule": a reproducible source defect (F1) was reproduced but not marked. The inventory does not flag it either (row L18 covers only the unsynchronized race). §5 says a site missing from the inventory should be flagged, and the BUG rule still applies.

Notes (not deviations):
- `volatile` on `prev_seconds` has no C# counterpart. The rulebook says nothing about it, but inventory row L18 prescribes `static volatile long prev_seconds` explicitly. It does not conflict with any rule (S2-12 only adds `private`, which is present), so following the row is obedient. Behaviour is unchanged, and the race stays reproduced and marked.
- `@SuppressWarnings("unchecked")` on the local declaration is a compile-warning annotation only, and no rule forbids it.

Verdict: DEVIATIONS: 1 (§2A BUG rule).

## Rulebook gaps

1. **How other units refer to a §4 nested second top-level type.** whatsnew.aspx:69,75 uses the bare name `BugNews` (namespace `btnet`). After §4 the type is `btnet.WhatsNew.BugNews`, but neither §4 nor §2A says whether a dependent unit writes `import btnet.WhatsNew.BugNews;` and the bare name, or writes `WhatsNew.BugNews` qualified. Two implementers could differ, and with no import it fails to compile. Proposed rule (§4): "A reference to a nested second top-level type from another unit uses a single-type import of `<package>.<Primary>.<Type>` and the bare simple name, unless the J-C15 name-clash rule applies." Evidence: whatsnew.aspx:69,75, WhatsNew.java:84, POP3Main.cs:777 (the only other §4 site, which is referenced only inside its own file).
2. **Access modifier of the §4 nested type.** S2-12 exempts "§4's nested second top-level type" from the access-modifier rule, but §4 does not say which modifier it takes. The §4 example `static class AcceptAllCertificatePolicy` is package-private because the C# type has no modifier; here C# `public class BugNews` became `public static class`, which looks right but is undecided. Proposed rule (§4): "The nested class keeps the source type's explicit modifier; a type with no modifier (C# `internal`) is package-private; always `static`."
3. **Unsynchronized static mutable fields.** Whether to add `volatile` was decided only in inventory row L18, not by any rule, and other `STATIC_SHARED_UNSYNCHRONIZED` rows could go either way. Proposed rule (§2A General): "A static field that C# reads or writes without a lock is translated without `volatile` (or always with it — pick one) and marked `BUG(port)` when the race is observable." Evidence: whatsnew.cs:18,27-32, inventory row whatsnew.cs L18.
