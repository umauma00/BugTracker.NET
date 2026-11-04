// Source: src/BugTracker.Web/Security/ClaimsIdentityExtensions.cs
package btnet.Security;

import btnet.runtime.Convert;
import btnet.runtime.SecurityException;
import btnet.runtime.Strings;
import btnet.runtime.security.Claim;
import btnet.runtime.security.ClaimTypes;
import btnet.runtime.security.ClaimsIdentity;
import btnet.runtime.security.IIdentity;

public final class ClaimsIdentityExtensions {
    private ClaimsIdentityExtensions() {}

    public static int GetUserId(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.UserId));
    }

    public static String GetEmail(IIdentity identity) {
        return GetClaimsValue(identity, ClaimTypes.Email);
    }

    public static int GetOrganizationId(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.OrganizationId));
    }

    public static int GetForcedProjectId(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.ForcedProjectId));
    }

    public static int GetBugsPerPage(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.BugsPerPage));
    }

    public static boolean GetCanOnlySeeOwnReportedBugs(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanOnlySeeOwnReportedBugs));
    }

    public static boolean GetCanAssignToInternalUsers(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanAssignToInternalUsers));
    }

    public static boolean GetEnablePopups(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.EnablePopUps));
    }

    public static boolean GetUseFCKEditor(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.UseFCKEditor));
    }

    public static boolean GetCanEditTasks(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanEditTasks));
    }

    public static boolean GetCanViewTasks(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanViewTasks));
    }

    public static boolean GetCanAddBugs(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanAddBugs));
    }

    public static boolean GetCanUseReports(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanUseReports));
    }

    public static boolean GetCanEditReports(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanEditReports));
    }

    public static boolean GetCanMergeBugs(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanMergeBugs));
    }

    public static boolean GetCanMassEditBugs(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanMassEditBugs));
    }

    public static int GetOtherOrgsPermissionLevels(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.OtherOrgsPermissionLevel));
    }

    public static int GetCategoryFieldPermissionLevel(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.CategoryFieldPermissionLevel));
    }

    public static int GetTagsFieldPermissionLevel(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.TagsFieldPermissionLevel));
    }

    public static int GetProjectFieldPermissionLevel(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.ProjectFieldPermissionLevel));
    }

    public static int GetStatusFieldPermissionLevel(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.StatusFieldPermissionLevel));
    }

    public static int GetPriorityFieldPermissionLevel(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.PriorityFieldPermissionLevel));
    }

    public static int GetAssignedToFieldPermissionLevel(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.AssignedToFieldPermissionLevel));
    }

    public static int GetOrgFieldPermissionLevel(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.OrgFieldPermissionLevel));
    }

    public static int GetUdfFieldPermissionLevel(IIdentity identity) {
        return Convert.ToInt32(GetClaimsValue(identity, BtnetClaimTypes.UdfFieldPermissionLevel));
    }

    public static boolean GetCanSearch(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanSearch));
    }

    public static boolean GetCanEditAndDeleteBugs(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanEditAndDeleteBugs));
    }

    public static boolean GetCanEditAndDeletePosts(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanEditAndDeletePosts));
    }

    public static boolean GetCanDeleteBugs(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.CanDeleteBugs));
    }

    public static boolean GetIsExternalUser(IIdentity identity) {
        return Convert.ToBoolean(GetClaimsValue(identity, BtnetClaimTypes.IsExternalUser));
    }

    public static boolean IsInRole(IIdentity identity, String roleName) {
        if (identity instanceof ClaimsIdentity) {
            ClaimsIdentity claimsIdentity = (ClaimsIdentity) identity;
            return claimsIdentity.HasClaim(ClaimTypes.Role, roleName);
        } else {
            throw new SecurityException("Identity is not a valid Claims Identity");
        }
    }

    private static String GetClaimsValue(IIdentity identity, String claimType) {
        if (identity instanceof ClaimsIdentity) {
            ClaimsIdentity claimsIdentity = (ClaimsIdentity) identity;

            Claim userIdClaim = claimsIdentity.FindFirst(claimType);
            if (userIdClaim != null) {
                return userIdClaim.Value();
            } else {
                // inventory: src/BugTracker.Web/Security/ClaimsIdentityExtensions.cs L185
                throw new SecurityException(Strings.Format("Identity is missing value for claim type {0}", claimType));
            }
        } else {
            throw new SecurityException("Identity is not a valid Claims Identity");
        }
    }
}

// PORT STATUS: confidence=high todos=0
