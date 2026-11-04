package btnet.runtime.search;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands for NEST 1.0.2 {@code ISearchResponse<object>} [C29]: {@code Hits} =
 * {@code hits.hits[]} in response order (empty, never null). {@code IsValid} is
 * false when the server answered with a non-2xx status (NEST then returns an
 * invalid response with no hits instead of throwing).
 */
public class SearchResponse {
    public List<Hit> Hits = new ArrayList<>();
    /** {@code hits.total} (0 if absent). */
    public long Total;
    /** NEST {@code IsValid}. */
    public boolean IsValid = true;
    /** HTTP status of the response. */
    public int StatusCode;
}
