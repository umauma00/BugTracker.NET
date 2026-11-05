// Source: src/BugTracker.Web/Models/Mapping/BugRelationShipMap.cs
package btnet.Models.Mapping;

import btnet.Models.BugRelationShip;
import btnet.runtime.data.EntityTypeConfiguration;

public class BugRelationShipMap extends EntityTypeConfiguration<BugRelationShip>
{
    public BugRelationShipMap()
    {
        // Primary Key
        // inventory: src/BugTracker.Web/Models/Mapping/BugRelationShipMap.cs L11
        this.HasKey("Id");

        // Properties
        // inventory: src/BugTracker.Web/Models/Mapping/BugRelationShipMap.cs L14
        this.Property("Type")
            .HasMaxLength(500);

        // Table & Column Mappings
        this.ToTable("bug_relationships");
        // inventory: src/BugTracker.Web/Models/Mapping/BugRelationShipMap.cs L19
        this.Property("Id").HasColumnName("re_id");
        // inventory: src/BugTracker.Web/Models/Mapping/BugRelationShipMap.cs L20
        this.Property("Bug1Id").HasColumnName("re_bug1");
        // inventory: src/BugTracker.Web/Models/Mapping/BugRelationShipMap.cs L21
        this.Property("Bug2Id").HasColumnName("re_bug2");
        // inventory: src/BugTracker.Web/Models/Mapping/BugRelationShipMap.cs L22
        this.Property("Type").HasColumnName("re_type");
        // inventory: src/BugTracker.Web/Models/Mapping/BugRelationShipMap.cs L23
        this.Property("Direction").HasColumnName("re_direction");
    }
}

// PORT STATUS: confidence=high todos=0
