# Runtime authoring notes (cross-author contract issues, for the compile loop)

## From data+config author (2026-11-04)
- Not compiled yet (Maven denied in-loop; build daemon to run). Jackson 3 (`tools.jackson`) / Spring 7 calls written from memory.
- `WebConfigToProperties` must be run once by the orchestrator (via build daemon / exec) to generate the settings properties.
- Deviations from RULEBOOK text, following .NET instead: DataView.Sort bad direction → IndexOutOfRangeException (whole entry read as column name), unbalanced `[` → ArgumentException; JSON serializes public fields only and keeps declaration order; Web.config has 85 active `<add>` (rulebook said 156); SqlParameter re-add to another command throws like SqlClient.
- For mail author: `ConfigurationManager.GetSection("system.net/mailSettings/smtp")` returns null → email.cs:215 NRE unless mail runtime covers it.
- For web author: `BtnetApplication` registers `RequestContextFilter` itself (don't also @Component it); assumes no-arg ctors for `LowercaseResourceResolver`, `BtnetDialect`, `RequestContextFilter`; `Application_OnStart` must be invoked by the web runtime's `HttpApplication`.
- Invented nested types: DbContext.Database.SetInitializer, EntityTypeConfiguration.DatabaseGeneratedOption, PropertyConfiguration, DbModelBuilder.ConfigurationRegistrar, SqlDataReader.SqlNullValueException, DataView.SyntaxErrorException/EvaluateException.

## From security/mail/net/search author (2026-11-04)
- 47 classes + 10 test classes; not compiled. Admin hash acceptance vector (setup.sql:168, salt PmafvZSWFYML3, password admin) reproduced exactly in Python; Rfc2898DeriveBytesTest asserts it.
- Elasticsearch.Net 1.0.2 failure semantics (from its Transport.cs): connection failure throws; non-2xx → Search returns invalid response with no hits, Index returns false.
- SecurityConfig sets HttpRequest.OwinContextFactory and HttpContext.PrincipalResolver hooks.
- Invented: anonymous principal Name() = ""; unauthenticated API: non-AJAX 302 to default.aspx?ReturnUrl=…, AJAX 401 + Web API JSON body (X-Responded-JSON TODO); MailMessage collections; SmtpClient nested SmtpException/SmtpDeliveryMethod/MailSettings reading btnet.mail.*; Uri.EscapeDataString 65,520-char limit (TODO verify); Pop3Client.close() sends QUIT (TODO verify).
