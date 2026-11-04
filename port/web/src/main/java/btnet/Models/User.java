// Source: src/BugTracker.Web/Models/user.cs
package btnet.Models;

import java.time.LocalDateTime;

public class User
{
    public int Id;
    public String UserName;
    public String Salt;
    public String Password;
    public String FristName;
    public String LastName;
    public String EMail;
    public int AdminId;
    public int DefaltQueryID;
    public int EnableNotifications;
    public int AutoSubscribe;
    public Integer AutoSubscribeOwnBugs;
    public Integer AutoSubscribeReportedBugs;
    public Integer SendNotificationsToSelf;
    public int Active;
    public Integer BugsPerPage;
    public Integer ForcedProject;
    public int ReportedNotifications;
    public int AssignedNotifications;
    public int SubscribedNotifications;
    public String Signature;
    public int UseEditor;
    public int EnableBugListPopUps;
    public int CreatedUser;
    public int OrganizationId;
    public LocalDateTime LastLoginDate;
    public String PasswordResetKey;
}
// PORT STATUS: confidence=high todos=0
