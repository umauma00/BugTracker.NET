// Source: src/BugTracker.Web/App_Start/LoggingConfig.cs
package btnet.App_Start;

import java.util.Objects;

import org.slf4j.LoggerFactory;

import btnet.Util;
import btnet.runtime.io.Path;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;

public final class LoggingConfig {
    private LoggingConfig() {}

    public static void Configure() {
        // NLog LoggingConfiguration -> programmatic Logback (RULEBOOK §2A Logging row)
        LoggerContext config = (LoggerContext) LoggerFactory.getILoggerFactory();

        // NLog FileTarget -> Logback RollingFileAppender (daily, date-numbered archives)
        RollingFileAppender<ch.qos.logback.classic.spi.ILoggingEvent> fileTarget = new RollingFileAppender<>();
        fileTarget.setContext(config);
        fileTarget.setName("File");

        String logFolder = Util.get_log_folder();
        String fileName = Path.Combine(logFolder, "btnet_log.txt");
        fileTarget.setFile(fileName);

        // ArchiveNumbering = Date, ArchiveEvery = Day
        // TODO(port): archive naming per NLog ArchiveNumbering=Date
        // pinned to NLog 3.x default <name>.{#}.<ext> (ArchiveFileName unset); verify against NLog 3.1.0
        TimeBasedRollingPolicy<ch.qos.logback.classic.spi.ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(config);
        rollingPolicy.setParent(fileTarget);
        rollingPolicy.setFileNamePattern(Path.Combine(logFolder, "btnet_log.%d{yyyyMMdd}.txt"));
        rollingPolicy.start();
        fileTarget.setRollingPolicy(rollingPolicy);

        // Line pattern pinned to NLog's default layout (RULEBOOK §2A Logging row, J-C20)
        // TODO(port): exact 4-digit fraction of NLog longdate
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(config);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSSS}|%level|%logger|%msg%n");
        encoder.start();
        fileTarget.setEncoder(encoder);
        fileTarget.start();

        // NLog MailTarget (UseSystemNetMailSettings = true, Subject = "BTNET Error Notification",
        // Layout = "${machinename}${newline} ${date} ${newline} ${newline} ${message} ${newline}  ${exception} ${newline}")
        // TODO(port): FATAL mail appender
        String mailTarget_To = Util.get_setting("ErrorEmailTo", "");
        String mailTarget_From = Util.get_setting("ErrorEmailFrom", "");

        //Turn logging on/off based on the LogEnabled setting
        var logLevel = Objects.equals(Util.get_setting("LogEnabled", "1"), "1") ? Level.TRACE : Level.OFF;

        var emailLogLevel = Objects.equals(Util.get_setting("ErrorEmailEnabled", "1"), "1") ? Level.ERROR : Level.OFF;
        // LogLevel.Fatal = Level.ERROR + marker MarkerFactory.getMarker("FATAL") (RULEBOOK §2A Fatal row); the FATAL mail appender must filter on that marker
        // mail rule ("*", emailLogLevel, mailTarget): no appender attached, see TODO(port) above

        // LogManager.Configuration = config
        Logger app = config.getLogger("btnet");
        app.detachAndStopAllAppenders();
        app.addAppender(fileTarget);
        app.setLevel(logLevel);
        app.setAdditive(false);
    }
}
// PORT STATUS: confidence=medium todos=3
