package btnet.runtime;

import java.time.LocalDateTime;

/** Stands for .NET {@code System.Timers.ElapsedEventArgs} (RULEBOOK §3 S1-20). */
public class ElapsedEventArgs extends EventArgs {
    private final LocalDateTime signalTime;

    public ElapsedEventArgs(LocalDateTime signalTime) {
        this.signalTime = signalTime;
    }

    /** .NET {@code SignalTime}. */
    public LocalDateTime SignalTime() {
        return signalTime;
    }
}
