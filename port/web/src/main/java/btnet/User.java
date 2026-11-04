// Source: src/BugTracker.Web/btnet/user.cs
/*
Copyright 2002-2011 Corey Trager
Distributed under the terms of the GNU General Public License
*/
package btnet;

import btnet.Security.PermissionLevel;
import btnet.runtime.Collections2;
import btnet.runtime.Convert;
import btnet.runtime.DBNull;
import btnet.runtime.Exceptions;
import btnet.runtime.Strings;
import btnet.runtime.data.DataRow;
import btnet.runtime.data.DataSet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class User
{
    public int usid = 0;
    public String username = "";
    public String fullname = "";
    public String email = "";
    public boolean is_admin = false;
    public boolean is_project_admin = false;
    public boolean is_guest = false;
    public boolean adds_not_allowed = false;
    public int bugs_per_page = 10;
    public boolean enable_popups = true;
    public boolean use_fckeditor = false;

    public boolean external_user = false;
    public boolean can_only_see_own_reported = false;
    public boolean can_edit_sql = false;
    public boolean can_delete_bug = false;

    public boolean can_edit_and_delete_posts = false;
    public boolean can_merge_bugs = false;
    public boolean can_mass_edit_bugs = false;

    public boolean can_use_reports = false;
    public boolean can_edit_reports = false;
    public boolean can_be_assigned_to = true;

    public boolean can_view_tasks = true;
    public boolean can_edit_tasks = true;
    public boolean can_search = true;
    public boolean can_assign_to_internal_users = false;

    public int other_orgs_permission_level = PermissionLevel.All;
    public int org = 0;
    public String org_name = "";
    public int forced_project = 0;

    public int assigned_to_field_permission_level = PermissionLevel.All;
    public int status_field_permission_level = PermissionLevel.All;
    public int category_field_permission_level = PermissionLevel.All;
    public int tags_field_permission_level = PermissionLevel.All;
    public int priority_field_permission_level = PermissionLevel.All;
    public int project_field_permission_level = PermissionLevel.All;
    public int org_field_permission_level = PermissionLevel.All;
    public int udf_field_permission_level = PermissionLevel.All;

    public Map<String, Integer> dict_custom_field_permission_level = new LinkedHashMap<>();

    public void set_from_db(DataRow dr)
    {
        this.usid = Convert.ToInt32(dr.get("us_id"));
        this.username = (String) dr.get("us_username");
        this.email = (String) dr.get("us_email");

        this.bugs_per_page = Convert.ToInt32(dr.get("us_bugs_per_page"));
        if (Objects.equals(Util.get_setting("DisableFCKEditor", "0"), "1"))
        {
            this.use_fckeditor = false;
        }
        else
        {
            this.use_fckeditor = Convert.ToBoolean(dr.get("us_use_fckeditor"));
        }
        this.enable_popups = Convert.ToBoolean(dr.get("us_enable_bug_list_popups"));

        this.external_user = Convert.ToBoolean(dr.get("og_external_user"));
        this.can_only_see_own_reported = Convert.ToBoolean(dr.get("og_can_only_see_own_reported"));
        this.can_edit_sql = Convert.ToBoolean(dr.get("og_can_edit_sql"));
        this.can_delete_bug = Convert.ToBoolean(dr.get("og_can_delete_bug"));
        this.can_edit_and_delete_posts = Convert.ToBoolean(dr.get("og_can_edit_and_delete_posts"));
        this.can_merge_bugs = Convert.ToBoolean(dr.get("og_can_merge_bugs"));
        this.can_mass_edit_bugs = Convert.ToBoolean(dr.get("og_can_mass_edit_bugs"));
        this.can_use_reports = Convert.ToBoolean(dr.get("og_can_use_reports"));
        this.can_edit_reports = Convert.ToBoolean(dr.get("og_can_edit_reports"));
        this.can_be_assigned_to = Convert.ToBoolean(dr.get("og_can_be_assigned_to"));
        this.can_view_tasks = Convert.ToBoolean(dr.get("og_can_view_tasks"));
        this.can_edit_tasks = Convert.ToBoolean(dr.get("og_can_edit_tasks"));
        this.can_search = Convert.ToBoolean(dr.get("og_can_search"));
        this.can_assign_to_internal_users = Convert.ToBoolean(dr.get("og_can_assign_to_internal_users"));
        this.other_orgs_permission_level = (int) dr.get("og_other_orgs_permission_level");
        this.org = (int) dr.get("og_id");
        this.org_name = (String) dr.get("og_name");
        this.forced_project = (int) dr.get("us_forced_project");

        this.category_field_permission_level = (int) dr.get("og_category_field_permission_level");

        if (Objects.equals(Util.get_setting("EnableTags", "0"), "1"))
        {
            this.tags_field_permission_level = (int) dr.get("og_tags_field_permission_level");
        }
        else
        {
            this.tags_field_permission_level = PermissionLevel.None;
        }
        this.priority_field_permission_level = (int) dr.get("og_priority_field_permission_level");
        this.assigned_to_field_permission_level = (int) dr.get("og_assigned_to_field_permission_level");
        this.status_field_permission_level = (int) dr.get("og_status_field_permission_level");
        this.project_field_permission_level = (int) dr.get("og_project_field_permission_level");
        this.org_field_permission_level = (int) dr.get("og_org_field_permission_level");
        this.udf_field_permission_level = (int) dr.get("og_udf_field_permission_level");

        // field permission for custom fields
        DataSet ds_custom = Util.get_custom_columns();
        for (DataRow dr_custom : ds_custom.Tables().get(0).Rows())
        {
            String bg_name = (String) dr_custom.get("name");
            String og_name = "og_"
                + Strings.cs((String) dr_custom.get("name"))
                + "_field_permission_level";

            try
            {
                Object obj = dr.get(og_name);
                if (obj == DBNull.Value)
                {
                    Collections2.set(dict_custom_field_permission_level, bg_name, PermissionLevel.All);
                }
                else
                {
                    Collections2.set(dict_custom_field_permission_level, bg_name, (int) dr.get(og_name));
                }
            }

            // inventory: src/BugTracker.Web/btnet/user.cs L137
            catch (RuntimeException ex)
            {
                btnet.Util.write_to_log("exception looking for " + Strings.cs(og_name) + ":" + Strings.cs(Exceptions.Message(ex)));

                // automatically add it if it's missing
                SQLString sql = new SQLString("alter table orgs add [@name] int null");
                sql.AddParameterWithValue("name", og_name);
                btnet.DbUtil.execute_nonquery(sql);
                Collections2.set(dict_custom_field_permission_level, bg_name, PermissionLevel.All);
            }

        }

        if (Strings.Trim((String) dr.get("us_firstname")).length() == 0)
        {
            this.fullname = (String) dr.get("us_lastname");
        }
        else
        {
            this.fullname = Strings.cs((String) dr.get("us_lastname")) + ", " + Strings.cs((String) dr.get("us_firstname"));
        }


        if ((int) dr.get("us_admin") == 1)
        {
            this.is_admin = true;
        }
        else
        {
            if ((int) dr.get("project_admin") > 0)
            {
                this.is_project_admin = true;
            }
            else
            {
                if (Objects.equals(Strings.ToLower(this.username), "guest"))
                {
                    this.is_guest = true;
                }
            }
        }


        // if user is forced to a specific project, and doesn't have
        // at least reporter permission on that project, than user
        // can't add bugs
        if ((int) dr.get("us_forced_project") != 0)
        {
            if ((int) dr.get("pu_permission_level") == PermissionLevel.ReadOnly
            || (int) dr.get("pu_permission_level") == PermissionLevel.None)
            {
                this.adds_not_allowed = true;
            }
        }
    }

    public static int copy_user(
        String username,
        String email,
        String firstname,
        String lastname,
        String signature,
        int salt,
        String password,
        String template_username,
        boolean use_domain_as_org_name)
    {
        // get all the org columns

        btnet.Util.write_to_log("copy_user creating " + Strings.cs(username) + " from template user " + Strings.cs(template_username));
        StringBuilder org_columns = new StringBuilder();

        SQLString sql = new SQLString("");

        if (use_domain_as_org_name)
        {
            sql.Append(" /* get org cols */\n"
            + "select sc.name\n"
            + "from syscolumns sc\n"
            + "inner join sysobjects so on sc.id = so.id\n"
            + "where so.name = 'orgs'\n"
            + "and sc.name not in ('og_id', 'og_name', 'og_domain')");

            DataSet ds = btnet.DbUtil.get_dataset(sql);
            for (DataRow dr : ds.Tables().get(0).Rows())
            {
                org_columns.append(",");
                org_columns.append("[");
                org_columns.append(Strings.cs(Convert.ToString(dr.get("name"))));
                org_columns.append("]");
            }

        }


        sql = new SQLString("\n"
            + "/* copy user */\n"
            + "declare @template_user_id int\n"
            + "declare @template_org_id int\n"
            + "select @template_user_id = us_id,\n"
            + "@template_org_id = us_org \n"
            + "from users where us_username = @template_user\n"
            + "\n"
            + "declare @org_id int\n"
            + "set @org_id = -1\n"
            + "\n"
            + "IF @use_domain_as_org_name = 1\n"
            + "BEGIN\n"
            + "    select @org_id = og_id from orgs where og_domain = @domain\n"
            + "    IF @org_id = -1\n"
            + "    BEGIN\n"
            + "        insert into orgs\n"
            + "        (\n"
            + "            og_name,\n"
            + "            og_domain       \n"
            + "            @ORG_COLUMNS        \n"
            + "        )\n"
            + "        select \n"
            + "        @domain,\n"
            + "        @domain\n"
            + "        @ORG_COLUMNS\n"
            + "        from orgs where og_id = @template_org_id\n"
            + "        select @org_id = scope_identity()\n"
            + "    END\n"
            + "END\n"
            + "\n"
            + "declare @new_user_id int\n"
            + "set @new_user_id = -1\n"
            + "\n"
            + "IF NOT EXISTS (SELECT us_id FROM users WHERE us_username = @username)\n"
            + "BEGIN\n"
            + "\n"
            + "insert into users\n"
            + "\t(us_username, us_email, us_firstname, us_lastname, us_signature, us_salt, us_password,\n"
            + "\tus_default_query,\n"
            + "\tus_enable_notifications,\n"
            + "\tus_auto_subscribe,\n"
            + "\tus_auto_subscribe_own_bugs,\n"
            + "\tus_auto_subscribe_reported_bugs,\n"
            + "\tus_send_notifications_to_self,\n"
            + "\tus_active,\n"
            + "\tus_bugs_per_page,\n"
            + "\tus_forced_project,\n"
            + "\tus_reported_notifications,\n"
            + "\tus_assigned_notifications,\n"
            + "\tus_subscribed_notifications,\n"
            + "\tus_use_fckeditor,\n"
            + "\tus_enable_bug_list_popups,\n"
            + "\tus_org)\n"
            + "\n"
            + "select\n"
            + "\t@username, @email, @firstname, @lastname, @signature, @salt, @password,\n"
            + "\tus_default_query,\n"
            + "\tus_enable_notifications,\n"
            + "\tus_auto_subscribe,\n"
            + "\tus_auto_subscribe_own_bugs,\n"
            + "\tus_auto_subscribe_reported_bugs,\n"
            + "\tus_send_notifications_to_self,\n"
            + "\t1, -- active\n"
            + "\tus_bugs_per_page,\n"
            + "\tus_forced_project,\n"
            + "\tus_reported_notifications,\n"
            + "\tus_assigned_notifications,\n"
            + "\tus_subscribed_notifications,\n"
            + "\tus_use_fckeditor,\n"
            + "\tus_enable_bug_list_popups,\n"
            + "\tcase when @org_id = -1 then us_org else @org_id end\n"
            + "\tfrom users where us_id = @template_user_id\n"
            + "\n"
            + "select @new_user_id = scope_identity()\n"
            + "\n"
            + "insert into project_user_xref\n"
            + "\t(pu_project, pu_user, pu_auto_subscribe, pu_permission_level, pu_admin)\n"
            + "\n"
            + "select pu_project, @new_user_id, pu_auto_subscribe, pu_permission_level, pu_admin\n"
            + "\tfrom project_user_xref\n"
            + "\twhere pu_user = @template_user_id\n"
            + "\n"
            + "select @new_user_id\n"
            + "\n"
            + "END\n"
            + "");
        sql = sql.AddParameterWithValue("username", username);
        sql = sql.AddParameterWithValue("email", email);
        sql = sql.AddParameterWithValue("firstname", firstname);
        sql = sql.AddParameterWithValue("lastname", lastname);
        sql = sql.AddParameterWithValue("signature", signature);
        sql = sql.AddParameterWithValue("salt", Convert.ToString(salt));
        sql = sql.AddParameterWithValue("password", password);
        sql = sql.AddParameterWithValue("template_user", template_username);

        sql = sql.AddParameterWithValue("use_domain_as_org_name", Convert.ToString(use_domain_as_org_name ? "1" : "0"));

        String[] email_parts = Strings.Split(email, '@');
        if (email_parts.length == 2)
        {
            sql = sql.AddParameterWithValue("domain", email_parts[1]);
        }
        else
        {
            sql = sql.AddParameterWithValue("domain", email);
        }

        sql = sql.AddParameterWithValue("ORG_COLUMNS", org_columns.toString());
        return Convert.ToInt32(btnet.DbUtil.execute_scalar(sql));

    }

} // end class
// PORT STATUS: confidence=high todos=0
