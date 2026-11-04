// Source: src/BugTracker.Web/Security/Security.cs
package btnet.Security;

import btnet.SQLString;
import btnet.Util;
import btnet.runtime.Convert;
import btnet.runtime.DBNull;
import btnet.runtime.data.DataRow;
import btnet.runtime.security.Claim;
import btnet.runtime.security.ClaimTypes;
import btnet.runtime.security.ClaimsIdentity;
import btnet.runtime.security.IOwinContext;
import btnet.runtime.web.HttpRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Security {

    public static void SignIn(IOwinContext owinContext, String username)
    {
        var identity = GetIdentity(username);
        owinContext.Authentication().SignIn(identity);
    }

    public static ClaimsIdentity GetIdentity(String username)
    {
        // inventory: src/BugTracker.Web/Security/Security.cs L22
        // FIXED(port): F4 — see RULEBOOK §0.1
        SQLString sql = new SQLString("\nselect u.us_id, u.us_username, u.us_org, u.us_bugs_per_page, u.us_enable_bug_list_popups,\n"
            + "       u.us_use_fckeditor, u.us_forced_project, u.us_email,\n"
            + "       org.*,\n"
            + "       isnull(u.us_forced_project, 0 ) us_forced_project,\n"
            + "       proj.pu_permission_level,\n"
            + "       isnull(proj.pu_admin, 0) pu_admin,\n"
            + "       u.us_admin,\n"
            + "       (select count(1) from project_user_xref pa where pa.pu_user = u.us_id and pa.pu_admin = 1) [project_admin]\n"
            + "from users u\n"
            + "inner join orgs org \n"
            + "    on u.us_org = org.og_id\n"
            + "left outer join project_user_xref proj\n"
            + "\ton proj.pu_project = u.us_forced_project\n"
            + "\tand proj.pu_user = u.us_id\n"
            + "where us_username = @us and u.us_active = 1");
        sql = sql.AddParameterWithValue("us", username);
        DataRow dr = btnet.DbUtil.get_datarow(sql);

        var bugsPerPage = dr.get("us_bugs_per_page") == DBNull.Value ? 10 : (int) dr.get("us_bugs_per_page");

        List<Claim> claims = new ArrayList<>(Arrays.asList(
                new Claim(BtnetClaimTypes.UserId, Convert.ToString(dr.get("us_id"))),
                new Claim(ClaimTypes.Name, Convert.ToString(dr.get("us_username"))),
                new Claim(ClaimTypes.Email, Convert.ToString(dr.get("us_email"))),
                new Claim(BtnetClaimTypes.OrganizationId, Convert.ToString(dr.get("us_org"))),
                new Claim(BtnetClaimTypes.BugsPerPage, Convert.ToString(bugsPerPage)),
                new Claim(BtnetClaimTypes.EnablePopUps, Convert.ToString((int) dr.get("us_enable_bug_list_popups") == 1)),
                new Claim(BtnetClaimTypes.CanOnlySeeOwnReportedBugs, Convert.ToString((int) dr.get("og_can_only_see_own_reported") == 1)),
                new Claim(BtnetClaimTypes.CanUseReports, Convert.ToString((int) dr.get("og_can_use_reports") == 1)),
                new Claim(BtnetClaimTypes.CanEditReports, Convert.ToString((int) dr.get("og_can_edit_reports") == 1)),
                new Claim(BtnetClaimTypes.CanEditAndDeleteBugs, Convert.ToString((int) dr.get("og_can_edit_and_delete_posts") == 1)),
                new Claim(BtnetClaimTypes.CanDeleteBugs, Convert.ToString((int) dr.get("og_can_delete_bug") == 1)),
                new Claim(BtnetClaimTypes.CanMergeBugs, Convert.ToString((int) dr.get("og_can_merge_bugs") == 1)),
                new Claim(BtnetClaimTypes.CanMassEditBugs, Convert.ToString((int) dr.get("og_can_mass_edit_bugs") == 1)),
                new Claim(BtnetClaimTypes.CanAssignToInternalUsers, Convert.ToString((int) dr.get("og_can_assign_to_internal_users") == 1)),

                new Claim(BtnetClaimTypes.CanEditAndDeletePosts, Convert.ToString((int) dr.get("og_can_edit_and_delete_posts") == 1)),

                new Claim(BtnetClaimTypes.CanEditTasks, Convert.ToString((int) dr.get("og_can_edit_tasks") == 1)),
                new Claim(BtnetClaimTypes.CanViewTasks, Convert.ToString((int) dr.get("og_can_view_tasks") == 1)),

                new Claim(BtnetClaimTypes.OtherOrgsPermissionLevel, Convert.ToString(dr.get("og_other_orgs_permission_level"))),
                new Claim(BtnetClaimTypes.CategoryFieldPermissionLevel, Convert.ToString(dr.get("og_category_field_permission_level"))),
                new Claim(BtnetClaimTypes.PriorityFieldPermissionLevel, Convert.ToString(dr.get("og_priority_field_permission_level"))),
                new Claim(BtnetClaimTypes.ProjectFieldPermissionLevel, Convert.ToString(dr.get("og_project_field_permission_level"))),
                new Claim(BtnetClaimTypes.StatusFieldPermissionLevel, Convert.ToString(dr.get("og_status_field_permission_level"))),
                new Claim(BtnetClaimTypes.AssignedToFieldPermissionLevel, Convert.ToString(dr.get("og_assigned_to_field_permission_level"))),
                new Claim(BtnetClaimTypes.OrgFieldPermissionLevel, Convert.ToString(dr.get("og_org_field_permission_level"))),
                new Claim(BtnetClaimTypes.UdfFieldPermissionLevel, Convert.ToString(dr.get("og_udf_field_permission_level"))),

            // BUG(port): latent: second CanOnlySeeOwnReportedBugs claim (from us_enable_bug_list_popups) is shadowed by the first (:49) because FindFirst reads the first — repro: none today (RULEBOOK §0.1 "Not fixed")
                new Claim(BtnetClaimTypes.CanOnlySeeOwnReportedBugs, Convert.ToString((int) dr.get("us_enable_bug_list_popups") == 1)),
                new Claim(BtnetClaimTypes.CanSearch, Convert.ToString((int) dr.get("og_can_search") == 1)),
                new Claim(BtnetClaimTypes.IsExternalUser, Convert.ToString((int) dr.get("og_external_user") == 1)),
                new Claim(BtnetClaimTypes.UseFCKEditor, Convert.ToString((int) dr.get("us_use_fckeditor") == 1))

            ));

        boolean canAdd = true;
        int permssionLevel = dr.get("pu_permission_level") == DBNull.Value
            ? Convert.ToInt32(Util.get_setting("DefaultPermissionLevel", "2"))
            : (int) dr.get("pu_permission_level");
        // if user is forced to a specific project, and doesn't have
        // at least reporter permission on that project, than user
        // can't add bugs
        int forcedProjectId = dr.get("us_forced_project") == DBNull.Value ? 0 : (int) dr.get("us_forced_project");
        if (forcedProjectId != 0)
        {
            if (permssionLevel == PermissionLevel.ReadOnly || permssionLevel == PermissionLevel.None)
            {
                canAdd = false;
            }
        }
        claims.add(new Claim(BtnetClaimTypes.CanAddBugs, Convert.ToString(canAdd)));
        claims.add(new Claim(BtnetClaimTypes.ForcedProjectId, Convert.ToString(forcedProjectId)));

        int tagsPermissionLevel;
        if (Objects.equals(Util.get_setting("EnableTags", "0"), "1"))
        {
            tagsPermissionLevel = (int) dr.get("og_tags_field_permission_level");
        }
        else
        {
            tagsPermissionLevel = PermissionLevel.None;
        }

        claims.add(new Claim(BtnetClaimTypes.TagsFieldPermissionLevel, Convert.ToString(tagsPermissionLevel)));


        if ((int) dr.get("us_admin") == 1)
        {
            claims.add(new Claim(ClaimTypes.Role, BtnetRoles.Admin));
        }
        else
        {
            if ((int) dr.get("project_admin") > 0)
            {
                claims.add(new Claim(ClaimTypes.Role, BtnetRoles.ProjectAdmin));
            }
        }
        claims.add(new Claim(ClaimTypes.Role, BtnetRoles.User));


        return new ClaimsIdentity(claims, "ApplicationCookie", ClaimTypes.Name, ClaimTypes.Role);
    }

    public static void SignOut(HttpRequest request)
    {
        var owinContext = request.GetOwinContext();
        owinContext.Authentication().SignOut();
    }
}
// PORT STATUS: confidence=high todos=0
