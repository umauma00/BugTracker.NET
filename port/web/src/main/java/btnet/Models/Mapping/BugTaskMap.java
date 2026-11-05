// Source: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs
package btnet.Models.Mapping;

import btnet.Models.BugTask;
import btnet.runtime.data.EntityTypeConfiguration;

public class BugTaskMap extends EntityTypeConfiguration<BugTask>
{
    public BugTaskMap()
    {
        // Primary Key
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L11
        this.HasKey("Id");

        // Properties
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L14
        this.Property("DurationUnits")
            .HasMaxLength(20);

        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L17
        this.Property("Description")
            .HasMaxLength(400);

        // Table & Column Mappings
        this.ToTable("bug_tasks");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L22
        this.Property("Id").HasColumnName("tsk_id");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L23
        this.Property("BugId").HasColumnName("tsk_bug");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L24
        this.Property("CreatedUserId").HasColumnName("tsk_created_user");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L25
        this.Property("CreatedDate").HasColumnName("tsk_created_date");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L26
        this.Property("LastUpdatedUserId").HasColumnName("tsk_last_updated_user");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L27
        this.Property("LastUpdatedDate").HasColumnName("tsk_last_updated_date");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L28
        this.Property("AssignedToUserId").HasColumnName("tsk_assigned_to_user");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L29
        this.Property("PlannedStartDate").HasColumnName("tsk_planned_start_date");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L30
        this.Property("ActualStartDate").HasColumnName("tsk_actual_start_date");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L31
        this.Property("PlannedEndDate").HasColumnName("tsk_planned_end_date");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L32
        this.Property("ActualEndDate").HasColumnName("tsk_actual_end_date");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L33
        this.Property("PlannedDuration").HasColumnName("tsk_planned_duration");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L34
        this.Property("ActualDuration").HasColumnName("tsk_actual_duration");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L35
        this.Property("DurationUnits").HasColumnName("tsk_duration_units");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L36
        this.Property("PercentComplete").HasColumnName("tsk_percent_complete");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L37
        this.Property("StatusId").HasColumnName("tsk_status");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L38
        this.Property("SortSequence").HasColumnName("tsk_sort_sequence");
        // inventory: src/BugTracker.Web/Models/Mapping/BugTaskMap.cs L39
        this.Property("Description").HasColumnName("tsk_description");
    }
}

// PORT STATUS: confidence=high todos=0
