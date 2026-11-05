// Source: src/BugTracker.Web/Models/Mapping/BugPostMap.cs
package btnet.Models.Mapping;

import btnet.Models.BugPost;
import btnet.runtime.data.EntityTypeConfiguration;

public class BugPostMap extends EntityTypeConfiguration<BugPost>
{
    public BugPostMap()
    {
        // Primary Key
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L11
        this.HasKey("Id");

        // Properties
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L14
        this.Property("Type")
            .IsRequired()
            .HasMaxLength(8);

        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L18
        this.Property("Comment")
            .IsRequired();

        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L21
        this.Property("FromEmail")
            .HasMaxLength(800);

        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L24
        this.Property("ToEmail")
            .HasMaxLength(800);

        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L27
        this.Property("File")
            .HasMaxLength(1000);

        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L30
        this.Property("ContentType")
            .HasMaxLength(200);

        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L33
        this.Property("CCEmail")
            .HasMaxLength(800);

        // Table & Column Mappings
        this.ToTable("bug_posts");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L38
        this.Property("Id").HasColumnName("bp_id");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L39
        this.Property("BugId").HasColumnName("bp_bug");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L40
        this.Property("Type").HasColumnName("bp_type");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L41
        this.Property("UserId").HasColumnName("bp_user");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L42
        this.Property("Date").HasColumnName("bp_date");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L43
        this.Property("Comment").HasColumnName("bp_comment");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L44
        this.Property("CommentSearch").HasColumnName("bp_comment_search");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L45
        this.Property("FromEmail").HasColumnName("bp_email_from");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L46
        this.Property("ToEmail").HasColumnName("bp_email_to");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L47
        this.Property("File").HasColumnName("bp_file");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L48
        this.Property("Size").HasColumnName("bp_size");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L49
        this.Property("ContentType").HasColumnName("bp_content_type");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L50
        this.Property("ParentId").HasColumnName("bp_parent");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L51
        this.Property("OriginalCommentId").HasColumnName("bp_original_comment_id");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L52
        this.Property("HideFromExternalUsers").HasColumnName("bp_hidden_from_external_users");
        // inventory: src/BugTracker.Web/Models/Mapping/BugPostMap.cs L53
        this.Property("CCEmail").HasColumnName("bp_email_cc");
    }
}

// PORT STATUS: confidence=high todos=0
