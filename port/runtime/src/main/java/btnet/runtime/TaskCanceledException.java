package btnet.runtime;

/** Stands for .NET {@code System.Threading.Tasks.TaskCanceledException}. Unchecked (RULEBOOK §2A Exceptions). */
public class TaskCanceledException extends DotNetException {
    private static final long serialVersionUID = 1L;

    public TaskCanceledException() {
        super("A task was canceled.");
    }

    public TaskCanceledException(String message) {
        super(message);
    }

    public TaskCanceledException(String message, Throwable innerException) {
        super(message, innerException);
    }
}
