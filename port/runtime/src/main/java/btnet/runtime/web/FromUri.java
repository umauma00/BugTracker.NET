package btnet.runtime.web;

import btnet.runtime.NameValueCollection;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web API URI binding (RULEBOOK §2A JSON / Web API).
 *
 * <ul>
 * <li>{@link #bind(HttpRequest, String, Class)}: a simple action parameter from the query
 * string, name case-insensitive. Absent → {@link HttpResponseException} 404 (Web API selects
 * no action; {@code TODO(port)} verify); an unconvertible value → a model error and the
 * type's default ({@code 0}/{@code false}/{@code null}).</li>
 * <li>{@link #bindArray(HttpRequest, String, Class)}: {@code [FromUri] T[] filters} from
 * {@code filters[i][Prop]} (jQuery) or {@code filters[i].Prop}, {@code i} = 0, 1, … until the
 * first missing index; absent → empty array ({@code TODO(port)}: verify Web API 5.2.3 gives
 * empty vs null).</li>
 * </ul>
 */
public final class FromUri {
    private FromUri() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T bind(HttpRequest request, String name, Class<T> type) {
        String raw = request.QueryString().get(name);
        if (raw == null) throw new HttpResponseException(404);
        try {
            return (T) ApiBody.convert(raw, type);
        } catch (RuntimeException e) {
            ModelStateDictionary.forCurrentRequest().AddModelError(name, "The value '" + raw + "' is not valid for " + name + ".");
            return (T) defaultOf(type);
        }
    }

    /** Like {@link #bind}, but an absent parameter gives {@code defaultValue} (an optional parameter). */
    @SuppressWarnings("unchecked")
    public static <T> T bindOptional(HttpRequest request, String name, Class<T> type, T defaultValue) {
        String raw = request.QueryString().get(name);
        if (raw == null) return defaultValue;
        try {
            return (T) ApiBody.convert(raw, type);
        } catch (RuntimeException e) {
            ModelStateDictionary.forCurrentRequest().AddModelError(name, "The value '" + raw + "' is not valid for " + name + ".");
            return (T) defaultOf(type);
        }
    }

    private static Object defaultOf(Class<?> t) {
        if (t == int.class || t == Integer.class) return 0;
        if (t == long.class || t == Long.class) return 0L;
        if (t == boolean.class || t == Boolean.class) return false;
        if (t == double.class || t == Double.class) return 0.0;
        if (t == short.class || t == Short.class) return (short) 0;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] bindArray(HttpRequest request, String name, Class<T> elementType) {
        NameValueCollection qs = request.QueryString();
        Map<Integer, Map<String, String>> byIndex = new LinkedHashMap<>();
        String prefix = name.toLowerCase(java.util.Locale.ROOT) + "[";
        for (String key : qs.AllKeys()) {
            if (key == null) continue;
            String k = key.toLowerCase(java.util.Locale.ROOT);
            if (!k.startsWith(prefix)) continue;
            int close = key.indexOf(']', prefix.length());
            if (close < 0) continue;
            int index;
            try {
                index = Integer.parseInt(key.substring(prefix.length(), close).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String rest = key.substring(close + 1);
            String prop;
            if (rest.startsWith("[") && rest.endsWith("]")) prop = rest.substring(1, rest.length() - 1);
            else if (rest.startsWith(".")) prop = rest.substring(1);
            else continue;
            byIndex.computeIfAbsent(index, i -> new LinkedHashMap<>()).put(prop, qs.get(key));
        }
        int n = 0;
        while (byIndex.containsKey(n)) n++;
        T[] out = (T[]) Array.newInstance(elementType, n);
        for (int i = 0; i < n; i++) {
            T item = ApiBody.newInstance(elementType);
            for (Map.Entry<String, String> e : byIndex.get(i).entrySet()) {
                Field f = ApiBody.field(elementType, e.getKey());
                if (f == null) continue;
                try {
                    f.set(item, ApiBody.convert(e.getValue(), f.getType()));
                } catch (RuntimeException | IllegalAccessException ex) {
                    ModelStateDictionary.forCurrentRequest().AddModelError(
                        name + "[" + i + "]." + f.getName(), "The value '" + e.getValue() + "' is not valid for " + f.getName() + ".");
                }
            }
            out[i] = item;
        }
        return out;
    }
}
