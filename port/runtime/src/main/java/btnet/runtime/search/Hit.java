package btnet.runtime.search;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stands for NEST 1.0.2 {@code IHit<object>} [C29, N1]: {@code hits.hits[i]} of a
 * search response. Every member is a public field; {@code Highlights} is a
 * {@code LinkedHashMap} keyed by field name in the JSON order of
 * {@code hits.hits[i].highlight}, empty (never null) when the hit has no highlight
 * (NEST {@code HighlightFieldDictionary}).
 */
public class Hit {
    /** {@code _id}. */
    public String Id;
    /** {@code _score} (0 when absent or null). */
    public double Score;
    /** {@code _source} as a JSON object map (insertion order), {@code null} if absent. */
    public Map<String, Object> Source;
    /** {@code highlight}, see the class comment. */
    public Map<String, Highlight> Highlights = new LinkedHashMap<>();
}
