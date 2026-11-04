// Source: src/BugTracker.Web/Models/Mapping/BugPostAttachmentMap.cs
package btnet.Models.Mapping;

import btnet.Models.BugPostAttachment;
import btnet.runtime.data.EntityTypeConfiguration;

public class BugPostAttachmentMap extends EntityTypeConfiguration<BugPostAttachment>
{
    public BugPostAttachmentMap()
    {
        // Primary Key
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostAttachmentMap.cs L11
        this.HasKey("Id");

        // Properties
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostAttachmentMap.cs L14
        this.Property("Content")
            .IsRequired();

        // Table & Column Mappings
        this.ToTable("bug_post_attachments");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostAttachmentMap.cs L19
        this.Property("Id").HasColumnName("bpa_id");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostAttachmentMap.cs L20
        this.Property("PostId").HasColumnName("bpa_post");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostAttachmentMap.cs L21
        this.Property("Content").HasColumnName("bpa_content");
    }
}

// PORT STATUS: confidence=high todos=0
