package btnet.runtime.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ASP.NET Web API {@code System.Web.Http.ModelBinding.ModelStateDictionary}
 * ({@code ApiController.ModelState}; RULEBOOK §2A JSON / Web API). Errors per key in
 * insertion order; {@link #IsValid()} = no errors. {@link #current()} is a proxy over the
 * current request's dictionary (kept in {@code HttpContext.Items}), which
 * {@link ApiBody#bind} and {@link FromUri} fill.
 */
public class ModelStateDictionary {
    private static final Object KEY = new Object();
    private static final ModelStateDictionary PROXY = new ModelStateDictionary(true);

    private final boolean proxy;
    private final Map<String, List<String>> errors = new LinkedHashMap<>();

    public ModelStateDictionary() {
        this(false);
    }

    private ModelStateDictionary(boolean proxy) {
        this.proxy = proxy;
    }

    /** The current request's dictionary (proxy). */
    public static ModelStateDictionary current() {
        return PROXY;
    }

    /** The current request's real dictionary (created on first use). */
    static ModelStateDictionary forCurrentRequest() {
        HttpContext c = HttpContext.Current();
        if (c == null) throw new HttpException("ModelState is not available in this context.");
        Object o = c.Items().get(KEY);
        if (o instanceof ModelStateDictionary m) return m;
        ModelStateDictionary m = new ModelStateDictionary(false);
        c.Items().put(KEY, m);
        return m;
    }

    private ModelStateDictionary t() {
        return proxy ? forCurrentRequest() : this;
    }

    public boolean IsValid() {
        return t().errors.values().stream().allMatch(List::isEmpty);
    }

    public void AddModelError(String key, String errorMessage) {
        t().errors.computeIfAbsent(key, k -> new ArrayList<>()).add(errorMessage == null ? "" : errorMessage);
    }

    public boolean ContainsKey(String key) {
        return t().errors.containsKey(key);
    }

    public int Count() {
        return t().errors.size();
    }

    public Set<String> Keys() {
        return Collections.unmodifiableSet(t().errors.keySet());
    }

    /** The error messages recorded for {@code key} (empty if none). */
    public List<String> get(String key) {
        List<String> l = t().errors.get(key);
        return l == null ? List.of() : Collections.unmodifiableList(l);
    }

    public void Clear() {
        t().errors.clear();
    }

    /** Web API's 400 body: {@code {"Message":"The request is invalid.","ModelState":{key:[messages]}}}. */
    public Map<String, Object> toErrorBody() {
        Map<String, Object> ms = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : t().errors.entrySet()) {
            if (!e.getValue().isEmpty()) ms.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Message", "The request is invalid.");
        body.put("ModelState", ms);
        return body;
    }
}
