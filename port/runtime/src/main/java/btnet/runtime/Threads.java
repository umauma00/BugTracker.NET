package btnet.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * .NET threading constructs (RULEBOOK §1 Async, §2A "Threads, timers, async", §3 C2/C19/C22/C33/S1-2):
 * {@code new Thread(p).Start()}, {@code Task.Run}, {@code async void}, {@code Thread.Sleep}. All threads are
 * non-daemon platform threads (.NET threads are foreground).
 */
public final class Threads {
    private Threads() {
    }

    private static final Logger log = LoggerFactory.getLogger("btnet.runtime.Threads");
    private static final Marker FATAL = MarkerFactory.getMarker("FATAL");

    /**
     * The .NET unhandled-exception policy [S1-2, D40]: writes {@code "Unhandled Exception: " + e.ToString() + CRLF}
     * to stderr, logs it at error with marker FATAL, then {@code Runtime.halt(1)} (no shutdown hooks, like a
     * .NET process crash). A ResponseEndException (ThreadAbortException analogue) only ends the thread, as an
     * unhandled ThreadAbortException does in .NET.
     */
    static final Thread.UncaughtExceptionHandler DOTNET_HANDLER = (thread, t) -> {
        if (t instanceof btnet.runtime.web.ResponseEndException) {
            return;
        }
        String text = "Unhandled Exception: " + Exceptions.dotNetToString(t) + "\r\n";
        try {
            System.err.print(text);
            System.err.flush();
            log.error(FATAL, text);
        } finally {
            Runtime.getRuntime().halt(1);
        }
    };

    /** .NET {@code Thread.Sleep(ms)}: on interrupt re-sets the flag and returns early (C19). */
    public static void Sleep(long millisecondsTimeout) {
        if (millisecondsTimeout < -1) {
            throw new ArgumentOutOfRangeException("millisecondsTimeout", "Number must be either non-negative and less than or equal to Int32.MaxValue or -1.");
        }
        try {
            if (millisecondsTimeout == -1) {
                Thread.sleep(Long.MAX_VALUE);
            } else {
                Thread.sleep(millisecondsTimeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** .NET {@code new Thread(r)}: non-daemon, .NET unhandled-exception handler, not started. */
    public static Thread create(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(false);
        t.setUncaughtExceptionHandler(DOTNET_HANDLER);
        return t;
    }

    /** .NET {@code new Thread(r).Start()}: {@link #create} then {@code start()}; returns the thread. */
    public static Thread start(Runnable r) {
        Thread t = create(r);
        t.start();
        return t;
    }

    /** .NET 4.5 {@code Task.Run(() => x())}: like {@link #start}, but an escaping exception is dropped (logged at debug). */
    public static Thread startTask(Runnable r) {
        return start(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                log.debug("Unobserved task exception: {}", Exceptions.dotNetToString(t));
            }
        });
    }

    /**
     * .NET {@code async void} [C33, J-C1, S1-1]: runs {@code body} on a new non-daemon thread with the .NET
     * unhandled-exception handler; the caller blocks until the body calls {@code firstAwait.run()} (idempotent;
     * also run in a {@code finally} when the body ends or throws). An interrupted caller re-sets its flag and
     * returns.
     */
    public static void runAsync(Consumer<Runnable> body) {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable firstAwait = latch::countDown;
        start(() -> {
            try {
                body.accept(firstAwait);
            } finally {
                firstAwait.run();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
