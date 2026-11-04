// Source: src/BugTracker.Web/Models/BugPost.cs
package btnet.Models;

import java.time.LocalDateTime;

public class BugPost
{
    public int Id;
    public int BugId;
    public String Type;
    public int UserId;
    public LocalDateTime Date;
    public String Comment;
    public String CommentSearch;
    public String FromEmail;
    public String ToEmail;
    public String File;
    public Integer Size;
    public String ContentType;
    public Integer ParentId;
    public Integer OriginalCommentId;
    public int HideFromExternalUsers;
    public String CCEmail;
}
// PORT STATUS: confidence=high todos=0
