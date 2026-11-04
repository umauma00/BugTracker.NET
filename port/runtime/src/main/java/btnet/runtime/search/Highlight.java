package btnet.runtime.search;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands for NEST 1.0.2 {@code Nest.Highlight} [N1]: one highlighted field of a
 * hit. Every member is a public field. {@code Highlights} is never null.
 */
public class Highlight {
    /** The hit's {@code _id}. */
    public String DocumentId;
    /** The field name (key of {@code hits.hits[i].highlight}). */
    public String Field;
    /** The fragments, in response order. */
    public List<String> Highlights = new ArrayList<>();
}
