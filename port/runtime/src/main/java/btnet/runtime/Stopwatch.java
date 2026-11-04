package btnet.runtime;

import java.time.Duration;

/** Stands for .NET {@code System.Diagnostics.Stopwatch} over {@link System#nanoTime()}. */
public final class Stopwatch {
    private long elapsedNanos;
    private long startedAt;
    private boolean running;

    public Stopwatch() {
    }

    /** .NET {@code Stopwatch.StartNew()}. */
    public static Stopwatch StartNew() {
        Stopwatch s = new Stopwatch();
        s.Start();
        return s;
    }

    public synchronized void Start() {
        if (!running) {
            startedAt = System.nanoTime();
            running = true;
        }
    }

    public synchronized void Stop() {
        if (running) {
            elapsedNanos += System.nanoTime() - startedAt;
            running = false;
        }
    }

    public synchronized void Reset() {
        elapsedNanos = 0;
        running = false;
    }

    public synchronized void Restart() {
        elapsedNanos = 0;
        startedAt = System.nanoTime();
        running = true;
    }

    public synchronized boolean IsRunning() {
        return running;
    }

    private synchronized long nanos() {
        return running ? elapsedNanos + (System.nanoTime() - startedAt) : elapsedNanos;
    }

    /** .NET {@code ElapsedMilliseconds}. */
    public long ElapsedMilliseconds() {
        return nanos() / 1_000_000L;
    }

    /** .NET {@code Elapsed} (TimeSpan → Duration). */
    public Duration Elapsed() {
        return Duration.ofNanos(nanos());
    }

    /** .NET {@code ElapsedTicks} with {@link #Frequency} = 10 000 000 (100 ns ticks). */
    public long ElapsedTicks() {
        return nanos() / 100;
    }

    /** .NET {@code Stopwatch.Frequency}. */
    public static final long Frequency = 10_000_000L;
}
