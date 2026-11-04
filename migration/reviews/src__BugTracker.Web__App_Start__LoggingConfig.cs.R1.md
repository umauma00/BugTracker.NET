# Review R1 — src/BugTracker.Web/App_Start/LoggingConfig.cs

Target: `port/web/src/main/java/btnet/App_Start/LoggingConfig.java` (70 lines). No template (core unit, §0).
Inventory: no rows for `App_Start/LoggingConfig.cs` (`grep LoggingConfig migration/inventory.tsv` returns nothing). The governing rule is the RULEBOOK §2A "Logging, mail, …" row `App_Start/LoggingConfig.cs` (RULEBOOK.md:644), so a missing row is not an inventory bug here: the rule decides the unit.
Context checked: `Global.asax.cs:29,101` (Configure is called twice at startup), `Global.asax.cs:23` (the only NLog call in the source: `logger.Fatal(exc)`), `packages.config:27` (NLog 3.1.0.0), runtime `btnet.runtime.io.Path.Combine(String...)` (exists, §3.1), `btnet.Util.get_log_folder()` (exists, `Util.java:679`).

## Findings

F1 | MEDIUM | LoggingConfig.java:23-24,59-62 | LoggingConfig.cs:31-32,37 | §1 Logging row, §2A LoggingConfig row (RULEBOOK.md:644), §0 "old code is the spec" | `config.reset()` on the shared Logback `LoggerContext`, then level + appender put on the **root** logger. In .NET, `LogManager.Configuration = config` replaces only NLog's configuration, and the `"*"` rule applies only to NLog loggers, which means the application's own loggers (`btnet.*`; the source has one, `Global.asax.cs:22`). ASP.NET/IIS never log through NLog. In the port, Spring Boot, Tomcat, Hikari, Thymeleaf and Spring Security log through the same context. So (a) with `LogEnabled=1`, every framework logger at TRACE goes into `btnet_log.txt`: different content and very large volume. (b) With `LogEnabled=0`, root OFF silences all framework logging, including startup and failure output. (c) `reset()` also removes Spring Boot's console appender and its configured logger levels. | Leave the context's root and the Boot configuration alone. Configure only the application logger subtree, e.g. `Logger app = config.getLogger("btnet"); app.detachAndStopAllAppenders(); app.addAppender(fileTarget); app.setLevel(logLevel); app.setAdditive(false);` (`detach…` keeps the double call at Global.asax.cs:29/101 idempotent). Rulebook-level question: see Gap G1. If the human keeps "root" as the rule, this finding becomes a rule clarification only. |

F2 | LOW | LoggingConfig.java:23-24 | LoggingConfig.cs:12-37 (assignment at :37) | source line | The C# builds a new `LoggingConfiguration` and publishes it atomically at the end (`:37`). If anything before that throws (`Util.get_log_folder()` → `get_folder`, `Path.Combine` with a null folder → `ArgumentNullException`, appender start failure), the previous NLog configuration stays active. The Java resets the live context **first** (`:24`), so any exception between `:24` and `:62` leaves the process with no appenders at all. The reset also drops every log event emitted during configuration. | Build and start the appender/encoder/policy on the context without resetting. Then, as the last statements, swap the logger's appenders and level (`detachAndStopAllAppenders` → `addAppender` → `setLevel`), mirroring `LogManager.Configuration = config` as a final assignment. |

F3 | LOW | LoggingConfig.java:50 | LoggingConfig.cs:13-18,31-32 | source line | `fileTarget.start()` opens (creates) `btnet_log.txt` and its folder eagerly. This happens even when `LogEnabled != "1"` (level OFF), and even before any event. NLog's `FileTarget` opens the file only on the first write, so with logging off the .NET app never creates the file. Observable on disk: an empty `btnet_log.txt` appears in the port when logging is disabled. | Make file creation lazy (e.g. Logback `FileAppender.setLazy(true)` where the Boot-managed Logback version has it). Otherwise mark the site `// TODO(port): NLog FileTarget creates the file on first write; Logback opens it at start()`. |

F4 | LOW | LoggingConfig.java:35,39 | LoggingConfig.cs:15-17 | §2A LoggingConfig row (archive naming left as `TODO(port)`), UNKNOWN rule (§2A) | The archive pattern chosen is `btnet_log.txt.yyyyMMdd` (suffix after the extension). NLog (3.x) with `ArchiveFileName` unset derives the archive pattern by inserting `.{#}` **before** the extension (`Path.ChangeExtension(fileName, ".{#}" + ext)`), and `ArchiveNumbering=Date` fills `{#}` with `yyyyMMdd`. The original therefore archives to `btnet_log.yyyyMMdd.txt`. The marker is present and the rule leaves the question open, so this is not an obedience defect. But the "conservative" choice is the one that differs from the known NLog default. | `rollingPolicy.setFileNamePattern(Path.Combine(Util.get_log_folder(), "btnet_log.%d{yyyyMMdd}.txt"));` (or derive from `fileName` by inserting before the extension). Keep the `TODO(port)` until it is verified against NLog 3.1.0 (`packages.config:27`). |

F5 | LOW | LoggingConfig.java:64 | LoggingConfig.cs:34 | §2A row `log.Fatal` → `error(MarkerFactory.getMarker("FATAL"), …)` (RULEBOOK.md:643) | `LogLevel.Fatal` is mapped to the threshold `Level.ERROR` alone. Per §2A, NLog Fatal is Logback ERROR **plus** the `FATAL` marker. An ERROR threshold without a marker filter admits every ERROR event (Spring/Tomcat errors, non-fatal application errors). NLog's `Fatal` minLevel admits only Fatal. The variable is dead today (mail appender is the sanctioned TODO), so nothing differs at runtime yet. But the value records the wrong semantics for whoever wires the TODO. | Keep the variable, and make the comment at `:65` / the TODO state that the mail rule needs `Level.ERROR` **and** a FATAL-marker filter (e.g. `// emailLogLevel: ERROR + FATAL marker (RULEBOOK §2A Fatal row)`), or name the TODO `TODO(port): FATAL mail appender` with that note in a separate comment (§3 marker format: explanation in a separate comment). |

Not findings (checked, correct):
- Header `// Source: src/BugTracker.Web/App_Start/LoggingConfig.cs` (§6.1). `static class` → `public final class` + `private LoggingConfig() {}` (§2A). Member name `Configure` kept (§4).
- `Path.Combine(Util.get_log_folder(), "btnet_log.txt")` uses runtime `btnet.runtime.io.Path` (§2A `Path.*` row, §3.1).
- String `==` → `Objects.equals` (§2A Strings). Setting keys, defaults (`"1"`, `""`) and the evaluation order of the `get_setting` calls (log folder, ErrorEmailTo, ErrorEmailFrom, LogEnabled, ErrorEmailEnabled) are identical to the source. The values of `To`/`From` are still read even though they are unused (C6 order kept).
- Line pattern `%d{yyyy-MM-dd HH:mm:ss.SSSS}|%level|%logger|%msg%n` is exactly the pinned J-C20 text. Both mandated `TODO(port)` markers are present, and the mail one is verbatim `TODO(port): FATAL mail appender`.
- Source comment `//Turn logging on/off based on the LogEnabled setting` is copied (§6.2).
- Trailer `todos=3` = three `TODO(port)` markers (lines 35, 44, 54) (§6.7). Marker format matches §3.
- Compile-level, by reading: logback-classic is on the compile classpath transitively (runtime → Boot starters, §1 allows SLF4J/Logback). `Logger.ROOT_LOGGER_NAME` resolves through `org.slf4j.Logger`. `TimeBasedRollingPolicy` has its parent set before `start()`. Generic `ILoggingEvent` is fully qualified (allowed; no wildcard import). No checked exceptions.

## Rulebook obedience

Verdict: **OBEDIENT** (0 deviations).
- §2A LoggingConfig row: programmatic Logback in `Configure()`, rolling file `<log folder>/btnet_log.txt`, daily, TRACE/OFF by `LogEnabled`, pinned pattern, both TODO texts. All followed.
- §2A general/strings rows, §4 naming/imports, §6.1/6.2/6.3/6.7: followed.
- The finding-level defects (F1–F3) come from what the rule leaves undecided (which logger, reset semantics, eager open), not from ignoring a rule. F4 is an UNKNOWN-rule choice under a sanctioned TODO. F5 conflicts mildly with the spirit of the §2A Fatal row, but in dead code; I do not count it as a deviation.

## Rulebook gaps

G1 — **Scope of the Logback configuration.** The §2A row says "level TRACE if `LogEnabled=1` else OFF" but not *which* logger. In the port, unlike NLog, framework and application logging share one context. Proposed rule: "`LoggingConfig.Configure()` never calls `LoggerContext.reset()` and never touches the root logger. It configures logger `btnet` (`detachAndStopAllAppenders`, `addAppender(file)`, `setLevel(TRACE|OFF)`, `setAdditive(false)`). Framework logging stays on Boot's defaults." Evidence: NLog `"*"` covers only NLog loggers (`LoggingConfig.cs:32`); the only source logger is `btnet.Global` (`Global.asax.cs:22`); Configure runs twice (`Global.asax.cs:29,101`). Runtime logger `btnet.runtime.Threads` (§3, FATAL handler) falls under `btnet` too, which matches .NET (where the crash goes to the event log, not NLog). The human decides.

G2 — **Atomic swap vs. reset-first** (F2): proposed text: "build appenders first; apply to the logger as the final statements (= `LogManager.Configuration = config`)."

G3 — **Eager file creation** (F3): the rule should state whether creating an empty `btnet_log.txt` with logging OFF is accepted (D-006 style) or must be avoided (`setLazy(true)`, if the Boot-managed Logback version has it).

G4 — **Archive name** (F4): proposed to pin `btnet_log.%d{yyyyMMdd}.txt` (NLog inserts `{#}` before the extension when `ArchiveFileName` is unset), with the `TODO(port)` kept for verification against NLog 3.1.0.

G5 — **Line ending and encoding of the log file:** NLog 3.1 `FileTarget` defaults to `LineEnding=Default` (`Environment.NewLine` = `\r\n` on the original Windows host) and `Encoding.Default` (ANSI). The pinned pattern ends in `%n` (`\n` on Linux), and the Logback encoder defaults to UTF-8. §1 says `Environment.NewLine = "\r\n"` and "text I/O UTF-8". The rule should say whether the log keeps `%n` (the row says "Not judged") or uses `\r\n`.

G6 — **Mail rule level mapping** (F5): when the FATAL mail appender TODO is resolved, the rule should say "threshold ERROR + `FATAL` marker filter (e.g. `OnMarkerEvaluator` / `EvaluatorFilter`)", consistent with the §2A `Fatal` row.

## Counts

HIGH 0 · MEDIUM 1 · LOW 4 — **OBEDIENT**
