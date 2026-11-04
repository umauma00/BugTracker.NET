# Confirmed findings — src__BugTracker.Web__Security__Security.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

G1. **List collection initializers are undecided.** The §2A S1-29 row decides only `new Dictionary<K,V> { {k, v}, … }`, and the object-initializer row (C6) covers member assignment. `new List<T> { a, b, … }` (`Security.cs:41-78`) has no canonical form. Two translators could write `new ArrayList<>(Arrays.asList(a, b, …))`, `new ArrayList<>(List.of(…))`, or one `add` per element. These forms are not equivalent: `List.of` throws on a `null` element, while C# `List<T>.Add(null)` accepts it. The C# compiler emits one `Add` per element in source order. Proposed rule: "`new List<T> { e1, …, en }` → `new ArrayList<>(Arrays.asList(e1, …, en))` (elements evaluated left to right, `null` allowed, insertion order kept; `List.of` BANNED because it rejects `null`). For a field, use the same expression as its initializer." Evidence: `Security.cs:41-78`, translated here as `Arrays.asList`, which is correct but not mandated.

1. **Collection initializer for `List<T>`.** S1-29 decides `Dictionary` initializers (construct,
   then one `Collections2.Add` per entry). It is silent on `new List<T> { a, b, … }`
   (`Security.cs:41-78`). The implementer wrote `new ArrayList<>(Arrays.asList(a, b, …))`.
   Another implementer could write `var claims = new ArrayList<Claim>(); claims.add(a); …`. The
   behavior is the same: left-to-right evaluation, the same first exception, insertion order.
   But two agents could answer differently. *Proposed rule:* "`new List<T> { e1, …, en }` →
   `new ArrayList<>(Arrays.asList(e1, …, en))` (declared type per C1); evaluation order and the
   first exception are those of C#."
2. **Marker on the §0.1 "Not fixed" examples.** §0.1 lists `Security.cs:73` as preserved. It
   does not say whether the site needs a `BUG(port)` marker. The implementer added
   `// BUG(port): latent: … — repro: none today …`, which is reasonable under S2-28 because
   the claim is unobservable. Another implementer could leave it unmarked, since the
   behavior is "specified" rather than defective. *Proposed rule:* "Each §0.1 'Not fixed' site
   carries `// BUG(port):` (with `latent:` when unobservable, S2-28)."
