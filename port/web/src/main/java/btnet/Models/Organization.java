// Source: src/BugTracker.Web/Models/Organization.cs
package btnet.Models;

public class Organization
{
    public int Id;
    public String Name;
    public String Domain;
    public int NonAdminsCanUse;
    public int ExternalUser;
    public int CanBeAssignedTo;
    public int CanOnlySeeOwnReport;
    public int CanEditSQL;
    public int CanDeleteBug;
    public int CanEditPosts;
    public int CanMergeBugs;
    public int CanMassEdit;
    public int CanUseReports;
    public int CanEditReports;
    public int CanViewTasks;
    public int CanEditTasks;
    public int CanSearch;
    public int OtherOrgsPermissionLevel;
    public int CanAssignToInternalUsers;
    public int CategoryFieldPErmissionLevel;
    public int PriorityFieldPermissionLevel;
    public int AssignedToFieldPermissionLevel;
    public int StatusFieldPermissionLevel;
    public int ProjectFieldPermissionLevel;
    public int OrgFieldPermissionLevel;
    public int UserDefinedFieldPermissionLevel;
    public int TagsPermissionLevel;
    public int Active;
}
// PORT STATUS: confidence=high todos=0
