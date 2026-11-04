package btnet.runtime.data;

import btnet.runtime.DBNull;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Json.NET's {@code DataTableConverter} (RULEBOOK §2A "DataTable JSON"): a {@link DataTable}
 * is an array of objects, one per row, properties = column names in column order,
 * {@code DBNull} → {@code null}; every other value goes through the configured mapper
 * ({@code LocalDateTime} → {@code "yyyy-MM-ddTHH:mm:ss[.fffffff trimmed]"}, see
 * {@code btnet.runtime.config.JsonConfig}). Registered by {@code JsonConfig}.
 */
public class DataTableSerializer extends StdSerializer<DataTable> {

    public DataTableSerializer() {
        super(DataTable.class);
    }

    @Override
    public void serialize(DataTable value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartArray();
        int n = value.Columns().Count();
        for (DataRow row : value.Rows()) {
            gen.writeStartObject();
            for (int i = 0; i < n; i++) {
                gen.writeName(value.Columns().get(i).ColumnName());
                Object v = row.get(i);
                if (v == null || v == DBNull.Value) {
                    gen.writeNull();
                } else {
                    ctxt.writeValue(gen, v);
                }
            }
            gen.writeEndObject();
        }
        gen.writeEndArray();
    }
}
