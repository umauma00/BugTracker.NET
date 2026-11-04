package btnet.runtime.web;

import btnet.runtime.Convert;
import btnet.runtime.NameValueCollection;
import btnet.runtime.config.JsonConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Web API {@code [FromBody] T} binding (RULEBOOK §2A JSON / Web API):
 * {@code BugFromEmail bugFromEmail = ApiBody.bind(Request, BugFromEmail.class);}.
 *
 * <ul>
 * <li>JSON body ({@code Content-Type} containing {@code json}): {@code JsonConfig.mapper()}
 * (case-insensitive property names); malformed JSON → a model error under the parameter
 * name and {@code null}, as Json.NET's formatter.</li>
 * <li>{@code application/x-www-form-urlencoded}: each form key sets the public field of the
 * same name, case-insensitively (the service posts camelCase {@code fromAddress}); values
 * converted per the field type; a conversion failure is a model error.</li>
 * <li>Empty body → {@code null}. Another media type with a body →
 * {@link HttpResponseException} 415.</li>
 * <li>Then validation: each {@link Required} field that is {@code null} (or a blank string)
 * adds {@code "The <Field> field is required."} under {@code "<param>.<Field>"}.</li>
 * </ul>
 * The parameter name defaults to the type's simple name with a lower-case first letter
 * ({@code BugFromEmail} → {@code bugFromEmail}, the source's naming).
 */
public final class ApiBody {
    private ApiBody() {
    }

    public static <T> T bind(HttpRequest request, Class<T> type) {
        String n = type.getSimpleName();
        String param = n.isEmpty() ? "value" : Character.toLowerCase(n.charAt(0)) + n.substring(1);
        return bind(request, type, param);
    }

    public static <T> T bind(HttpRequest request, Class<T> type, String parameterName) {
        ModelStateDictionary ms = ModelStateDictionary.forCurrentRequest();
        byte[] body = request.bodyBytes();
        String ct = request.ContentType().toLowerCase(Locale.ROOT);
        T value;
        if (ct.startsWith("application/x-www-form-urlencoded")) {
            NameValueCollection form = request.Form();
            if (form.AllKeys().length == 0) {
                value = null;
            } else {
                value = newInstance(type);
                for (String key : form.AllKeys()) {
                    if (key == null) continue;
                    Field f = field(type, key);
                    if (f == null) continue;
                    String raw = form.get(key);
                    try {
                        f.set(value, convert(raw, f.getType()));
                    } catch (RuntimeException | IllegalAccessException e) {
                        ms.AddModelError(parameterName + "." + f.getName(),
                            "The value '" + raw + "' is not valid for " + f.getName() + ".");
                    }
                }
            }
        } else if (body.length == 0 || new String(body, StandardCharsets.UTF_8).isBlank()) {
            value = null;
        } else if (ct.contains("json")) {
            try {
                value = JsonConfig.mapper().readValue(new String(body, StandardCharsets.UTF_8), type);
            } catch (RuntimeException e) {
                ms.AddModelError(parameterName, e.getMessage());
                value = null;
            }
        } else {
            throw new HttpResponseException(415);
        }
        if (value != null) validate(value, parameterName, ms);
        return value;
    }

    /** Runs the {@link Required} checks of {@code value}'s public fields. */
    static void validate(Object value, String prefix, ModelStateDictionary ms) {
        for (Field f : publicFields(value.getClass())) {
            Required r = f.getAnnotation(Required.class);
            if (r == null) continue;
            Object v;
            try {
                v = f.get(value);
            } catch (IllegalAccessException e) {
                continue;
            }
            boolean missing = v == null || (v instanceof String s && !r.AllowEmptyStrings() && s.trim().isEmpty());
            if (missing) {
                String msg = r.ErrorMessage().isEmpty() ? "The " + f.getName() + " field is required." : r.ErrorMessage();
                ms.AddModelError(prefix + "." + f.getName(), msg);
            }
        }
    }

    static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create " + type.getName(), e);
        }
    }

    static List<Field> publicFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Field f : type.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers())) out.add(f);
        }
        return out;
    }

    /** The public instance field named {@code name}, case-insensitively, or {@code null}. */
    static Field field(Class<?> type, String name) {
        for (Field f : publicFields(type)) {
            if (f.getName().equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    /**
     * Web API simple-type conversion of a posted string: {@code String} as is; nullable
     * types: empty → {@code null}; {@code int}/{@code Integer}, {@code long}, {@code boolean},
     * {@code double}, {@code BigDecimal}, {@code LocalDateTime} via the runtime {@code Convert};
     * {@code byte[]} from base64. Failure → a {@code RuntimeException}.
     */
    static Object convert(String raw, Class<?> t) {
        if (t == String.class) return raw;
        boolean primitive = t.isPrimitive();
        if (raw == null || raw.trim().isEmpty()) {
            if (primitive) throw new btnet.runtime.FormatException("empty value for a value type");
            return null;
        }
        String s = raw.trim();
        if (t == int.class || t == Integer.class) return Convert.ToInt32(s);
        if (t == long.class || t == Long.class) return Convert.ToInt64(s);
        if (t == boolean.class || t == Boolean.class) return Convert.ToBoolean(s);
        if (t == double.class || t == Double.class) return Double.parseDouble(s);
        if (t == short.class || t == Short.class) return Short.parseShort(s);
        if (t == BigDecimal.class) return Convert.ToDecimal(s);
        if (t == LocalDateTime.class) return Convert.ToDateTime(s);
        if (t == byte[].class) return Base64.getDecoder().decode(s);
        if (t.isEnum()) {
            for (Object c : t.getEnumConstants()) {
                if (((Enum<?>) c).name().equalsIgnoreCase(s)) return c;
            }
            throw new btnet.runtime.FormatException("not a member of " + t.getSimpleName());
        }
        throw new UnsupportedOperationException("TODO(port) ApiBody conversion to " + t.getName());
    }
}
