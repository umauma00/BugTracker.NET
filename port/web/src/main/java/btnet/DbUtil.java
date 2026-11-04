// Source: src/BugTracker.Web/btnet/dbutil.cs
/*
Copyright 2002-2011 Corey Trager
Distributed under the terms of the GNU General Public License
*/

package btnet;

import btnet.runtime.CultureInfo;
import btnet.runtime.DBNull;
import btnet.runtime.DotNetFormat;
import btnet.runtime.Stopwatch;
import btnet.runtime.Strings;
import btnet.runtime.data.CommandBehavior;
import btnet.runtime.data.DataRow;
import btnet.runtime.data.DataSet;
import btnet.runtime.data.DataView;
import btnet.runtime.data.SqlCommand;
import btnet.runtime.data.SqlConnection;
import btnet.runtime.data.SqlDataAdapter;
import btnet.runtime.data.SqlDataReader;
import btnet.runtime.data.SqlDbType;
import btnet.runtime.data.SqlParameter;
import java.util.Objects;

public class DbUtil
{
    ///////////////////////////////////////////////////////////////////////
    public static Object execute_scalar(SQLString sql)
    {
        if (Objects.equals(Util.get_setting("LogSqlEnabled", "1"), "1"))
        {
            Util.write_to_log("sql=\n" + Strings.cs(sql));
        }

        try (SqlConnection conn = GetConnection())
        {
            Object returnValue;
            SqlCommand cmd = new SqlCommand(sql.toString(), conn);
            cmd.Parameters().AddRange(sql.GetParameters().toArray(new SqlParameter[0])); // inventory: src/BugTracker.Web/btnet/dbutil.cs L28
            returnValue = cmd.ExecuteScalar();
            conn.Close(); // redundant, but just to be clear
            return returnValue;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static void execute_nonquery_without_logging(SQLString sql)
    {
        try (SqlConnection conn = GetConnection())
        {
            SqlCommand cmd = new SqlCommand(sql.toString(), conn);
            cmd.Parameters().AddRange(sql.GetParameters().toArray(new SqlParameter[0])); // inventory: src/BugTracker.Web/btnet/dbutil.cs L41
            cmd.ExecuteNonQuery();
            conn.Close(); // redundant, but just to be clear
        }

    }

    ///////////////////////////////////////////////////////////////////////
    public static void execute_nonquery(SQLString sql)
    {

        if (Objects.equals(Util.get_setting("LogSqlEnabled", "1"), "1"))
        {
            Util.write_to_log("sql=\n" + Strings.cs(sql));
        }

        try (SqlConnection conn = GetConnection())
        {
            SqlCommand cmd = new SqlCommand(sql.toString(), conn);
            cmd.Parameters().AddRange(sql.GetParameters().toArray(new SqlParameter[0])); // inventory: src/BugTracker.Web/btnet/dbutil.cs L60
            cmd.ExecuteNonQuery();
            conn.Close(); // redundant, but just to be clear
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static void execute_nonquery(SqlCommand cmd)
    {
        log_command(cmd);

        try (SqlConnection conn = GetConnection())
        {
            try
            {
                cmd.Connection(conn); // inventory: src/BugTracker.Web/btnet/dbutil.cs L75
                cmd.ExecuteNonQuery();
                conn.Close(); // redundant, but just to be clear
            }
            finally
            {
                conn.Close(); // redundant, but just to be clear
                cmd.Connection(null); // inventory: src/BugTracker.Web/btnet/dbutil.cs L82
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static SqlDataReader execute_reader(SQLString sql, int behavior)
    {
        if (Objects.equals(Util.get_setting("LogSqlEnabled", "1"), "1"))
        {
            Util.write_to_log("sql=\n" + Strings.cs(sql));
        }

        SqlConnection conn = GetConnection();
        try
        {
            try (SqlCommand cmd = new SqlCommand(sql.toString(), conn))
            {
                cmd.Parameters().AddRange(sql.GetParameters().toArray(new SqlParameter[0])); // inventory: src/BugTracker.Web/btnet/dbutil.cs L100
                return cmd.ExecuteReader(behavior | CommandBehavior.CloseConnection);
            }
        }
        catch (Throwable t) // inventory: src/BugTracker.Web/btnet/dbutil.cs L104
        {
            conn.Close();
            throw t;
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static SqlDataReader execute_reader(SqlCommand cmd, int behavior)
    {
        log_command(cmd);

        SqlConnection conn = GetConnection();
        try
        {
            cmd.Connection(conn);
            return cmd.ExecuteReader(behavior | CommandBehavior.CloseConnection);
        }
        catch (Throwable t) // inventory: src/BugTracker.Web/btnet/dbutil.cs L122
        {
            conn.Close();
            throw t;
        }
        finally
        {
            cmd.Connection(null);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static DataSet get_dataset(SQLString sql)
    {

        if (Objects.equals(Util.get_setting("LogSqlEnabled", "1"), "1"))
        {
            Util.write_to_log("sql=\n" + Strings.cs(sql));
        }

        DataSet ds = new DataSet();
        try (SqlConnection conn = GetConnection())
        {
            try (SqlDataAdapter da = new SqlDataAdapter(sql.toString(), conn))
            {

                da.SelectCommand().Parameters().AddRange(sql.GetParameters().toArray(new SqlParameter[0])); // inventory: src/BugTracker.Web/btnet/dbutil.cs L148
                Stopwatch stopwatch = new Stopwatch();
                stopwatch.Start();
                da.Fill(ds);
                stopwatch.Stop();
                log_stopwatch_time(stopwatch);
                conn.Close(); // redundant, but just to be clear
                return ds;
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static void log_stopwatch_time(Stopwatch stopwatch)
    {
        if (Objects.equals(Util.get_setting("LogSqlEnabled", "1"), "1"))
        {
            Util.write_to_log("elapsed milliseconds:" + Strings.cs(DotNetFormat.Format(stopwatch.ElapsedMilliseconds(), "0000", CultureInfo.EN_US))); // inventory: src/BugTracker.Web/btnet/dbutil.cs L165
        }
    }


    ///////////////////////////////////////////////////////////////////////
    public static DataView get_dataview(SQLString sql)
    {
        DataSet ds = get_dataset(sql);
        return new DataView(ds.Tables().get(0));
    }


    ///////////////////////////////////////////////////////////////////////
    public static DataRow get_datarow(SQLString sql)
    {
        DataSet ds = get_dataset(sql);
        if (ds.Tables().get(0).Rows().Count() != 1)
        {
            return null;
        }
        else
        {
            return ds.Tables().get(0).Rows().get(0);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    public static SqlConnection GetConnection()
    {

        String connection_string = Util.get_setting("ConnectionString", "MISSING CONNECTION STRING");
        SqlConnection conn = new SqlConnection(connection_string);
        conn.Open();
        return conn;
    }

    ///////////////////////////////////////////////////////////////////////
    private static void log_command(SqlCommand cmd)
    {
        if (Objects.equals(Util.get_setting("LogSqlEnabled", "1"), "1"))
        {
            StringBuilder sb = new StringBuilder();
            sb.append(Strings.cs("sql=\n" + Strings.cs(cmd.CommandText())));
            // TODO(port): runtime SqlParameterCollection must implement Iterable<SqlParameter> (foreach over cmd.Parameters)
            for (SqlParameter param : cmd.Parameters())
            {
                sb.append("\n  ");
                sb.append(Strings.cs(param.ParameterName()));
                sb.append("=");
                if (param.Value() == null || param.Value() == DBNull.Value)
                {
                    sb.append("null");
                }
                else if (param.SqlDbType() == SqlDbType.Text || param.SqlDbType() == SqlDbType.Image)
                {
                    sb.append("...");
                }
                else
                {
                    sb.append("\"");
                    sb.append(Strings.cs(param.Value()));
                    sb.append("\"");
                }
            }
            Util.write_to_log(sb.toString());
        }
    }

} // end DbUtil

// end namespace
// PORT STATUS: confidence=high todos=1
