// Source: src/BugTracker.Web/Models/BugTask.cs
package btnet.Models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class BugTask
{
    public int Id;

    public int BugId;
    public Bug Bug;

    public int CreatedUserId;
    public User CreatedUser;

    public LocalDateTime CreatedDate;
    public int LastUpdatedUserId;
    public User LastUpdatedUser;

    public LocalDateTime LastUpdatedDate;
    public Integer AssignedToUserId;
    public User AssignedToUser;

    public LocalDateTime PlannedStartDate;
    public LocalDateTime ActualStartDate;
    public LocalDateTime PlannedEndDate;
    public LocalDateTime ActualEndDate;
    public BigDecimal PlannedDuration;
    public BigDecimal ActualDuration;
    public String DurationUnits;
    public Integer PercentComplete;
    public Integer StatusId;
    public Status Status;

    public Integer SortSequence;
    public String Description;
}
// PORT STATUS: confidence=high todos=0
