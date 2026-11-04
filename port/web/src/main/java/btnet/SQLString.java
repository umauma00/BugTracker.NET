// Source: src/BugTracker.Web/btnet/SQLString.cs
package btnet;

import btnet.runtime.DBNull;
import btnet.runtime.Strings;
import btnet.runtime.data.SqlParameter;
import java.util.ArrayList;
import java.util.List;

public class SQLString
{
    private String _value;
    private List<SqlParameter> _parameters;

    public SQLString(String value)
    {
        _value = value;
        _parameters = new ArrayList<SqlParameter>();
    }

    public SQLString(String value, List<SqlParameter> parameters)
    {
        _value = value;
        _parameters = parameters;
    }

    @Override
    public String toString()
    {
        return _value;
    }

    public SQLString AddParameterWithValue(String parameter, Object value)
    {
        if (value == null)
            value = DBNull.Value;
        if (!parameter.startsWith("@"))
            parameter = "@" + Strings.cs(parameter);
        var p = new SqlParameter();
        p.ParameterName(parameter);
        p.Value(value);
        _parameters.add(p);
        return this;
    }

    public SQLString AddParameterWithValue(String parameter, int value)
    {
        if (!parameter.startsWith("@"))
            parameter = "@" + Strings.cs(parameter);
        var p = new SqlParameter();
        p.ParameterName(parameter);
        p.Value(value);
        _parameters.add(p);
        return this;
    }


    public SQLString Append(String toAppend)
    {
        _value = Strings.cs(_value) + Strings.cs(toAppend);
        return this;
    }

    public SQLString Append(SQLString toAppend)
    {
        _value = Strings.cs(_value) + Strings.cs(toAppend.toString());
        for (SqlParameter param : toAppend.GetParameters())
            _parameters.add(param);
        return this;

    }
    public List<SqlParameter> GetParameters()
    {
        return _parameters;
    }
}
// PORT STATUS: confidence=high todos=0
