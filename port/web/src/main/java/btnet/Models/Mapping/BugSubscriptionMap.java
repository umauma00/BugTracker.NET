// Source: src/BugTracker.Web/Models/Mapping/BugSubscriptionMap.cs
package btnet.Models.Mapping;

import btnet.Models.BugSubscription;
import btnet.runtime.data.EntityTypeConfiguration;

public class BugSubscriptionMap extends EntityTypeConfiguration<BugSubscription>
{
    public BugSubscriptionMap()
    {
        // Primary Key
        // inventory: src/BugTracker.Web/Models/Mapping/BugSubscriptionMap.cs L11
        this.HasKey("Id");

        // Properties
        // inventory: src/BugTracker.Web/Models/Mapping/BugSubscriptionMap.cs L14
        this.Property("BugId")
            .HasDatabaseGeneratedOption(DatabaseGeneratedOption.None);

        // inventory: src/BugTracker.Web/Models/Mapping/BugSubscriptionMap.cs L17
        this.Property("UserId")
            .HasDatabaseGeneratedOption(DatabaseGeneratedOption.None);

        // Table & Column Mappings
        this.ToTable("bug_subscriptions");
        // inventory: src/BugTracker.Web/Models/Mapping/BugSubscriptionMap.cs L22
        this.Property("BugId").HasColumnName("bs_bug");
        // inventory: src/BugTracker.Web/Models/Mapping/BugSubscriptionMap.cs L23
        this.Property("UserId").HasColumnName("bs_user");
    }
}

// PORT STATUS: confidence=high todos=0
