package btnet.runtime.config;

import btnet.runtime.data.DataTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigTest {

    @Test
    void readsOnlyUncommentedAppSettingsWithRemoveAndOverride() throws Exception {
        String xml = "<?xml version=\"1.0\"?><configuration><appSettings>"
                + "<add key=\"A\" value=\"1\"/><!-- <add key=\"B\" value=\"2\"/> -->"
                + "<add key=\"AutoReplyText\" value=\"x&#xA;y\"/><add key=\"a\" value=\"3\"/>"
                + "<add key=\"C\" value=\"c\"/><remove key=\"c\"/>"
                + "</appSettings><location><appSettings><add key=\"Z\" value=\"z\"/></appSettings></location></configuration>";
        Map<String, String> m = WebConfigToProperties.readAppSettings(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        // NameValueSectionHandler: a repeated key (case-insensitive) keeps the last value
        assertEquals(List.of("AutoReplyText", "a"), List.copyOf(m.keySet()));
        assertEquals("x\ny", m.get("AutoReplyText"));
    }

    @Test
    void propertiesRoundTripKeepsValuesAndOrder() throws Exception {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("SubversionAdditionalArgs", " --config-dir c:\\cit\\mysvn_config");
        in.put("AutoReplyText", "Thanks.\nDon't call us = ok # é");
        in.put("ConnectionString", "server=(local);database=btnet");
        Map<String, String> out = new LinkedHashMap<>();
        ConfigurationManager.parseInto(new StringReader(WebConfigToProperties.render(in, "Web.config")), out);
        assertEquals(in, out);
        assertEquals(List.copyOf(in.keySet()), List.copyOf(out.keySet()));
    }

    @Test
    void jsonDatesAndDataTables() throws Exception {
        // Json.NET DateTimeKind.Unspecified: "yyyy-MM-ddTHH:mm:ss" + up to 7 trimmed fraction digits
        assertEquals("2026-11-03T10:20:30", JsonConfig.formatDateTime(LocalDateTime.of(2026, 11, 3, 10, 20, 30)));
        assertEquals("2026-11-03T10:20:30.5", JsonConfig.formatDateTime(LocalDateTime.of(2026, 11, 3, 10, 20, 30, 500_000_000)));
        assertEquals("2026-11-03T10:20:30.1234567",
                JsonConfig.formatDateTime(LocalDateTime.of(2026, 11, 3, 10, 20, 30, 123_456_789)));
        DataTable t = new DataTable();
        t.Columns().Add("id", Integer.class);
        t.Columns().Add("desc", String.class);
        t.Columns().Add("d", LocalDateTime.class);
        t.Rows().Add(1, null, LocalDateTime.of(2020, 1, 2, 3, 4, 5));
        // DataTableConverter: array of row objects in column order, DBNull → null
        assertEquals("[{\"id\":1,\"desc\":null,\"d\":\"2020-01-02T03:04:05\"}]",
                JsonConfig.mapper().writeValueAsString(t));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordsTotal", 1);
        m.put("data", t);
        assertEquals("{\"recordsTotal\":1,\"data\":[{\"id\":1,\"desc\":null,\"d\":\"2020-01-02T03:04:05\"}]}",
                JsonConfig.mapper().writeValueAsString(m));
    }

    public static class Pojo {
        public int Zeta = 1;
        public String Alpha = "a";
        private String hidden = "h";
    }

    @Test
    void publicFieldsInDeclarationOrder() throws Exception {
        // Newtonsoft: public members, declaration order (Jackson 3 sorts alphabetically by default)
        assertEquals("{\"Zeta\":1,\"Alpha\":\"a\"}", JsonConfig.mapper().writeValueAsString(new Pojo()));
        Pojo p = JsonConfig.mapper().readValue("{\"zeta\":5,\"ALPHA\":\"b\",\"extra\":0}", Pojo.class);
        assertEquals(5, p.Zeta);
        assertEquals("b", p.Alpha);
    }
}
