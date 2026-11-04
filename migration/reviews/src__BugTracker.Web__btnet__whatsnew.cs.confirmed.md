# Confirmed findings — src/BugTracker.Web/btnet/whatsnew.cs → port/web/src/main/java/btnet/WhatsNew.java

Adjudicator. Inputs: R1, R2, source, target, RULEBOOK §2A "The BUG rule" (lines 698-704), §0 "old code is the spec" (lines 40-42), §3 marker format (line 1720).

## Confirmed

C1 | from R1.F1/R2.F1 | port/web/src/main/java/btnet/WhatsNew.java:36 (bump site; existing marker at :25 stays) | Add a `BUG(port)` marker for the single-thread de-dup defect; code unchanged | Keep lines 35-40 exactly as they are and insert above line 36: `// BUG(port): dedupe only compares with the previous value, so a third add_news in the same second yields seconds S, S+1, S (duplicate, non-monotonic; whatsnew.aspx:77 'seconds > since' then skips the later item) — repro: EnableWhatsNewPage=1, three add_news calls within one second on one thread` | Both reviewers report the same defect. Verified by trace of whatsnew.cs:27-32: call 1 at second S → S (prev=S); call 2 → S==prev → S+1 (prev=S+1); call 3 → S!=S+1 → S (prev=S). The reader at whatsnew.aspx:77 filters `news.seconds > since`. The only marker (WhatsNew.java:25) blames concurrency only. RULEBOOK §2A BUG rule (698-699) requires every reproduced source defect to be marked; the defect is not in §0.1.

## Not confirmed

(none — the two reviews each have one finding, and it is the same one)

## Rulebook gaps

1. **Cross-unit name of a §4 nested second top-level type** (R1 gap 1, R2 G3). `BugNews` is now `btnet.WhatsNew.BugNews`; no rule says whether dependents (whatsnew.aspx:69-75) write `import btnet.WhatsNew.BugNews;` plus the bare name, or `WhatsNew.BugNews` qualified. Proposed (§4): pick one form, e.g. a single-type import of `<package>.<Primary>.<Type>` and the bare simple name unless the J-C15 name-clash rule applies. Evidence: whatsnew.cs:72, whatsnew.aspx:69-75, WhatsNew.java:84.
2. **Access modifier of the §4 nested type** (R1 gap 2). S2-12 exempts it, but §4 does not state the modifier. Proposed (§4): keep the source type's explicit modifier; no modifier (C# internal) → package-private; always `static`. Evidence: whatsnew.cs:72 `public class BugNews` → WhatsNew.java:84 `public static class`; §4 example `static class AcceptAllCertificatePolicy`.
3. **`volatile` on unsynchronized static mutable fields** (R1 gap 3, R2 G1). Only inventory row whatsnew.cs L18 decides it. Proposed (§2A General): a non-`readonly` static written outside any lock (tag `STATIC_SHARED_UNSYNCHRONIZED`) → `private static volatile` (JLS 17.7 non-atomic long/double), race reproduced and marked `BUG(port)`; or explicitly never add `volatile` — pick one. Evidence: whatsnew.cs:18,27-32; WhatsNew.java:26.
4. **`final` on non-`readonly` lock objects** (R2 G2). §2A maps only `readonly` → `final`; inventory L17 adds `final` to `mylock`. Proposed (§2A lock row): a static `object` used only as a `lock` target and never reassigned → `private static final Object`, or forbid adding `final` without `readonly`. Evidence: whatsnew.cs:17,42; WhatsNew.java:23; bug.cs:1276.
