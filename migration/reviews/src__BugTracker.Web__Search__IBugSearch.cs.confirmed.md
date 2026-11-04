# Confirmed findings — src__BugTracker.Web__Search__IBugSearch.cs

NONE

Both reviewers reported 0 findings (HIGH 0 / MEDIUM 0 / LOW 0); no adjudication needed.

## Not confirmed

## Rulebook gaps

1. **Form of copied XML doc comments.** §6.2 says only "Source comments are
   copied as comments" and excludes `<summary>` boilerplate only for
   `*.designer.cs`. It does not say whether a `///` `<summary>`/`<param>`/
   `<returns>` block is copied verbatim as `//` lines or turned into Javadoc
   (`@param`/`@return`), as this implementer did (`java:7-9, 11-14, 17-20,
   23-28`). The text is kept either way, but different units may end up with
   different forms. One small loss: the trailing space in "Re-index all bugs. "
   (`IBugSearch.cs:12`) is dropped in `java:12`. Proposed rule: "A `///` XML
   doc comment becomes Javadoc: `<summary>` text becomes the body, `<param
   name="x">` becomes `@param x`, `<returns>` becomes `@return`, and the text
   is kept verbatim apart from trailing whitespace."

- **G1 — XML doc comments (`///` with `<summary>`, `<param>`, `<returns>`) in non-designer files.** §6.2 says "Source comments are copied as comments" and §6.2 [S2-31] drops only the `<summary>`/`<remarks>` boilerplate of `*.designer.cs`. It does not say whether a hand-written XML doc comment is copied verbatim as `/// <summary>…` line comments or rendered as Javadoc (`/** … @param x … @return … */`). This implementer chose Javadoc (target :7-9, :11-14, :17-20, :23-28), dropping the XML tags and a trailing space after "Re-index all bugs. " (src:12). The text is preserved, so it is not a defect, but two implementers could answer differently (meta-rule). Proposed rule (§6.2): "A C# XML doc comment becomes a Javadoc comment on the same member: `<summary>` text → the description, `<param name="x">t</param>` → `@param x t`, `<returns>t</returns>` → `@return t`, other tags kept as text; line text otherwise unchanged (trailing whitespace may be dropped)." Evidence: `Search/IBugSearch.cs:6-8,11-14,17-20,23-28`; other XML-doc sites exist in `Search/BugSearch.cs` and elsewhere in `src/`.
