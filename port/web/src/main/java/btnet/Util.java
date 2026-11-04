// Source: src/BugTracker.Web/btnet/util.cs
/*
Copyright 2002-2011 Corey Trager
Distributed under the terms of the GNU General Public License
*/

package btnet;

import btnet.Security.ClaimsIdentityExtensions;
import btnet.runtime.AppPaths;
import btnet.runtime.ClrTypes;
import btnet.runtime.Convert;
import btnet.runtime.CultureInfo;
import btnet.runtime.DBNull;
import btnet.runtime.DateTimes;
import btnet.runtime.Decimal;
import btnet.runtime.DotNetException;
import btnet.runtime.DotNetFormat;
import btnet.runtime.DotNetRegex;
import btnet.runtime.Encoding;
import btnet.runtime.Exceptions;
import btnet.runtime.FormatException;
import btnet.runtime.Int32;
import btnet.runtime.NameValueCollection;
import btnet.runtime.NumberStyles;
import btnet.runtime.Ref;
import btnet.runtime.RegexOptions;
import btnet.runtime.StringSplitOptions;
import btnet.runtime.Strings;
import btnet.runtime.config.ConfigurationManager;
import btnet.runtime.data.DataColumn;
import btnet.runtime.data.DataRowView;
import btnet.runtime.data.DataSet;
import btnet.runtime.data.DataTable;
import btnet.runtime.data.DataView;
import btnet.runtime.io.Directory;
import btnet.runtime.io.Path;
import btnet.runtime.security.IIdentity;
import btnet.runtime.security.Rfc2898DeriveBytes;
import btnet.runtime.web.ApplicationState;
import btnet.runtime.web.HttpContext;
import btnet.runtime.web.HttpRequest;
import btnet.runtime.web.HttpResponse;
import btnet.runtime.web.HttpUtility;
import btnet.runtime.web.UrlHelper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Util {

	///////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////
	// Util
	///////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////

	public static HttpContext context = null; // inventory: src/BugTracker.Web/btnet/util.cs L32
	private static HttpRequest Request = null; // inventory: src/BugTracker.Web/btnet/util.cs L33
	//private static HttpResponse Response = null;
	//private static HttpServerUtility Server = null;

	private static final Object dummy = new Object(); // inventory: src/BugTracker.Web/btnet/util.cs L37

	public static final DotNetRegex reCommas = new DotNetRegex(","); // inventory: src/BugTracker.Web/btnet/util.cs L39
	public static final DotNetRegex rePipes = new DotNetRegex("\\|"); // inventory: src/BugTracker.Web/btnet/util.cs L40
	private static final DotNetRegex reEmail = new DotNetRegex("^([a-zA-Z0-9_\\-\\'\\.]+)@([a-zA-Z0-9_\\-\\.]+)\\.([a-zA-Z]{2,5})$"); // inventory: src/BugTracker.Web/btnet/util.cs L41


	public static boolean validate_email(String s)
	{
		// BUG(port): "$" also matches before a final line feed, so a trailing newline is accepted — repro: validate_email("a@b.com\n")
		return reEmail.IsMatch(s);
	}

	///////////////////////////////////////////////////////////////////////
	public static void set_context(HttpContext asp_net_context)
	{
		context = asp_net_context;

		try
		{
			Request = context.Request(); // inventory: src/BugTracker.Web/btnet/util.cs L33
		}
		catch (RuntimeException e) // inventory: src/BugTracker.Web/btnet/util.cs L58
		{
			Util.write_to_log("caught exception in util.set_context:" + Strings.cs(Exceptions.Message(e)));
		}

	}

	///////////////////////////////////////////////////////////////////////
	public static String get_form_name()
	{
		return get_setting("AspNetFormId", "ctl00");
	}

	///////////////////////////////////////////////////////////////////////
	public static void write_to_log(String s)
	{
		Logger log = LoggerFactory.getLogger(Util.class);
		log.debug(s);
	}

	///////////////////////////////////////////////////////////////////////
	public static void write_to_memory_log(String s)
	{
		// (source TODO) This can probably be handled in a better way using NLog or Glimpse

		if (HttpContext.Current() == null)
		{
			return;
		}

		if (Objects.equals(Util.get_setting("MemoryLogEnabled", "0"), "0"))
		{
			return;
		}

		String url = "";
		if (HttpContext.Current().Request() != null)
		{
			url = Strings.ToStringStrict(HttpContext.Current().Request().Url());
		}

		String line = Strings.cs(DotNetFormat.Format(LocalDateTime.now(), "yyy-MM-dd HH:mm:ss:fff", CultureInfo.EN_US)) // inventory: src/BugTracker.Web/btnet/util.cs L99
			+ " "
			+ Strings.cs(url)
			+ " "
			+ Strings.cs(s);


		List<String> list = (List<String>) HttpContext.Current().Application().get("log"); // inventory: src/BugTracker.Web/btnet/util.cs L106

		if (list == null)
		{
			list = new ArrayList<String>();
			HttpContext.Current().Application().set("log", list); // inventory: src/BugTracker.Web/btnet/util.cs L111
		}

		list.add(line);

	}

	///////////////////////////////////////////////////////////////////////
	public static void do_not_cache(HttpResponse Response)
	{
		Response.CacheControl("no-cache");
		Response.AddHeader("Pragma", "no-cache");
		Response.Expires(-1);
	}

	///////////////////////////////////////////////////////////////////////
	public static String get_setting(String name, String default_value)
	{

		NameValueCollection name_values
			= (NameValueCollection) ConfigurationManager.GetSection("appSettings");
		if (Strings.IsNullOrEmpty(name_values.get(name)))
		{
			return default_value;
		}
		else
		{
			return name_values.get(name);
		}
	}


	///////////////////////////////////////////////////////////////////////
	public static boolean is_int(String maybe_int)
	{
		try
		{
			int i = Int32.Parse(maybe_int);
			return true;
		}
		catch (RuntimeException ignored) // inventory: src/BugTracker.Web/btnet/util.cs L151
		{
			return false;
		}
	}

	///////////////////////////////////////////////////////////////////////
	public static String is_valid_decimal(String name, String val, int left_of_decimal, int right_of_decimal)
	{
		CultureInfo ci = btnet.Util.get_culture_info();
		Ref<BigDecimal> x = new Ref<>(null); // inventory: src/BugTracker.Web/btnet/util.cs L162
		if (!Decimal.TryParse(val, NumberStyles.Float, ci, x))
		{
			return Strings.cs(name) + " is not in a valid decimal format";
		}

		String[] vals = Strings.Split(val, StringSplitOptions.None, ci.NumberFormat().NumberDecimalSeparator());

		// BUG(port): the right_of_decimal check is in an else-if that can never run after "vals.length > 0" — repro: is_valid_decimal("x", "1.12345", 5, 2) returns ""
		if (vals.length > 0)
		{
			if (vals[0].length() > left_of_decimal)
			{
				return Strings.cs(name) + " has too many digits to the left of the decimal point";
			}
		}
		else if (vals.length > 1)
		{
			if (vals[1].length() > right_of_decimal)
			{
				return Strings.cs(name) + " has too many digits to the right of the decimal point";
			}
		}

		return "";

	}

	///////////////////////////////////////////////////////////////////////
	public static boolean is_datetime(String maybe_datetime)
	{
		LocalDateTime d;

		try
		{
			d = DateTimes.Parse(maybe_datetime, get_culture_info());
			return true;
		}
		catch (RuntimeException ignored) // inventory: src/BugTracker.Web/btnet/util.cs L198
		{
			return false;
		}
	}

	///////////////////////////////////////////////////////////////////////
	public static String bool_to_string(boolean b)
	{
		return (b ? "1" : "0");
	}


	///////////////////////////////////////////////////////////////////////
	public static String strip_html(String text_with_tags) {

		if (Objects.equals(Util.get_setting("StripHtmlTagsFromSearchableText", "1"), "1"))
		{
			// sanctioned regex rewrite — RULEBOOK §2A C25
			return HttpUtility.HtmlDecode(DotNetRegex.Replace(text_with_tags, "<[^>]*>", "")); // inventory: src/BugTracker.Web/btnet/util.cs L216
		}
		else
		{
			return text_with_tags;
		}

	}

	///////////////////////////////////////////////////////////////////////
	public static String strip_dangerous_tags(String text_with_tags)
	{
		// inventory: src/BugTracker.Web/btnet/util.cs L228-L259 (literal patterns; the duplicate onmouseup line is dead code and stays)
		String s = DotNetRegex.Replace(text_with_tags,
		                     "<script", "<scrSAFEipt", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "</script", "</scrSAFEipt", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "<object", "<obSAFEject", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "</object", "</obSAFEject", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "<embed", "<emSAFEbed", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "</embed", "</emSAFEbed", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onabort", "onSAFEabort", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onblur", "onSAFEblur", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onchange", "onSAFEchange", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onclick", "onSAFEclick", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "ondblclick", "onSAFEdblclick", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onerror", "onSAFEerror", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onfocus", "onSAFEfocus", RegexOptions.IgnoreCase);

		s = DotNetRegex.Replace(s, "onkeydown", "onSAFEkeydown", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onkeypress", "onSAFEkeypress", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onkeyup", "onSAFEkeyup", RegexOptions.IgnoreCase);

		s = DotNetRegex.Replace(s, "onload", "onSAFEload", RegexOptions.IgnoreCase);

		s = DotNetRegex.Replace(s, "onmousedown", "onSAFEmousedown", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onmousemove", "onSAFEmousemove", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onmouseout", "onSAFEmouseout", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onmouseup", "onSAFEmouseup", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onmouseup", "onSAFEmouseup", RegexOptions.IgnoreCase);

		// BUG(port): replacement "onSAFEresetK" has a stray K — repro: strip_dangerous_tags("onreset")
		s = DotNetRegex.Replace(s, "onreset", "onSAFEresetK", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onresize", "onSAFEresize", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onselect", "onSAFEselect", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onsubmit", "onSAFEsubmit", RegexOptions.IgnoreCase);
		s = DotNetRegex.Replace(s, "onunload", "onSAFEunload", RegexOptions.IgnoreCase);

		return s;
	}


	///////////////////////////////////////////////////////////////////////
	public static CultureInfo get_culture_info()
	{
		// Create a basic culture object to provide also all input parsing
		// original: new CultureInfo(Thread.CurrentThread.CurrentCulture.Name); culture is en-US everywhere (RULEBOOK §2A Dates, numbers, culture)
		return CultureInfo.EN_US;
	}

	///////////////////////////////////////////////////////////////////////
	public static String format_db_date_and_time(Object date)
	{

		if (date.getClass() == DBNull.class)
		{
			return "";
		}
		// not sure when this case happens, but it's a workaround for a bug
		// somebody reported, 1257368
		else if (date.getClass() == String.class)
		{
			return Strings.ToStringStrict(date);
		}
		else
		{

			// We don't know whether time is significant or not,
			// but we can guess.  Probably, not for sure, but probably
			// if the time is 12:00 AM, the time is just debris.

			LocalDateTime dt = (LocalDateTime) date;

			if (dt.getYear() == 1900)
			{
				return "";
			}
			else
			{
				String date_time_format = "";
				if ((dt.getHour() == 0 || dt.getHour() == 12) && dt.getMinute() == 0 && dt.getSecond() == 0)
				{
					date_time_format = get_setting("JustDateFormat", "g");
				}
				else
				{
					date_time_format = get_setting("DateTimeFormat", "g");
				}

				int hours_offset = Convert.ToInt32(btnet.Util.get_setting("DisplayTimeOffsetInHours", "0"));

				if (hours_offset != 0)
				{
					dt = dt.plusHours(hours_offset);
				}
				return DotNetFormat.Format(dt, date_time_format, get_culture_info());
			}
		}
	}


	//modified by CJU on jan 9 2008
	///////////////////////////////////////////////////////////////////////
	public static String format_db_value( BigDecimal val ) {

		return DotNetFormat.Format(val, "G", get_culture_info( )); // inventory: src/BugTracker.Web/btnet/util.cs (RULEBOOK §2A decimal ToString(IFormatProvider), S1-15)

	}

	///////////////////////////////////////////////////////////////////////
	public static String format_db_value( LocalDateTime val ) {

		return format_db_date_and_time( val );

	}

	///////////////////////////////////////////////////////////////////////
	public static String format_db_value( Object val ) {

		if( val instanceof BigDecimal )
			return format_db_value( (BigDecimal)val );
		else if( val instanceof LocalDateTime )
			return format_db_value( (LocalDateTime)val );
		else
			return Convert.ToString( val );

	}
	//end modified by CJU on jan 9 2008

	///////////////////////////////////////////////////////////////////////
	public static String format_local_date_into_db_format(String date)
	{


		// seems to already be in the right format
		LocalDateTime d;
		try
		{
			d = DateTimes.Parse(date, get_culture_info());
		}
		catch (FormatException e_) // inventory: src/BugTracker.Web/btnet/util.cs L362
		{
			// Can not translate this
			return "";
		}
		// Note that yyyyMMdd HH:mm:ss is a universal SQL dateformat for strings.
		return DotNetFormat.Format(d, get_setting("SQLServerDateFormat", "yyyyMMdd HH:mm:ss"), CultureInfo.EN_US);

	}

	///////////////////////////////////////////////////////////////////////
	public static String format_local_decimal_into_db_format( String val )
	{
		BigDecimal x = Decimal.Parse(val, get_culture_info());

		return DotNetFormat.Format(x, "G", CultureInfo.InvariantCulture); // inventory: src/BugTracker.Web/btnet/util.cs L377 (RULEBOOK §2A decimal ToString(IFormatProvider), S1-15)
	}

	///////////////////////////////////////////////////////////////////////
	public static SQLString alter_sql_per_project_permissions(SQLString sql, IIdentity identity)
	{
		int userId = ClaimsIdentityExtensions.GetUserId(identity);
		int organizationId = ClaimsIdentityExtensions.GetOrganizationId(identity);

		String project_permissions_sql;

		String dpl = Util.get_setting("DefaultPermissionLevel", "2");

		if (Objects.equals(dpl, "0"))
		{
			project_permissions_sql = " (bugs.bg_project in (\r\n" +
				"\t\t\t\t\tselect pu_project\r\n" +
				"\t\t\t\t\tfrom project_user_xref\r\n" +
				"\t\t\t\t\twhere pu_user = $user\r\n" +
				"\t\t\t\t\tand pu_permission_level > 0)) ";
		}
		else
		{
			project_permissions_sql = " (bugs.bg_project not in (\r\n" +
				"\t\t\t\t\tselect pu_project\r\n" +
				"\t\t\t\t\tfrom project_user_xref\r\n" +
				"\t\t\t\t\twhere pu_user = $user\r\n" +
				"\t\t\t\t\tand pu_permission_level = 0)) ";
		}

		if (ClaimsIdentityExtensions.GetCanOnlySeeOwnReportedBugs(identity))
		{
			project_permissions_sql += "\r\n" +
				"\t\t\t\t\t    and bugs.bg_reported_user = $user ";

		}
		else
		{
			if (ClaimsIdentityExtensions.GetOtherOrgsPermissionLevels(identity) == 0)
			{
				project_permissions_sql += "\r\n" +
				"\t\t\t\t\t    and bugs.bg_org = $user.org ";
			}
		}

		project_permissions_sql
			= project_permissions_sql.replace("$user.org", Convert.ToString(organizationId));

		project_permissions_sql
			= project_permissions_sql.replace("$user", Convert.ToString(userId));


		// Figure out where to alter sql for project permissions
		// I've tried lots of different schemes over the years....

		int alter_here_pos = sql.toString().indexOf("$ALTER_HERE"); // places - can be multiple - are explicitly marked
		if (alter_here_pos != -1)
		{
			return new SQLString(sql.toString().replace("$ALTER_HERE", "/* ALTER_HERE */ " + Strings.cs(project_permissions_sql)), sql.GetParameters());
		}
		else
		{
			String bug_sql;
			var rawSQL = sql.toString();
			int where_pos = rawSQL.indexOf("WhErE"); // first look for a "special" where, case sensitive, in case there are multiple where's to choose from
			if (where_pos == -1)
				where_pos = Strings.ToUpper(rawSQL).indexOf("WHERE");

			int order_pos = rawSQL.indexOf("/*ENDWHR*/"); // marker for end of the where statement

			if (order_pos == -1)
				order_pos = Strings.ToUpper(rawSQL).lastIndexOf("ORDER BY");

			if (order_pos < where_pos)
				order_pos = -1; // ignore an order by that occurs in a subquery, for example

			if (where_pos != -1 && order_pos != -1)
			{
				// both WHERE and ORDER BY clauses
				bug_sql = Strings.cs(rawSQL.substring(0, where_pos + 5))
					+ " /* altered - both  */ ( "
					+ Strings.cs(rawSQL.substring(where_pos + 5, (where_pos + 5) + (order_pos - (where_pos + 5))))
					+ " ) AND ( "
					+ Strings.cs(project_permissions_sql)
					+ " ) "
					+ Strings.cs(rawSQL.substring(order_pos));
			}
			else if (order_pos == -1 && where_pos == -1)
			{
				// Neither
				bug_sql = Strings.cs(rawSQL) + " /* altered - neither */ WHERE " + Strings.cs(project_permissions_sql);
			}
			else if (order_pos == -1)
			{
				// WHERE, without order
				bug_sql = Strings.cs(rawSQL.substring(0, where_pos + 5))
					+ " /* altered - just where */ ( "
					+ Strings.cs(rawSQL.substring(where_pos + 5))
					+ " ) AND ( "
					+ Strings.cs(project_permissions_sql) + " )";
			}
			else
			{
				// ORDER BY, without WHERE
				bug_sql = Strings.cs(rawSQL.substring(0, order_pos))
					+ " /* altered - just order by  */ WHERE "
					+ Strings.cs(project_permissions_sql)
					+ Strings.cs(rawSQL.substring(order_pos));
			}

			return new SQLString(bug_sql, sql.GetParameters());
		}

	}


	///////////////////////////////////////////////////////////////////////
	public static String HashString(String password, String salt)
	{
		Rfc2898DeriveBytes k2 = new Rfc2898DeriveBytes(password, Encoding.UTF8.GetBytes(Strings.cs(salt) + Strings.cs(salt)));
		var result = Encoding.UTF8.GetString(k2.GetBytes(128));
		return result;
	}

	///////////////////////////////////////////////////////////////////////
	public static void update_user_password(int us_id, String unencypted)
	{
		var salt = GenerateRandomString();

		String hashed = Util.HashString(unencypted, Convert.ToString(salt));

		var sql = new SQLString("update users set us_password = @hashed, us_salt = @salt where us_id = @id");

		sql = sql.AddParameterWithValue("hashed", hashed);
		sql = sql.AddParameterWithValue("salt", Convert.ToString(salt));
		sql = sql.AddParameterWithValue("id", Convert.ToString(us_id));

		btnet.DbUtil.execute_nonquery(sql);
	}

	public static void update_user_password(String username, String unencypted)
	{
		var salt = GenerateRandomString();

		String hashed = Util.HashString(unencypted, Convert.ToString(salt));

		var sql = new SQLString("update users set us_password = @hashed, us_salt = @salt where us_username = @username");

		sql = sql.AddParameterWithValue("hashed", hashed);
		sql = sql.AddParameterWithValue("salt", Convert.ToString(salt));
		sql = sql.AddParameterWithValue("username", Convert.ToString(username));

		btnet.DbUtil.execute_nonquery(sql);
	}

	// BUG(port): non-cryptographic PRNG used for password salt and reset key — repro: any call to GenerateRandomString
	private static final java.util.Random _random = new java.util.Random(); // inventory: src/BugTracker.Web/btnet/util.cs L532
	public static String GenerateRandomString()
	{
		var characters = "ABCDEFGHIJKLMNOPQURSTUVWXYZabcdefghijklmnopqurtuvwxyz1234567890".toCharArray();
		var builder = new StringBuilder();
		// BUG(port): the loop bound is re-drawn on every pass, so the length is not uniform in 10..99 — repro: any call
		for (int i = 0; i < 10 + _random.nextInt(90); i++)
		{
			// BUG(port): Next(characters.Length - 1) never selects the last character ('0') — repro: any call
			builder.append(characters[_random.nextInt(characters.length - 1)]);
		}
		return builder.toString();
	}

	///////////////////////////////////////////////////////////////////////
	public static String capitalize_first_letter(String s)
	{
		if (s.length() > 0 && Objects.equals(Util.get_setting("NoCapitalization", "0"), "0"))
		{
			return Strings.cs(Strings.ToUpper(s.substring(0, 1))) + Strings.cs(s.substring(1, 1 + (s.length() - 1)));
		}
		return s;

	}


	///////////////////////////////////////////////////////////////////////
	public static String sanitize_integer(String s)
	{
		int n;
		String s2;
		try
		{
			n = Convert.ToInt32(s);
			s2 = Convert.ToString(n);
		}
		catch (Throwable t) // inventory: src/BugTracker.Web/btnet/util.cs L566
		{
			Exceptions.rethrowIfEnd(t);
			throw (new DotNetException("Expected integer.  Possible SQL injection attempt?"));

		}

		return s;
	}


	///////////////////////////////////////////////////////////////////////
	public static boolean is_numeric_datatype(Class<?> datatype)
	{

		if (datatype == Integer.class
		|| datatype == BigDecimal.class
		|| datatype == Double.class
		|| datatype == Float.class
		|| datatype == ClrTypes.UInt32.class
		|| datatype == Long.class
		|| datatype == ClrTypes.UInt64.class
		|| datatype == Short.class
		|| datatype == ClrTypes.UInt16.class)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public static String GetAbsolutePath(String path)
	{
		String result;
		if (Path.IsPathRooted(path))
		{
			result = path;
		}
		else
		{
			String appRootPath = Path.GetDirectoryName(AppPaths.ConfigurationFile());
			result = Path.Combine(appRootPath, path);
		}
		return result;
	}

	///////////////////////////////////////////////////////////////////////
	public static String get_folder(String name, String dflt)
	{
		String folder = Util.get_setting(name, "");
		if (Objects.equals(folder, ""))
			return dflt;

		folder = GetAbsolutePath(folder);
		if (!Directory.Exists(folder))
		{
			throw (new DotNetException(Strings.cs(name) + " specified in Web.config, \""
			+ Strings.cs(folder)
			+ "\", not found.  Edit Web.config."));
		}

		return folder;
	}

	///////////////////////////////////////////////////////////////////////
	public static String get_upload_folder()
	{
		return get_folder("UploadFolder", GetAbsolutePath("App_Data\\uploads"));
	}

	///////////////////////////////////////////////////////////////////////
	public static String get_log_folder()
	{
		return get_folder("LogFileFolder", GetAbsolutePath("App_Data\\logs"));
	}

	///////////////////////////////////////////////////////////////////////
	public static String[] split_string_using_commas(String s)
	{
		return reCommas.Split(s);
	}



	///////////////////////////////////////////////////////////////////////
	public static String[] split_dropdown_vals(String s)
	{
		String[] array = rePipes.Split(s);
		for (int i = 0; i < array.length; i++)
		{
			array[i] = Strings.Trim(array[i]).replace("\r", "").replace("\n", "");
		}
		return array;
	}

	///////////////////////////////////////////////////////////////////////
	// common to add/edit custom files, project
	public static String validate_dropdown_values(String vals)
	{
		if (vals.contains("'")
		|| vals.contains("\"")
		|| vals.contains("<")
		|| vals.contains(">")
		|| vals.contains("\t"))
		{
			return "Special characters like <, >, or quotes not allowed.";
		}
		else
		{
			return "";
		}
	}

	///////////////////////////////////////////////////////////////////////
	public static String how_long_ago(int seconds)
	{
		return how_long_ago(Duration.ofHours(0).plusMinutes(0).plusSeconds(seconds));
	}

	///////////////////////////////////////////////////////////////////////
	public static String how_long_ago(Duration ts)
	{

		if ((int) ts.toDaysPart() > 0)
		{
			if ((int) ts.toDaysPart() == 1)
			{
				if (ts.toHoursPart() > 2)
				{
					return "1 day and " + ts.toHoursPart() + " hours ago";
				}
				else
				{
					return "1 day ago";
				}
			}
			else
			{
				return (int) ts.toDaysPart() + " days ago";
			}
		}
		else if (ts.toHoursPart() > 0)
		{
			if (ts.toHoursPart() == 1)
			{
				if (ts.toMinutesPart() > 5)
				{
					return "1 hour and " + ts.toMinutesPart() + " minutes ago";
				}
				else
				{
					return "1 hour ago";
				}
			}
			else
			{
				return ts.toHoursPart() + " hours ago";
			}
		}
		else if (ts.toMinutesPart() > 0)
		{
			if (ts.toMinutesPart() == 1)
			{
				return "1 minute ago";
			}
			else
			{
				return ts.toMinutesPart() + " minutes ago";
			}
		}
		else
		{
			return ts.toSecondsPart() + " seconds ago";
		}

	}


	///////////////////////////////////////////////////////////////////////
	public static DataTable get_related_users(IIdentity identity, boolean force_full_names)
	{
		SQLString sql;

		final String limitUsernameDropdownSql = "\r\n" +
				"\r\n" +
				"select isnull(bg_assigned_to_user,0) keep_me\r\n" +
				"into #temp2\r\n" +
				"from bugs\r\n" +
				"union\r\n" +
				"select isnull(bg_reported_user,0) from bugs\r\n" +
				"\r\n" +
				"delete from #temp\r\n" +
				"where us_id not in (select keep_me from #temp2)\r\n" +
				"drop table #temp2\r\n";

		if (Objects.equals(Util.get_setting("DefaultPermissionLevel", "2"), "0"))
		{
			// only show users who have explicit permission
			// for projects that this user has permissions for

			sql = new SQLString("\r\n" +
				"/* get related users 1 */\r\n" +
				"\r\n" +
				"select us_id,\r\n" +
				"case when @fullnames = 1 then\r\n" +
				"    case when len(isnull(us_firstname,'') + ' ' + isnull(us_lastname,'')) > 1\r\n" +
				"\tthen isnull(us_firstname,'') + ' ' + isnull(us_lastname,'')\r\n" +
				"    else us_username end\r\n" +
				"else us_username end us_username,\r\n" +
				"isnull(us_email,'') us_email,\r\n" +
				"us_org,\r\n" +
				"og_external_user\r\n" +
				"into #temp\r\n" +
				"from users\r\n" +
				"inner join orgs on us_org = og_id\r\n" +
				"where us_id in\r\n" +
				"\t(select pu1.pu_user from project_user_xref pu1\r\n" +
				"\twhere pu1.pu_project in\r\n" +
				"\t\t(select pu2.pu_project from project_user_xref pu2\r\n" +
				"\t\twhere pu2.pu_user = @userid\r\n" +
				"\t\tand pu2.pu_permission_level <> 0\r\n" +
				"\t\t)\r\n" +
				"\tand pu1.pu_permission_level <> 0\r\n" +
				"\t)\r\n" +
				"\r\n" +
				"if @og_external_user = 1 -- external\r\n" +
				"and @og_other_orgs_permission_level = 0 -- other orgs\r\n" +
				"begin\r\n" +
				"\tdelete from #temp where og_external_user = 1 and us_org <> @userorg \r\n" +
				"end\r\n");


			if (Objects.equals(Util.get_setting("LimitUsernameDropdownsInSearch", "0"), "1"))
			{
				sql.Append(limitUsernameDropdownSql);
			}

			sql.Append("\r\n" +
				"select us_id, us_username, us_email from #temp order by us_username\r\n" +
				"\r\n" +
				"drop table #temp");
		}
		else
		{
			// show users UNLESS they have been explicitly excluded
			// from all the projects the viewer is able to view

			// the cartesian join in the first select is intentional

			sql = new SQLString("\r\n" +
				"/* get related users 2 */\r\n" +
				"select  pj_id, us_id,\r\n" +
				"case when @fullnames = 1 then\r\n" +
				"    case when len(isnull(us_firstname,'') + ' ' + isnull(us_lastname,'')) > 1\r\n" +
				"\tthen isnull(us_firstname,'') + ' ' + isnull(us_lastname,'')\r\n" +
				"    else us_username end\r\n" +
				"else us_username end us_username,\r\n" +
				"isnull(us_email,'') us_email\r\n" +
				"into #temp\r\n" +
				"from projects, users\r\n" +
				"where pj_id not in\r\n" +
				"(\r\n" +
				"\tselect pu_project from project_user_xref\r\n" +
				"\twhere pu_permission_level = 0 and pu_user = @userid\r\n" +
				")\r\n" +
				"\r\n");
			if (Objects.equals(Util.get_setting("LimitUsernameDropdownsInSearch", "0"), "1"))
			{
				sql.Append(limitUsernameDropdownSql);
			}

			sql.Append("\r\n" +
				"\r\n" +
				"if @og_external_user = 1 -- external\r\n" +
				"and @og_other_orgs_permission_level = 0 -- other orgs\r\n" +
				"begin\r\n" +
				"\tselect distinct a.us_id, a.us_username, a.us_email\r\n" +
				"\tfrom #temp a\r\n" +
				"\tinner join users b on a.us_id = b.us_id\r\n" +
				"\tinner join orgs on b.us_org = og_id\r\n" +
				"\twhere og_external_user = 0 or b.us_org = @userorg\r\n" +
				"\torder by a.us_username\r\n" +
				"end\r\n" +
				"else\r\n" +
				"begin\r\n" +
				"\r\n" +
				"\tselect distinct us_id, us_username, us_email\r\n" +
				"\t\tfrom #temp\r\n" +
				"\t\tleft outer join project_user_xref on pj_id = pu_project\r\n" +
				"\t\tand us_id = pu_user\r\n" +
				"\t\twhere isnull(pu_permission_level,2) <> 0\r\n" +
				"\t\torder by us_username\r\n" +
				"end\r\n" +
				"\r\n" +
				"drop table #temp");

		}

		if (force_full_names || Objects.equals(Util.get_setting("UseFullNames", "0"), "1"))
		{
			// true condition
			sql = sql.AddParameterWithValue("fullnames", 1);
		}
		else
		{
			// false condition
			sql = sql.AddParameterWithValue("fullnames", 0);
		}

		sql = sql.AddParameterWithValue("userid", ClaimsIdentityExtensions.GetUserId(identity));
		sql = sql.AddParameterWithValue("userorg", ClaimsIdentityExtensions.GetOrganizationId(identity));
		sql = sql.AddParameterWithValue("og_external_user", ClaimsIdentityExtensions.GetIsExternalUser(identity) ? 1 : 0);
		sql = sql.AddParameterWithValue("og_other_orgs_permission_level", ClaimsIdentityExtensions.GetOtherOrgsPermissionLevels(identity));

		return btnet.DbUtil.get_dataset(sql).Tables().get(0);

	}


	///////////////////////////////////////////////////////////////////////
	public static int get_default_user(int projectid)
	{

		if (projectid == 0) {return 0;}

		var sql = new SQLString("select isnull(pj_default_user,0)\r\n" +
				"\t\t\t\t\tfrom projects\r\n" +
				"\t\t\t\t\twhere pj_id = @pj");

		sql = sql.AddParameterWithValue("pj", Convert.ToString(projectid));
		Object obj = btnet.DbUtil.execute_scalar(sql);

		if (obj != null)
		{
			return (int) obj;
		}
		else
		{
			return 0;
		}

	}


	///////////////////////////////////////////////////////////////////////
	public static DataSet get_custom_columns()
	{

		DataSet ds = (DataSet) ApplicationState.get().get("custom_columns_dataset"); // inventory: src/BugTracker.Web/btnet/util.cs L918

		if (ds != null)
		{
			return ds;
		}
		else
		{
			ds = btnet.DbUtil.get_dataset(new SQLString("\r\n" +
				"/* custom columns */ select sc.name, st.[name] [datatype], \r\n" +
				"case when st.[name] = 'nvarchar' or st.[name] = 'nchar' then sc.length/2 else sc.length end as [length], \r\n" +
				"sc.xprec, sc.xscale, sc.isnullable,\r\n" +
				"mm.text [default value], \r\n" +
				"dflts.name [default name], \r\n" +
				"isnull(ccm_dropdown_type,'') [dropdown type],\r\n" +
				"isnull(ccm_dropdown_vals,'') [vals],\r\n" +
				"isnull(ccm_sort_seq, sc.colorder) [column order],\r\n" +
				"sc.colorder\r\n" +
				"from syscolumns sc\r\n" +
				"inner join systypes st on st.xusertype = sc.xusertype\r\n" +
				"inner join sysobjects so on sc.id = so.id\r\n" +
				"left outer join syscomments mm on sc.cdefault = mm.id\r\n" +
				"left outer join custom_col_metadata on ccm_colorder = sc.colorder\r\n" +
				"left outer join sysobjects dflts on dflts.id = mm.id\r\n" +
				"where so.name = 'bugs'\r\n" +
				"and st.[name] <> 'sysname'\r\n" +
				"and sc.name not in ('rowguid',\r\n" +
				"'bg_id',\r\n" +
				"'bg_short_desc',\r\n" +
				"'bg_reported_user',\r\n" +
				"'bg_reported_date',\r\n" +
				"'bg_project',\r\n" +
				"'bg_org',\r\n" +
				"'bg_category',\r\n" +
				"'bg_priority',\r\n" +
				"'bg_status',\r\n" +
				"'bg_assigned_to_user',\r\n" +
				"'bg_last_updated_user',\r\n" +
				"'bg_last_updated_date',\r\n" +
				"'bg_user_defined_attribute',\r\n" +
				"'bg_project_custom_dropdown_value1',\r\n" +
				"'bg_project_custom_dropdown_value2',\r\n" +
				"'bg_project_custom_dropdown_value3',\r\n" +
				"'bg_tags')\r\n" +
				"order by sc.id, isnull(ccm_sort_seq,sc.colorder)"));

			ApplicationState.get().set("custom_columns_dataset", ds); // inventory: src/BugTracker.Web/btnet/util.cs L964
			return ds;
		}

	}


	///////////////////////////////////////////////////////////////////////
	public static boolean check_password_strength(String pw)
	{
		if (Objects.equals(Util.get_setting("RequireStrongPasswords", "0"), "0"))
		{
			return true;
		}

		if (pw.length() < 8) return false;
		if (pw.indexOf("password") > -1) return false;
		if (pw.indexOf("123") > -1) return false;
		if (pw.indexOf("asdf") > -1) return false;
		if (pw.indexOf("qwer") > -1) return false;
		if (pw.indexOf("test") > -1) return false;

		int lowercase = 0;
		int uppercase = 0;
		int digits = 0;
		int special_chars = 0;

		for (int i = 0; i < pw.length(); i++)
		{
			char c = pw.charAt(i);
			if (c >= 'a' && c <= 'z') lowercase = 1;
			else if (c >= 'A' && c <= 'Z') uppercase = 1;
			else if (c >= '0' && c <= '9') digits = 1;
			else special_chars = 1;
		}

		if (lowercase + uppercase + digits + special_chars < 2)
		{
			return false;
		}

		return true;
	}

	///////////////////////////////////////////////////////////////////////
	public static String filename_to_content_type(String filename)
	{
		String ext = Strings.ToLower(Path.GetExtension(filename));

		if (Objects.equals(ext, ".jpg")
		|| Objects.equals(ext, ".jpeg"))
		{
			return "image/jpeg";
		}
		else if (Objects.equals(ext, ".gif"))
		{
			return "image/gif";
		}
		else if (Objects.equals(ext, ".bmp"))
		{
			return "image/bmp";
		}
		else if (Objects.equals(ext, ".tiff"))
		{
			return "image/tiff";
		}
		else if (Objects.equals(ext, ".txt") || Objects.equals(ext, ".ini") || Objects.equals(ext, ".bat") || Objects.equals(ext, ".js"))
		{
			return "text/plain";
		}
		else if (Objects.equals(ext, ".doc") || Objects.equals(ext, ".docx"))
		{
			return "application/msword";
		}
		else if (Objects.equals(ext, ".xls"))
		{
			return "application/excel";
		}
		else if (Objects.equals(ext, ".zip"))
		{
			return "application/zip";
		}
		else if (Objects.equals(ext, ".htm")
		|| Objects.equals(ext, ".html")
		|| Objects.equals(ext, ".asp")
		|| Objects.equals(ext, ".aspx")
		|| Objects.equals(ext, ".php"))
		{
			return "text/html";
		}
		else if (Objects.equals(ext, ".xml"))
		{
			return "text/xml";
		}
		else
		{
			return "";
		}

	}

	public static String request_to_string_for_sql(String val, String datatype)
	{

		if (val == null || val.length() == 0)
		{
			if (Objects.equals(datatype, "varchar")
			|| Objects.equals(datatype, "nvarchar")
			|| Objects.equals(datatype, "char")
			|| Objects.equals(datatype, "nchar"))
			{
				return "N''";
			}
			else
			{
				return "null";
			}
		}

		val = val.replace("'", "''");

		if (Objects.equals(datatype, "datetime"))
			return "'" + Strings.cs(btnet.Util.format_local_date_into_db_format(val)) + "'";
		else if (Objects.equals(datatype, "decimal"))
			return btnet.Util.format_local_decimal_into_db_format( val );
		else if (Objects.equals(datatype, "int"))
			return val;
		else
			return "N'" + Strings.cs(val) + "'";

	}

	///////////////////////////////////////////////////////////////////////
	public static void redirect(HttpRequest Request, HttpResponse Response)
	{
		// redirect to the page the user was going to or start off with bugs.aspx
		String url = Request.QueryString().get("url");
		String qs = Request.QueryString().get("qs");

		UrlHelper urlHelper = new UrlHelper(Request.RequestContext()); // inventory: src/BugTracker.Web/btnet/util.cs L1117
		// BUG(port): relative returnUrl rejected by IsLocalUrl — repro: default.aspx?url=edit_bug.aspx
		if (Strings.IsNullOrEmpty(url) || !urlHelper.IsLocalUrl(url))
		{
			String mobile = Request.get("mobile");
			if (Strings.IsNullOrEmpty(mobile))
			{
				Response.Redirect("bugs.aspx"); // inventory: src/BugTracker.Web/btnet/util.cs L1109
			}
			else {
				Response.Redirect("mbugs.aspx"); // inventory: src/BugTracker.Web/btnet/util.cs L1112
			}
		}
		else
		{
			Response.Redirect(Strings.cs(remove_line_breaks(url)) + "?" + Strings.cs(remove_line_breaks(HttpUtility.UrlDecode(qs)))); // inventory: src/BugTracker.Web/btnet/util.cs L1117
		}
	}

	///////////////////////////////////////////////////////////////////////
	public static void redirect(String url, HttpRequest Request, HttpResponse Response)
	{
		//redirect to the url supplied with the original querystring
		if (url.indexOf("?") > 0)
		{
			Response.Redirect(Strings.cs(url) + "&url="
				+ Strings.cs(remove_line_breaks(Request.QueryString().get("url")))
				+ "&qs="
				+ Strings.cs(remove_line_breaks(Request.QueryString().get("qs")))); // inventory: src/BugTracker.Web/btnet/util.cs L1127
		}
		else
		{
			Response.Redirect(Strings.cs(url) + "?url="
				+ Strings.cs(remove_line_breaks(Request.QueryString().get("url")))
				+ "&qs="
				+ Strings.cs(remove_line_breaks(Request.QueryString().get("qs")))); // inventory: src/BugTracker.Web/btnet/util.cs L1134
		}
	}

	///////////////////////////////////////////////////////////////////////
	public static String remove_line_breaks(String s) {
		if (s == null)
		{
			return "";
		}
		else
		{
			return s.replace("\n", "").replace("\r", "");
		}
	}

	///////////////////////////////////////////////////////////////////////
	public static void update_most_recent_login_datetime(int us_id)
	{
		var sql = new SQLString("update users set us_most_recent_login_datetime = getdate() where us_id = @us");
		sql = sql.AddParameterWithValue("us", Convert.ToString(us_id));
		DbUtil.execute_nonquery(sql);
	}


	///////////////////////////////////////////////////////////////////////
	public static void print_as_excel(HttpResponse Response, DataView dv)
	{
		Response.Clear();
		Response.AddHeader("content-disposition", "attachment; filename=btnet_" + Strings.cs(DotNetFormat.Format(LocalDateTime.now(), "yyyyMMdd_HHmmss", CultureInfo.EN_US)) + ".csv"); // inventory: src/BugTracker.Web/btnet/util.cs L1166
		Response.ContentType("application/ms-excel");
		Response.ContentEncoding(Encoding.UTF8);

		if (Objects.equals(btnet.Util.get_setting("WriteUtf8Preamble", "1"), "1"))
		{
			Response.BinaryWrite(Encoding.UTF8.GetPreamble());
		}

		int col;
		boolean first_column;
		String quote = "\"";
		String two_quotes = Strings.cs(quote) + Strings.cs(quote);
		String line_break = "\r\n";

		// column names
		first_column = true;
		for (col = 1; col < dv.Table().Columns().Count(); col++)
		{
			if (Objects.equals(dv.Table().Columns().get(col).ColumnName(), "$FLAG"))
				continue;
			if (Objects.equals(dv.Table().Columns().get(col).ColumnName(), "$SEEN"))
				continue;

			if (!first_column)
			{
				Response.Write(",");
			}
			Response.Write(quote);
			Response.Write(dv.Table().Columns().get(col).ColumnName().replace("<br>", " ").replace(quote, two_quotes));
			Response.Write(quote);
			first_column = false;
		}
		Response.Write(line_break);

		// bug rows
		for (DataRowView drv : dv)
		{
			first_column = true;
			for (col = 1; col < dv.Table().Columns().Count(); col++)
			{
				DataColumn column = dv.Table().Columns().get(col);

				if (Objects.equals(column.ColumnName(), "$FLAG"))
					continue;
				if (Objects.equals(column.ColumnName(), "$SEEN"))
					continue;

				if (!first_column)
				{
					Response.Write(",");
				}

				Response.Write(quote);
				if (column.DataType() == LocalDateTime.class)
				{
					Response.Write(btnet.Util.format_db_date_and_time(drv.get(col)));
				}
				else
				{
					Response.Write(Convert.ToString(drv.get(col)).replace(line_break, "|").replace("\n", "|").replace(quote, two_quotes));
				}

				Response.Write(quote);

				first_column = false;
			}
			Response.Write(line_break);
		}

		Response.End(); // inventory: src/BugTracker.Web/btnet/util.cs L1236
	}


	///////////////////////////////////////////////////////////////////////
	public static DataSet get_all_tasks(IIdentity identity, int bugid)
	{
		var sql = new SQLString("select ");

		if (bugid == 0)
		{
			sql.Append("\r\n" +
				"bg_id as [id], \r\n" +
				"bg_short_desc as [description], \r\n" +
				"pj_name as [project], \r\n" +
				"ct_name as [category], \r\n" +
				"bug_statuses.st_name as [status],  \r\n" +
				"bug_users.us_username as [assigned to],");
		}

		sql.Append("tsk_id [task<br>id], tsk_description [task<br>description] ");

		if (Objects.equals(btnet.Util.get_setting("ShowTaskAssignedTo", "1"), "1"))
		{
			sql.Append(", task_users.us_username [task<br>assigned to]");
		}

		if (Objects.equals(btnet.Util.get_setting("ShowTaskPlannedStartDate", "1"), "1"))
		{
			sql.Append(", tsk_planned_start_date [planned start]");
		}
		if (Objects.equals(btnet.Util.get_setting("ShowTaskActualStartDate", "1"), "1"))
		{
			sql.Append(", tsk_actual_start_date [actual start]");
		}

		if (Objects.equals(btnet.Util.get_setting("ShowTaskPlannedEndDate", "1"), "1"))
		{
			sql.Append(", tsk_planned_end_date [planned end]");
		}
		if (Objects.equals(btnet.Util.get_setting("ShowTaskActualEndDate", "1"), "1"))
		{
			sql.Append(", tsk_actual_end_date [actual end]");
		}

		if (Objects.equals(btnet.Util.get_setting("ShowTaskPlannedDuration", "1"), "1"))
		{
			sql.Append(", tsk_planned_duration [planned<br>duration]");
		}
		if (Objects.equals(btnet.Util.get_setting("ShowTaskActualDuration", "1"), "1"))
		{
			sql.Append(", tsk_actual_duration  [actual<br>duration]");
		}


		if (Objects.equals(btnet.Util.get_setting("ShowTaskDurationUnits", "1"), "1"))
		{
			sql.Append(", tsk_duration_units [duration<br>units]");
		}

		if (Objects.equals(btnet.Util.get_setting("ShowTaskPercentComplete", "1"), "1"))
		{
			sql.Append(", tsk_percent_complete [percent<br>complete]");
		}

		if (Objects.equals(btnet.Util.get_setting("ShowTaskStatus", "1"), "1"))
		{
			sql.Append(", task_statuses.st_name  [task<br>status]");
		}

		if (Objects.equals(btnet.Util.get_setting("ShowTaskSortSequence", "1"), "1"))
		{
			sql.Append(", tsk_sort_sequence  [seq]");
		}

		sql.Append("\r\n" +
				"from bug_tasks \r\n" +
				"inner join bugs on tsk_bug = bg_id\r\n" +
				"left outer join projects on bg_project = pj_id\r\n" +
				"left outer join categories on bg_category = ct_id\r\n" +
				"left outer join statuses bug_statuses on bg_status = bug_statuses.st_id\r\n" +
				"left outer join statuses task_statuses on tsk_status = task_statuses.st_id\r\n" +
				"left outer join users bug_users on bg_assigned_to_user = bug_users.us_id\r\n" +
				"left outer join users task_users on tsk_assigned_to_user = task_users.us_id\r\n" +
				"where tsk_bug in \r\n" +
				"(");

		if (bugid == 0)
		{
			sql.Append(btnet.Util.alter_sql_per_project_permissions(new SQLString("select bg_id from bugs"), identity));
		}
		else
		{
			sql.Append(Convert.ToString(bugid));
		}
		sql.Append("\r\n" +
				")\r\n" +
				"order by tsk_sort_sequence, tsk_id");


		DataSet ds = btnet.DbUtil.get_dataset(sql);

		return ds;
	}


	///////////////////////////////////////////////////////////////////////
	public static void display_bug_not_found(HttpResponse Response, int id)
	{
		Response.Write("<link rel=StyleSheet href=btnet.css type=text/css>");
		Response.Write("<p>&nbsp;</p><div class=align>");
		Response.Write("<div class=err>");
		Response.Write(btnet.Util.capitalize_first_letter(btnet.Util.get_setting("SingularBugLabel", "bug")));
		Response.Write(" not found:&nbsp;" + Strings.cs(Convert.ToString(id)) + "</div>");
		Response.Write("<p><a href=bugs.aspx>View ");
		Response.Write(btnet.Util.get_setting("PluralBugLabel", "bug"));
		Response.Write("</a>");
		Response.End(); // inventory: src/BugTracker.Web/btnet/util.cs L1353
	}

	///////////////////////////////////////////////////////////////////////
	public static void display_you_dont_have_permission(HttpResponse Response)
	{
		Response.Write("<link rel=StyleSheet href=btnet.css type=text/css>");
		Response.Write("<p>&nbsp;</p><div class=align>");
		Response.Write("<div class=err>You are not allowed to view this "
			+ Strings.cs(btnet.Util.get_setting("SingularBugLabel", "bug"))
			+ "</div>");
		Response.Write("<p><a href=bugs.aspx>View "
			+ Strings.cs(btnet.Util.capitalize_first_letter(btnet.Util.get_setting
			("PluralBugLabel", "bugs"))) + "</a>");
		Response.End(); // inventory: src/BugTracker.Web/btnet/util.cs L1367
	}


} // end Util
// end namespace
// PORT STATUS: confidence=high todos=0
