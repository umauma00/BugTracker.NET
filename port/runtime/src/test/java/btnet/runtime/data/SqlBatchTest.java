package btnet.runtime.data;

import btnet.runtime.ArgumentException;
import btnet.runtime.DBNull;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlBatchTest {

    @Test
    void declarePrefixFollowsSqlClientInference() {
        // RULEBOOK §2A: String → nvarchar(4000) (max if longer), Integer → int, BigDecimal → decimal(38,s),
        // LocalDateTime → datetime, DBNull → nvarchar(4000); the SQL text itself is untouched.
        List<SqlParameter> ps = List.of(
                new SqlParameter("@a", "x"),
                new SqlParameter("b", 5),
                new SqlParameter("@c", new BigDecimal("1.50")),
                new SqlParameter("@d", LocalDateTime.of(2020, 1, 2, 3, 4, 5)),
                new SqlParameter("@e", DBNull.Value),
                new SqlParameter("@f", "y".repeat(4001)));
        assertEquals("DECLARE @a nvarchar(4000) = ?, @b int = ?, @c decimal(38,2) = ?, @d datetime = ?, "
                + "@e nvarchar(4000) = ?, @f nvarchar(max) = ?;\nselect @a", SqlBatch.commandText("select @a", ps));
        assertEquals("select 1", SqlBatch.commandText("select 1", List.of()));
    }

    @Test
    void imageParameterDeclaredAsVarbinaryMax() {
        // bug.cs:368 SqlDbType.Image — image is invalid for a DECLAREd variable
        SqlParameter p = new SqlParameter("@bc", SqlDbType.Image);
        p.Value(new byte[] {1});
        assertEquals("varbinary(max)", SqlBatch.declaredType(p));
    }

    @Test
    void parameterBelongsToOneCollection() {
        // SqlParameterCollection: "The SqlParameter is already contained by another SqlParameterCollection."
        SqlParameter p = new SqlParameter("@x", 1);
        new SqlCommand("select @x").Parameters().Add(p);
        SqlCommand other = new SqlCommand("select @x");
        ArgumentException e = assertThrows(ArgumentException.class, () -> other.Parameters().Add(p));
        assertEquals("The SqlParameter is already contained by another SqlParameterCollection.", e.getMessage());
    }

    @Test
    void connectionStringToJdbcUrl() {
        // RULEBOOK §3 [S1-9]: (local) → localhost, encrypt=false and loginTimeout=15 by default
        assertEquals("jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=btnet;user=sa;password=p;w;encrypt=false;loginTimeout=15"
                        .replace("password=p;w", "password={p;w}"),
                SqlConnection.toJdbcUrl("server=(local)\\SQLEXPRESS;database=btnet;user id=sa;password='p;w';Pooling=true"));
        assertEquals("jdbc:sqlserver://db:1433;encrypt=true;loginTimeout=30",
                SqlConnection.toJdbcUrl("Data Source=tcp:db,1433;Encrypt=yes;Connect Timeout=30"));
        ArgumentException e = assertThrows(ArgumentException.class, () -> SqlConnection.toJdbcUrl("server=x;foo=bar"));
        assertEquals("Keyword not supported: 'foo'.", e.getMessage());
    }
}
