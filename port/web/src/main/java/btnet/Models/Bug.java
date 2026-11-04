// Source: src/BugTracker.Web/Models/bug.cs
package btnet.Models;

import java.time.LocalDateTime;
import java.util.List;

public class Bug
{
    public int Id;
    public String ShortDescription;

    public int ReportedUserId;
    public User ReportedUser;

    public LocalDateTime ReportedDate;

    public int StatusId;
    public Status Status;

    public int PriorityId;
    public Priority Priority;

    public int OrganizationId;
    public Organization Organization;

    public int CategoryId;
    public Category Category;

    public int ProjectId;
    public Project Project;

    public Integer AssignedToUserId;
    public User AssignedToUser;

    public Integer LastUpdatedUserId;
    public User LastUpdatedUser;

    public LocalDateTime LastUpdatedDate;
    public Integer UserDefinedAttributeId;
    public String CustomDropDownValue1;
    public String CustomDropDownValue2;
    public String CustomDropDownValue3;
    public String Tags;

    public List<BugTask> Tasks;
}
// PORT STATUS: confidence=high todos=0
