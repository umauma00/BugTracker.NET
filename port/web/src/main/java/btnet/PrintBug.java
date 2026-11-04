// Source: src/BugTracker.Web/btnet/print_bug.cs
/*
Copyright 2002-2011 Corey Trager
Distributed under the terms of the GNU General Public License
*/

package btnet;

import btnet.Security.BtnetRoles;
import btnet.Security.ClaimsIdentityExtensions;
import btnet.Security.PermissionLevel;
import btnet.runtime.Convert;
import btnet.runtime.DotNetRegex;
import btnet.runtime.Exceptions;
import btnet.runtime.Match;
import btnet.runtime.RegexOptions;
import btnet.runtime.Strings;
import btnet.runtime.data.DataRow;
import btnet.runtime.data.DataSet;
import btnet.runtime.io.File;
import btnet.runtime.security.IIdentity;
import btnet.runtime.web.HttpContext;
import btnet.runtime.web.HttpResponse;
import btnet.runtime.web.HttpUtility;
import java.util.Objects;

public class PrintBug {

	// inventory: src/BugTracker.Web/btnet/print_bug.cs L19
	private static final DotNetRegex reEmail = new DotNetRegex(
			"([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\."
			+ ")|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})",
			RegexOptions.IgnoreCase
			| RegexOptions.IgnorePatternWhitespace);

	// convert URL's to hyperlinks
	// inventory: src/BugTracker.Web/btnet/print_bug.cs L28
	private static final DotNetRegex reHyperlinks = new DotNetRegex(
			//@"(?<Protocol>\w+):\/\/(?<Domain>[\w.]+\/?)\S*",
			"https?://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]",
			RegexOptions.IgnoreCase
			| RegexOptions.IgnorePatternWhitespace);


	///////////////////////////////////////////////////////////////////////
	public static void print_bug(HttpResponse Response, DataRow dr, IIdentity identity, boolean include_style, boolean images_inline, boolean history_inline, boolean internal_posts)
	{

		int bugid = Convert.ToInt32(dr.get("id"));
		String string_bugid = Convert.ToString(bugid);

		if (include_style) // when sending emails
		{
			Response.Write("\n<style>\n");

			// If this file exists, use it.

			String css_for_email_file = Util.GetAbsolutePath("custom\\btnet_css_for_email.css");

			try
			{
				if (File.Exists(css_for_email_file))
				{
					Response.WriteFile(css_for_email_file);
					Response.Write("\n");
				}
				else
				{
					css_for_email_file = Util.GetAbsolutePath("btnet_base.css");
					Response.WriteFile(css_for_email_file);
					Response.Write("\n");
					css_for_email_file = Util.GetAbsolutePath("custom\\btnet_custom.css");
					if (File.Exists(css_for_email_file))
					{
						Response.WriteFile(css_for_email_file);
						Response.Write("\n");
					}
				}
			}
			// inventory: src/BugTracker.Web/btnet/print_bug.cs L72
			catch (RuntimeException e)
			{
				Util.write_to_log("Exception trying to read css file for email \""
					+ Strings.cs(css_for_email_file)
					+ "\":"
					+ Strings.cs(Exceptions.Message(e)));
			}

			// underline links in the emails to make them more obvious
			Response.Write("\na {text-decoration: underline; }");
			Response.Write("\na:visited {text-decoration: underline; }");
			Response.Write("\na:hover {text-decoration: underline; }");
			Response.Write("\n</style>\n");
		}

		Response.Write("<body style='background:white'>");
		Response.Write("<b>"
			+ Strings.cs(Util.capitalize_first_letter(Util.get_setting("SingularBugLabel", "bug")))
			+ " ID:&nbsp;<a href="
			+ Strings.cs(Util.get_setting("AbsoluteUrlPrefix", "http://127.0.0.1/"))
			+ "edit_bug.aspx?id="
			+ Strings.cs(string_bugid)
			+ ">"
			+ Strings.cs(string_bugid)
			+ "</a>");

		if (Objects.equals(Util.get_setting("EnableMobile", "0"), "1"))
		{
			Response.Write(
				"&nbsp;&nbsp;&nbsp;&nbsp;Mobile link:&nbsp;<a href="
				+ Strings.cs(Util.get_setting("AbsoluteUrlPrefix", "http://127.0.0.1/"))
				+ "mbug.aspx?id="
				+ Strings.cs(string_bugid)
				+ ">"
				+ Strings.cs(Util.get_setting("AbsoluteUrlPrefix", "http://127.0.0.1/"))
				+ "mbug.aspx?id="
				+ Strings.cs(string_bugid)
				+ "</a>");

		}

		Response.Write("<br>");

		Response.Write("Short desc:&nbsp;<a href="
			+ Strings.cs(Util.get_setting("AbsoluteUrlPrefix", "http://127.0.0.1/"))
			+ "edit_bug.aspx?id="
			+ Strings.cs(string_bugid)
			+ ">"
			+ Strings.cs(HttpUtility.HtmlEncode((String) dr.get("short_desc")))
			+ "</a></b><p>");

		// start of the table with the bug fields
		Response.Write("\n<table border=1 cellpadding=3 cellspacing=0>");
		Response.Write("\n<tr><td>Last changed by<td>"
			+ Strings.cs(format_username((String) dr.get("last_updated_user"), (String) dr.get("last_updated_fullname")))
			+ "&nbsp;");
		Response.Write("\n<tr><td>Reported By<td>"
			+ Strings.cs(format_username((String) dr.get("reporter"), (String) dr.get("reporter_fullname")))
			+ "&nbsp;");
		Response.Write("\n<tr><td>Reported On<td>" + Strings.cs(Util.format_db_date_and_time(dr.get("reported_date"))) + "&nbsp;");

		if (ClaimsIdentityExtensions.GetTagsFieldPermissionLevel(identity) > 0)
			Response.Write("\n<tr><td>Tags<td>" + Strings.cs(dr.get("bg_tags")) + "&nbsp;");

		if (ClaimsIdentityExtensions.GetProjectFieldPermissionLevel(identity) > 0)
			Response.Write("\n<tr><td>Project<td>" + Strings.cs(dr.get("current_project")) + "&nbsp;");

		if (ClaimsIdentityExtensions.GetOrgFieldPermissionLevel(identity) > 0)
			Response.Write("\n<tr><td>Organization<td>" + Strings.cs(dr.get("og_name")) + "&nbsp;");

		if (ClaimsIdentityExtensions.GetCategoryFieldPermissionLevel(identity) > 0)
			Response.Write("\n<tr><td>Category<td>" + Strings.cs(dr.get("category_name")) + "&nbsp;");

		if (ClaimsIdentityExtensions.GetPriorityFieldPermissionLevel(identity) > 0)
			Response.Write("\n<tr><td>Priority<td>" + Strings.cs(dr.get("priority_name")) + "&nbsp;");

		if (ClaimsIdentityExtensions.GetAssignedToFieldPermissionLevel(identity) > 0)
			Response.Write("\n<tr><td>Assigned<td>"
				+ Strings.cs(format_username((String) dr.get("assigned_to_username"), (String) dr.get("assigned_to_fullname")))
				+ "&nbsp;");

		if (ClaimsIdentityExtensions.GetStatusFieldPermissionLevel(identity) > 0)
			Response.Write("\n<tr><td>Status<td>" + Strings.cs(dr.get("status_name")) + "&nbsp;");

		if (ClaimsIdentityExtensions.GetUdfFieldPermissionLevel(identity) > 0)
			if (Objects.equals(Util.get_setting("ShowUserDefinedBugAttribute", "1"), "1"))
			{
				Response.Write("\n<tr><td>"
					+ Strings.cs(Util.get_setting("UserDefinedBugAttributeName", "YOUR ATTRIBUTE"))
					+ "<td>"
					+ Strings.cs(dr.get("udf_name")) + "&nbsp;");
			}

		// Get custom column info  (There's an inefficiency here - we just did this
		// same call in get_bug_datarow...)

		// create project custom dropdowns
		if ((int) dr.get("project") != 0)
		{

			var sql = new SQLString("select\r\n"
					+ "\t\t\t\t\tisnull(pj_enable_custom_dropdown1,0) [pj_enable_custom_dropdown1],\r\n"
					+ "\t\t\t\t\tisnull(pj_enable_custom_dropdown2,0) [pj_enable_custom_dropdown2],\r\n"
					+ "\t\t\t\t\tisnull(pj_enable_custom_dropdown3,0) [pj_enable_custom_dropdown3],\r\n"
					+ "\t\t\t\t\tisnull(pj_custom_dropdown_label1,'') [pj_custom_dropdown_label1],\r\n"
					+ "\t\t\t\t\tisnull(pj_custom_dropdown_label2,'') [pj_custom_dropdown_label2],\r\n"
					+ "\t\t\t\t\tisnull(pj_custom_dropdown_label3,'') [pj_custom_dropdown_label3]\r\n"
					+ "\t\t\t\t\tfrom projects where pj_id = @pj");

			sql = sql.AddParameterWithValue("pj", Convert.ToString((int) dr.get("project")));

			DataRow project_dr = DbUtil.get_datarow(sql);


			if (project_dr != null)
			{
				for (int i = 1; i < 4; i++)
				{
					if ((int) project_dr.get("pj_enable_custom_dropdown" + Strings.cs(Convert.ToString(i))) == 1)
					{
						Response.Write("\n<tr><td>");
						Response.Write(project_dr.get("pj_custom_dropdown_label" + Strings.cs(Convert.ToString(i))));
						Response.Write("<td>");
						Response.Write(dr.get("bg_project_custom_dropdown_value" + Strings.cs(Convert.ToString(i))));
						Response.Write("&nbsp;");
					}
				}
			}
		}



		Response.Write("\n</table><p>"); // end of the table with the bug fields

		// Relationships
		if (Objects.equals(Util.get_setting("EnableRelationships", "0"), "1"))
		{
			write_relationships(Response, bugid);
		}

		// Tasks
		if (Objects.equals(Util.get_setting("EnableTasks", "0"), "1"))
		{
			write_tasks(Response, bugid);
		}


		DataSet ds_posts = get_bug_posts(bugid, ClaimsIdentityExtensions.GetIsExternalUser(identity), history_inline);
		write_posts(
			ds_posts,
			Response,
			bugid,
			0,
			false, /* don't write links */
			images_inline,
			history_inline,
			internal_posts, identity);

		Response.Write("</body>");

	}


	///////////////////////////////////////////////////////////////////////
	protected static void write_tasks(HttpResponse Response, int bugid)
	{

		DataSet ds_tasks = Util.get_all_tasks(HttpContext.Current().User().Identity(), bugid);

		if (ds_tasks.Tables().get(0).Rows().Count() > 0)
		{
			Response.Write("<b>Tasks</b><p>");

			SortableHtmlTable.create_nonsortable_from_dataset(
				Response, ds_tasks);
		}

	}

	///////////////////////////////////////////////////////////////////////
	protected static void write_relationships(HttpResponse Response, int bugid)
	{

		var sql = new SQLString("select bg_id [id],\r\n"
				+ "\t\t\t\tbg_short_desc [desc],\r\n"
				+ "\t\t\t\tre_type [comment],\r\n"
				+ "\t\t\t\tcase\r\n"
				+ "\t\t\t\t\twhen re_direction = 0 then ''\r\n"
				+ "\t\t\t\t\twhen re_direction = 2 then @child\r\n"
				+ "\t\t\t\t\telse @parent end [parent/child]\r\n"
				+ "\t\t\t\tfrom bug_relationships\r\n"
				+ "\t\t\t\tinner join bugs on re_bug2 = bg_id\r\n"
				+ "\t\t\t\twhere re_bug1 = @bg\r\n"
				+ "\t\t\t\torder by 1");

		sql = sql.AddParameterWithValue("bg", Convert.ToString(bugid));
		sql = sql.AddParameterWithValue("parent", "parent of " + Strings.cs(Convert.ToString(bugid)));
		sql = sql.AddParameterWithValue("child", "child of " + Strings.cs(Convert.ToString(bugid)));
		DataSet ds_relationships = DbUtil.get_dataset(sql);

		if (ds_relationships.Tables().get(0).Rows().Count() > 0)
		{
			Response.Write("<b>Relationships</b><p><table border=1 class=datat><tr>");
			Response.Write("<td class=datah valign=bottom>id</td>");
			Response.Write("<td class=datah valign=bottom>desc</td>");
			Response.Write("<td class=datah valign=bottom>comment</td>");
			Response.Write("<td class=datah valign=bottom>parent/child</td>");

			for (DataRow dr_relationships : ds_relationships.Tables().get(0).Rows())
			{
				Response.Write("<tr>");

				Response.Write("<td class=datad valign=top align=right>");
				Response.Write(Convert.ToString((int) dr_relationships.get("id")));

				Response.Write("<td class=datad valign=top>");
				Response.Write(Convert.ToString(dr_relationships.get("desc")));

				Response.Write("<td class=datad valign=top>");
				Response.Write(Convert.ToString(dr_relationships.get("comment")));

				Response.Write("<td class=datad valign=top>");
				Response.Write(Convert.ToString(dr_relationships.get("parent/child")));

			}

			Response.Write("</table><p>");

		}

	}

	///////////////////////////////////////////////////////////////////////
	public static int write_posts(DataSet ds_posts, HttpResponse Response, int bugid, int permission_level, boolean write_links, boolean images_inline, boolean history_inline, boolean internal_posts, IIdentity identity)
	{

		if (Objects.equals(Util.get_setting("ForceBordersInEmails", "0"), "1"))
		{
			Response.Write("\n<table id='posts_table' border=1 cellpadding=0 cellspacing=3>");
		}
		else
		{
			Response.Write("\n<table id='posts_table' border=0 cellpadding=0 cellspacing=3>");
		}

		int post_cnt = ds_posts.Tables().get(0).Rows().Count();

		int bp_id;
		int prev_bp_id = -1;


		for (DataRow dr : ds_posts.Tables().get(0).Rows())
		{

			if (!internal_posts)
			{
				if ((int) dr.get("bp_hidden_from_external_users") == 1)
				{
					continue;
				}
			}

			bp_id = (int) dr.get("bp_id");

			if (Objects.equals((String) dr.get("bp_type"), "update"))
			{

				String comment = (String) dr.get("bp_comment");

				if (ClaimsIdentityExtensions.GetTagsFieldPermissionLevel(identity) == PermissionLevel.None
				&& comment.startsWith("changed tags from"))
					continue;

				if (ClaimsIdentityExtensions.GetProjectFieldPermissionLevel(identity) == PermissionLevel.None
				&& comment.startsWith("changed project from"))
					continue;

				if (ClaimsIdentityExtensions.GetOrgFieldPermissionLevel(identity) == PermissionLevel.None
				&& comment.startsWith("changed organization from"))
					continue;

				if (ClaimsIdentityExtensions.GetCategoryFieldPermissionLevel(identity) == PermissionLevel.None
				&& comment.startsWith("changed category from"))
					continue;

				if (ClaimsIdentityExtensions.GetPriorityFieldPermissionLevel(identity) == PermissionLevel.None
				&& comment.startsWith("changed priority from"))
					continue;

				if (ClaimsIdentityExtensions.GetAssignedToFieldPermissionLevel(identity) == PermissionLevel.None
				&& comment.startsWith("changed assigned_to from"))
					continue;

				if (ClaimsIdentityExtensions.GetStatusFieldPermissionLevel(identity) == PermissionLevel.None
				&& comment.startsWith("changed status from"))
					continue;

				if (ClaimsIdentityExtensions.GetUdfFieldPermissionLevel(identity) == PermissionLevel.None
				&& comment.startsWith("changed " + Strings.cs(Util.get_setting("UserDefinedBugAttributeName", "YOUR ATTRIBUTE")) + " from"))
					continue;


			}

			if (bp_id == prev_bp_id)
			{
				// show another attachment
				write_email_attachment(Response, bugid, dr, write_links, images_inline);
			}
			else
			{
				// show the comment and maybe an attachment
				if (prev_bp_id != -1) {
					Response.Write("\n</table>"); // end the previous table
				}

				write_post(Response, bugid, permission_level, dr, bp_id, write_links, images_inline,
					ClaimsIdentityExtensions.IsInRole(identity, BtnetRoles.Admin),
					ClaimsIdentityExtensions.GetCanEditAndDeletePosts(identity),
					ClaimsIdentityExtensions.GetIsExternalUser(identity));


				if (!Objects.equals(Convert.ToString(dr.get("ba_file")), "")) // intentially "ba"
				{
					write_email_attachment(Response, bugid, dr, write_links, images_inline);
				}
				prev_bp_id = bp_id;
			}

		}

		if (prev_bp_id != -1)
		{
			Response.Write("\n</table>"); // end the previous table
		}

		Response.Write("\n</table>");

		return post_cnt;
	}

	///////////////////////////////////////////////////////////////////////
	private static void write_post(
		HttpResponse Response,
		int bugid,
		int permission_level,
		DataRow dr,
		int post_id,
		boolean write_links,
		boolean images_inline,
		boolean this_is_admin,
		boolean this_can_edit_and_delete_posts,
		boolean this_external_user)
	{
		String type = (String) dr.get("bp_type");

		String string_post_id = Convert.ToString(post_id);
		String string_bug_id = Convert.ToString(bugid);

		if ((int) dr.get("seconds_ago") < 2 && write_links)
		{
			// for the animation effect
			Response.Write("\n\n<tr><td class=cmt name=new_post>\n<table width=100%>\n<tr><td align=left>");
		}
		else
		{
			Response.Write("\n\n<tr><td class=cmt>\n<table width=100%>\n<tr><td align=left>");
		}


		/*
			Format one of the following:

			changed by
			email sent to
			email received from
			file attached by
			comment posted by

		*/

		if (Objects.equals(type, "update"))
		{
			if (write_links)
			{
				Response.Write("<img src=database.png align=top>&nbsp;");
			}

			// posted by
			Response.Write("<span class=pst>changed by ");
			Response.Write(format_email_username(
				write_links,
				bugid,
				permission_level,
				(String) dr.get("us_email"),
				(String) dr.get("us_username"),
				(String) dr.get("us_fullname")));
		}
		else if (Objects.equals(type, "sent"))
		{
			if (write_links)
			{
				Response.Write("<img src=email_edit.png align=top>&nbsp;");
			}

			Response.Write("<span class=pst>email <a name=" + Strings.cs(Convert.ToString(post_id)) + "></a>" + Strings.cs(Convert.ToString(post_id)) + " sent to ");

			if (write_links)
			{
				Response.Write(format_email_to(
					bugid,
					HttpUtility.HtmlEncode((String) dr.get("bp_email_to"))));
			}
			else
			{
				Response.Write(HttpUtility.HtmlEncode((String) dr.get("bp_email_to")));
			}

			if (!Objects.equals((String) dr.get("bp_email_cc"), ""))
			{
				Response.Write(", cc: ");

				if (write_links)
				{
					Response.Write(format_email_to(
						bugid,
						HttpUtility.HtmlEncode((String) dr.get("bp_email_cc"))));
				}
				else
				{
					Response.Write(HttpUtility.HtmlEncode((String) dr.get("bp_email_cc")));
				}

				Response.Write(", ");
			}

			Response.Write(" by ");

			Response.Write(format_email_username(
				write_links,
				bugid,
				permission_level,
				(String) dr.get("us_email"),
				(String) dr.get("us_username"),
				(String) dr.get("us_fullname")));

		}
		else if (Objects.equals(type, "received"))
		{
			if (write_links)
			{
				Response.Write("<img src=email_open.png align=top>&nbsp;");
			}
			Response.Write("<span class=pst>email <a name=" + Strings.cs(Convert.ToString(post_id)) + "></a>" + Strings.cs(Convert.ToString(post_id)) + " received from ");
			if (write_links)
			{
				Response.Write(format_email_from(
					post_id,
					(String) dr.get("bp_email_from")));
			}
			else
			{
				Response.Write((String) dr.get("bp_email_from"));
			}
		}
		else if (Objects.equals(type, "file"))
		{
			if ((int) dr.get("bp_hidden_from_external_users") == 1)
			{
				Response.Write("<div class=private>Internal Only!</div>");
			}
			Response.Write("<span class=pst>file <a name=" + Strings.cs(Convert.ToString(post_id)) + "></a>" + Strings.cs(Convert.ToString(post_id)) + " attached by ");
			Response.Write(format_email_username(
				write_links,
				bugid,
				permission_level,
				(String) dr.get("us_email"),
				(String) dr.get("us_username"),
				(String) dr.get("us_fullname")));
		}
		else if (Objects.equals(type, "comment"))
		{
			if ((int) dr.get("bp_hidden_from_external_users") == 1)
			{
				Response.Write("<div class=private>Internal Only!</div>");
			}

			if (write_links)
			{
				Response.Write("<img src=comment.png align=top>&nbsp;");
			}

			Response.Write("<span class=pst>comment <a name=" + Strings.cs(Convert.ToString(post_id)) + "></a>" + Strings.cs(Convert.ToString(post_id)) + " posted by ");
			Response.Write(format_email_username(
				write_links,
				bugid,
				permission_level,
				(String) dr.get("us_email"),
				(String) dr.get("us_username"),
				(String) dr.get("us_fullname")));
		}
		else
		{
			// inventory: src/BugTracker.Web/btnet/print_bug.cs L575
			// Debug.Assert dropped (RULEBOOK §2A)
		}


		// Format the date
		Response.Write(" on ");
		Response.Write(Util.format_db_date_and_time(dr.get("bp_date")));
		Response.Write(", ");
		Response.Write(Util.how_long_ago((int) dr.get("seconds_ago")));
		Response.Write("</span>");


		// Write the links

		if (write_links)
		{

			Response.Write("<td align=right>&nbsp;");

			if (permission_level != PermissionLevel.ReadOnly)
			{
				if (Objects.equals(type, "comment") || Objects.equals(type, "sent") || Objects.equals(type, "received"))
				{
					Response.Write("&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;'");
					Response.Write(" href=send_email.aspx?quote=1&bp_id=" + Strings.cs(string_post_id) + "&reply=forward");
					Response.Write(">forward</a>");
				}
			}

			// format links for responding to email
			if (Objects.equals(type, "received"))
			{
				if (this_is_admin
				|| (this_can_edit_and_delete_posts
				&& permission_level == PermissionLevel.All))
				{

					// This delete leaves debris around, but it's better than nothing
					Response.Write("&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;'");
					Response.Write(" href=delete_comment.aspx?id="
						+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id));
					Response.Write(">delete</a>");

				}

				if (permission_level != PermissionLevel.ReadOnly)
				{
					Response.Write("&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;'");
					Response.Write(" href=send_email.aspx?quote=1&bp_id=" + Strings.cs(string_post_id));
					Response.Write(">reply</a>");

					Response.Write("&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;'");
					Response.Write(" href=send_email.aspx?quote=1&bp_id=" + Strings.cs(string_post_id) + "&reply=all");
					Response.Write(">reply all</a>");
				}

			}
			else if (Objects.equals(type, "file"))
			{

				if (this_is_admin
				|| (this_can_edit_and_delete_posts
				&& permission_level == PermissionLevel.All))
				{
					Response.Write("&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;'");
					Response.Write(" href=edit_attachment.aspx?id="
						+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id));
					Response.Write(">edit</a>");

					Response.Write("&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;'");
					Response.Write(" href=delete_attachment.aspx?id="
						+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id));
					Response.Write(">delete</a>");

				}
			}
			else if (Objects.equals(type, "comment"))
			{
				if (this_is_admin
				|| (this_can_edit_and_delete_posts
				&& permission_level == PermissionLevel.All))
				{
					Response.Write("&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;'");
					Response.Write(" href=edit_comment.aspx?id="
						+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id));
					Response.Write(">edit</a>");

					Response.Write("&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;'");
					Response.Write(" href=delete_comment.aspx?id="
						+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id));
					Response.Write(">delete</a>");
				}
			}


			// custom bug link
			if (!Objects.equals(Util.get_setting("CustomPostLinkLabel", ""), ""))
			{

				String custom_post_link = "&nbsp;&nbsp;&nbsp;<a class=warn style='font-size: 8pt;' href="
					+ Strings.cs(Util.get_setting("CustomPostLinkUrl", ""))
					+ "?postid="
					+ Strings.cs(string_post_id)
					+ ">"
					+ Strings.cs(Util.get_setting("CustomPostLinkLabel", ""))
					+ "</a>";

				Response.Write(custom_post_link);

			}
		}

		Response.Write("\n</table>\n<table border=0>\n<tr><td>");
		// the text itself
		String comment = (String) dr.get("bp_comment");
		String comment_type = (String) dr.get("bp_content_type");

		if (write_links)
		{
			comment = format_comment(post_id, comment, comment_type);
		}
		else
		{
			comment = format_comment(0, comment, comment_type);
		}



		if (Objects.equals(type, "file"))
		{
			if (comment.length() > 0)
			{
				Response.Write(comment);
				Response.Write("<p>");
			}

			Response.Write("<span class=pst>");
			if (write_links)
			{
				Response.Write("<img src=attach.gif>");
			}
			Response.Write("attachment:&nbsp;</span><span class=cmt_text>");
			Response.Write(dr.get("bp_file"));
			Response.Write("</span>");

			if (write_links)
			{
				if (!Objects.equals((String) dr.get("bp_content_type"), "text/html") || Objects.equals(Util.get_setting("ShowPotentiallyDangerousHtml", "0"), "1"))
				{
					Response.Write("&nbsp;&nbsp;&nbsp;<a target=_blank style='font-size: 8pt;'");
					Response.Write(" href=view_attachment.aspx?download=0&id="
						+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id));
					Response.Write(">view</a>");
				}

				Response.Write("&nbsp;&nbsp;&nbsp;<a target=_blank style='font-size: 8pt;'");
				Response.Write(" href=view_attachment.aspx?download=1&id="
					+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id));
				Response.Write(">save</a>");
			}

			Response.Write("<p><span class=pst>size: ");
			Response.Write(dr.get("bp_size"));
			Response.Write("&nbsp;&nbsp;&nbsp;content-type: ");
			Response.Write(dr.get("bp_content_type"));
			Response.Write("</span>");


		}
		else
		{
			Response.Write(comment);
		}


		// maybe show inline images
		if (Objects.equals(type, "file"))
		{
			if (images_inline)
			{
				String file = Convert.ToString(dr.get("bp_file"));
				write_file_inline(Response, file, string_post_id, string_bug_id, (String) dr.get("bp_content_type"));
			}
		}

	}


	///////////////////////////////////////////////////////////////////////
	private static void write_email_attachment(HttpResponse Response, int bugid, DataRow dr, boolean write_links, boolean images_inline)
	{

		String string_post_id = Convert.ToString(dr.get("ba_id")); // intentially "ba"
		String string_bug_id = Convert.ToString(bugid);

		Response.Write("\n<p><span class=pst>");
		if (write_links)
		{
			Response.Write("<img src=attach.gif>");
		}
		Response.Write("attachment:&nbsp;</span>");
		Response.Write(dr.get("ba_file")); // intentially "ba"
		Response.Write("&nbsp;&nbsp;&nbsp;&nbsp;");

		if (write_links)
		{
			if (!Objects.equals((String) dr.get("bp_content_type"), "text/html") || Objects.equals(Util.get_setting("ShowPotentiallyDangerousHtml", "0"), "1"))
			{
				Response.Write("<a target=_blank href=view_attachment.aspx?download=0&id=");
				Response.Write(string_post_id);
				Response.Write("&bug_id=");
				Response.Write(string_bug_id);
				Response.Write(">view</a>");
			}

			Response.Write("&nbsp;&nbsp;&nbsp;<a target=_blank href=view_attachment.aspx?download=1&id=");
			Response.Write(string_post_id);
			Response.Write("&bug_id=");
			Response.Write(string_bug_id);
			Response.Write(">save</a>");
		}

		if (images_inline)
		{
			String file = Convert.ToString(dr.get("ba_file"));  // intentially "ba"
			write_file_inline(Response, file, string_post_id, string_bug_id, (String) dr.get("ba_content_type"));

		}

		Response.Write("<p><span class=pst>size: ");
		Response.Write(dr.get("ba_size"));
		Response.Write("&nbsp;&nbsp;&nbsp;content-type: ");
		Response.Write(dr.get("ba_content_type"));
		Response.Write("</span>");

	}


	///////////////////////////////////////////////////////////////////////
	private static void write_file_inline(
		HttpResponse Response,
		String filename,
		String string_post_id,
		String string_bug_id,
		String content_type)
	{

		if (Objects.equals(content_type, "image/gif")
		|| Objects.equals(content_type, "image/jpg")
		|| Objects.equals(content_type, "image/jpeg")
		|| Objects.equals(content_type, "image/pjpeg")
		|| Objects.equals(content_type, "image/png")
		|| Objects.equals(content_type, "image/x-png")
		|| Objects.equals(content_type, "image/bmp")
		|| Objects.equals(content_type, "image/tiff"))
		{
			Response.Write("<p>"
				+ "<a href=javascript:resize_image('im" + Strings.cs(string_post_id) + "',1.5)>" + "[+]</a>&nbsp;"
				+ "<a href=javascript:resize_image('im" + Strings.cs(string_post_id) + "',.6)>" + "[-]</a>"
				+ "<br><img id=im" + Strings.cs(string_post_id)
				+ " src=view_attachment.aspx?download=0&id="
				+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id)
				+ ">");
		}
		else if (Objects.equals(content_type, "text/plain")
		|| Objects.equals(content_type, "text/xml")
		|| Objects.equals(content_type, "text/css")
		|| Objects.equals(content_type, "text/js")
		|| (Objects.equals(content_type, "text/html") && Objects.equals(Util.get_setting("ShowPotentiallyDangerousHtml", "0"), "1")))
		{
			Response.Write("<p>"
				+ "<a href=javascript:resize_iframe('if" + Strings.cs(string_post_id) + "',200)>" + "[+]</a>&nbsp;"
				+ "<a href=javascript:resize_iframe('if" + Strings.cs(string_post_id) + "',-200)>" + "[-]</a>"
				+ "<br><iframe id=if"
				+ Strings.cs(string_post_id)
				+ " width=780 height=200 src=view_attachment.aspx?download=0&id="
				+ Strings.cs(string_post_id) + "&bug_id=" + Strings.cs(string_bug_id)
				+ "></iframe>");
		}

	}

	///////////////////////////////////////////////////////////////////////
	public static String format_email_username(
		boolean write_links,
		int bugid,
		int permission_level,
		String email,
		String username,
		String fullname)
	{
		if (email != null && !Objects.equals(email, "") && write_links && permission_level != PermissionLevel.ReadOnly)
		{
			return "<a href="
			+ Strings.cs(Util.get_setting("AbsoluteUrlPrefix", "http://127.0.0.1/"))
			+ "send_email.aspx?bg_id="
			+ Strings.cs(Convert.ToString(bugid))
			+ "&to="
			+ Strings.cs(email)
			+ ">"
			+ Strings.cs(format_username(username, fullname))
			+ "</a>";
		}
		else
		{
			return format_username(username, fullname);
		}

	}

	///////////////////////////////////////////////////////////////////////
	private static String format_email_to(int bugid, String email)
	{
		return "<a href="
		+ Strings.cs(Util.get_setting("AbsoluteUrlPrefix", "http://127.0.0.1/"))
		+ "send_email.aspx?bg_id=" + Strings.cs(Convert.ToString(bugid))
		+ "&to=" + Strings.cs(HttpUtility.UrlEncode(HttpUtility.HtmlDecode(email))) + ">"
		+ Strings.cs(email)
		+ "</a>";
	}


	///////////////////////////////////////////////////////////////////////
	private static String format_email_from(int comment_id, String from)
	{

		String display_part = "";
		String email_part = "";
		int pos = from.indexOf("<"); // "

		if (pos > 0)
		{
			display_part = from.substring(0, pos);
			email_part = from.substring(pos + 1, (pos + 1) + ((from.length() - pos) - 2));
		}
		else
		{
			email_part = from;
		}

		return Strings.cs(display_part)
			+ " <a href="
			+ Strings.cs(Util.get_setting("AbsoluteUrlPrefix", "http://127.0.0.1/"))
			+ "send_email.aspx?bp_id="
			+ Strings.cs(Convert.ToString(comment_id))
			+ ">"
			+ Strings.cs(email_part)
			+ "</a>";

	}

	///////////////////////////////////////////////////////////////////////
	private static String format_comment(int post_id, String s1, String t1)
	{
		String s2;
		String link_marker;

		if (!Objects.equals(t1, "text/html"))
		{
			s2 = HttpUtility.HtmlEncode(s1);

			if (post_id != 0)
			{
				// convert urls to links
				s2 = reHyperlinks.Replace(
					s2,
					PrintBug::convert_to_hyperlink);

				// This code doesn't perform well if s2 is one big string, no spaces, line breaks

				// convert email addresses to send_email links
				// inventory: src/BugTracker.Web/btnet/print_bug.cs L948
				s2 = reEmail.Replace(
					s2,
					m ->
					{
						return
						  "<a href=send_email.aspx?bp_id="
						  + Strings.cs(Convert.ToString(post_id))
						  + "&to="
						  + Strings.cs(m.Value())
						  + ">"
						  + Strings.cs(m.Value())
						  + "</a>";
					}
					);
			}

			s2 = s2.replace("\n", "<br>");
			s2 = s2.replace("  ", " &nbsp;");
			s2 = s2.replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");

		}
		else
		{
			s2 = s1;
		}

		// convert references to other bugs to links
		link_marker = Util.get_setting("BugLinkMarker", "bugid#");
		// inventory: src/BugTracker.Web/btnet/print_bug.cs L974
		DotNetRegex reLinkMarker = new DotNetRegex(Strings.cs(link_marker) + "([0-9]+)");
		s2 = reLinkMarker.Replace(
			s2,
			PrintBug::convert_bug_link);

		return "<span class=cmt_text>" + Strings.cs(s2) + "</span>";
	}

	///////////////////////////////////////////////////////////////////////
	// inventory: src/BugTracker.Web/btnet/print_bug.cs L986
	private static String convert_to_email(Match m)
	{
		// Get the matched string.
		return Strings.Format("<a href='mailto:{0}'>{0}</a>", m.Value());
	}

	///////////////////////////////////////////////////////////////////////
	// inventory: src/BugTracker.Web/btnet/print_bug.cs L995
	private static String convert_bug_link(Match m)
	{
		return "<a href="
			+ Strings.cs(Util.get_setting("AbsoluteUrlPrefix", "http://127.0.0.1/"))
			+ "edit_bug.aspx?id="
			+ Strings.cs(m.Groups().get(1).Value())
			+ ">"
			+ Strings.cs(m.Value())
			+ "</a>";
	}

	///////////////////////////////////////////////////////////////////////
	// inventory: src/BugTracker.Web/btnet/print_bug.cs L1004
	private static String convert_to_hyperlink(Match m)
	{
		return Strings.Format("<a target=_blank href='{0}'>{0}</a>", m.Value());
	}

	///////////////////////////////////////////////////////////////////////
	private static String format_username(String username, String fullname)
	{

		if (Objects.equals(Util.get_setting("UseFullNames", "0"), "0"))
		{
			return username;
		}
		else
		{
			return fullname;
		}
	}


	///////////////////////////////////////////////////////////////////////
	public static DataSet get_bug_posts(int bugid, boolean external_user, boolean history_inline)
	{
		SQLString sql = new SQLString("\r\n"
				+ "/* get_bug_posts */\r\n"
				+ "select\r\n"
				+ "a.bp_bug,\r\n"
				+ "a.bp_comment,\r\n"
				+ "isnull(us_username,'') [us_username],\r\n"
				+ "case rtrim(us_firstname)\r\n"
				+ "\twhen null then isnull(us_lastname, '')\r\n"
				+ "\twhen '' then isnull(us_lastname, '')\r\n"
				+ "\telse isnull(us_lastname + ', ' + us_firstname,'')\r\n"
				+ "\tend [us_fullname],\r\n"
				+ "isnull(us_email,'') [us_email],\r\n"
				+ "a.bp_date,\r\n"
				+ "datediff(s,a.bp_date,getdate()) [seconds_ago],\r\n"
				+ "a.bp_id,\r\n"
				+ "a.bp_type,\r\n"
				+ "isnull(a.bp_email_from,'') bp_email_from,\r\n"
				+ "isnull(a.bp_email_to,'') bp_email_to,\r\n"
				+ "isnull(a.bp_email_cc,'') bp_email_cc,\r\n"
				+ "isnull(a.bp_file,'') bp_file,\r\n"
				+ "isnull(a.bp_size,0) bp_size,\r\n"
				+ "isnull(a.bp_content_type,'') bp_content_type,\r\n"
				+ "a.bp_hidden_from_external_users,\r\n"
				+ "isnull(ba.bp_file,'') ba_file,  -- intentionally ba\r\n"
				+ "isnull(ba.bp_id,'') ba_id, -- intentionally ba\r\n"
				+ "isnull(ba.bp_size,'') ba_size,  -- intentionally ba\r\n"
				+ "isnull(ba.bp_content_type,'') ba_content_type -- intentionally ba\r\n"
				+ "from bug_posts a\r\n"
				+ "left outer join users on us_id = a.bp_user\r\n"
				+ "left outer join bug_posts ba on ba.bp_parent = a.bp_id and ba.bp_bug = a.bp_bug\r\n"
				+ "where a.bp_bug = @id\r\n"
				+ "and a.bp_parent is null");


		if (!history_inline)
		{
			sql.Append("\n and a.bp_type <> 'update'");
		}

		if (external_user)
		{
			sql.Append("\n and a.bp_hidden_from_external_users = 0");
		}

		sql.Append("\n order by a.bp_id ");
		sql.Append(Util.get_setting("CommentSortOrder", "desc"));
		sql.Append(", ba.bp_parent, ba.bp_id");

		sql = sql.AddParameterWithValue("@id", Convert.ToString(bugid));

		return DbUtil.get_dataset(sql);

	}


	// Second top-level type of print_bug.cs: static nested class (RULEBOOK §4)
	public static class WritePostResult
	{
		public int post_cnt;
		public String post_string;
		public String related_bugs;
	}

} // end PrintBug
// PORT STATUS: confidence=high todos=0
