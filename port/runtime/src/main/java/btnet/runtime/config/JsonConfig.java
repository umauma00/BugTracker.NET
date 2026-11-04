package btnet.runtime.config;

import btnet.runtime.CultureInfo;
import btnet.runtime.DBNull;
import btnet.runtime.DateTimes;
import btnet.runtime.data.DataTable;
import btnet.runtime.data.DataTableSerializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * The one configured Jackson 3 mapper, standing for Newtonsoft.Json 6.0.4's defaults
 * (RULEBOOK §2A JSON, §3 [S1-12]). {@code JsonConvert.SerializeObject(x)} →
 * {@code JsonConfig.mapper().writeValueAsString(x)}; Web API uses the same mapper (exposed as
 * the Spring {@code JsonMapper} bean by {@link BtnetApplication}).
 * <ul>
 * <li>public fields = C# auto-properties, names as declared (PascalCase), declaration order;
 *   getters are not auto-detected (computed properties that Newtonsoft emits carry
 *   {@code @JsonProperty("Name")});</li>
 * <li>case-insensitive property binding; unknown JSON properties ignored;</li>
 * <li>{@code DBNull} → {@code null}; {@link DataTable} → {@link DataTableSerializer} (also as a
 *   nested {@code Map} value); {@code LocalDateTime} →
 *   {@code "yyyy-MM-ddTHH:mm:ss[.fffffff trimmed]"} (Json.NET {@code DateTimeKind.Unspecified});
 *   {@code byte[]} as base64; a {@code Map} keeps its insertion order.</li>
 * </ul>
 */
public final class JsonConfig {
    private JsonConfig() {}

    private static final JsonMapper MAPPER = build();

    /** The configured mapper ({@code writeValueAsString} throws only unchecked {@code JacksonException}). */
    public static JsonMapper mapper() {
        return MAPPER;
    }

    private static JsonMapper build() {
        SimpleModule module = new SimpleModule("btnet-dotnet");
        module.addSerializer(DBNull.class, new DBNullSerializer());
        module.addSerializer(DataTable.class, new DataTableSerializer());
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());
        return JsonMapper.builder()
                .addModule(module)
                .changeDefaultVisibility(vc -> vc
                        .withVisibility(PropertyAccessor.GETTER, Visibility.NONE)
                        .withVisibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
                        .withVisibility(PropertyAccessor.SETTER, Visibility.NONE)
                        .withVisibility(PropertyAccessor.FIELD, Visibility.PUBLIC_ONLY))
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /** Json.NET date text for {@code DateTimeKind.Unspecified}. */
    public static String formatDateTime(LocalDateTime v) {
        StringBuilder sb = new StringBuilder(27);
        pad(sb, v.getYear(), 4).append('-');
        pad(sb, v.getMonthValue(), 2).append('-');
        pad(sb, v.getDayOfMonth(), 2).append('T');
        pad(sb, v.getHour(), 2).append(':');
        pad(sb, v.getMinute(), 2).append(':');
        pad(sb, v.getSecond(), 2);
        int ticks = v.getNano() / 100;          // .NET keeps 7 fractional digits
        if (ticks != 0) {
            String f = String.valueOf(10_000_000 + ticks).substring(1);
            int end = f.length();
            while (end > 0 && f.charAt(end - 1) == '0') end--;
            sb.append('.').append(f, 0, end);
        }
        return sb.toString();
    }

    private static StringBuilder pad(StringBuilder sb, int value, int width) {
        String s = String.valueOf(value);
        for (int i = s.length(); i < width; i++) sb.append('0');
        return sb.append(s);
    }

    static final class DBNullSerializer extends StdSerializer<DBNull> {
        DBNullSerializer() {
            super(DBNull.class);
        }

        @Override
        public void serialize(DBNull value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeNull();
        }
    }

    static final class LocalDateTimeSerializer extends StdSerializer<LocalDateTime> {
        LocalDateTimeSerializer() {
            super(LocalDateTime.class);
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(formatDateTime(value));
        }
    }

    static final class LocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {
        LocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
            String s = p.getValueAsString();
            if (s == null || s.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(s.endsWith("Z") ? s.substring(0, s.length() - 1) : s);
            } catch (DateTimeParseException e) {
                return DateTimes.Parse(s, CultureInfo.InvariantCulture);
            }
        }
    }
}
