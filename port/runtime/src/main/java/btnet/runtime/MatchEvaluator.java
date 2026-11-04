package btnet.runtime;

/** Stands for the .NET delegate {@code System.Text.RegularExpressions.MatchEvaluator}. */
@FunctionalInterface
public interface MatchEvaluator {
    String Invoke(Match match);
}
