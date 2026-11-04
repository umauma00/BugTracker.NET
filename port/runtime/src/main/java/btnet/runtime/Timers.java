package btnet.runtime;

import java.util.TimerTask;

/** {@code System.Timers.Timer} semantics on top of {@code java.util.Timer} (RULEBOOK §2A C17, §3 S1-14). */
public final class Timers {
    private Timers() {
    }

    /**
     * .NET {@code timer.Interval = value}: {@code value <= 0} → ArgumentException("'v' is not a valid value for
     * 'Interval'. 'Interval' must be greater than 0."); else {@code ceil(value)} milliseconds.
     */
    public static long Interval(double value) {
        if (value <= 0 || Double.isNaN(value)) {
            throw new ArgumentException("'" + Convert.ToString(value)
                    + "' is not a valid value for 'Interval'. 'Interval' must be greater than 0.");
        }
        return (long) Math.ceil(value);
    }

    /** A TimerTask that swallows every exception of the handler, as System.Timers.Timer does for Elapsed. */
    public static TimerTask task(Runnable r) {
        return new TimerTask() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    // System.Timers.Timer (.NET 2.0+) suppresses Elapsed handler exceptions
                }
            }
        };
    }
}
