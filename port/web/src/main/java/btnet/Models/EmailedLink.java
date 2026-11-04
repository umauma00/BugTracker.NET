// Source: src/BugTracker.Web/Models/EmailedLink.cs
package btnet.Models;

import java.time.LocalDateTime;

public class EmailedLink
{
    public String Id;
    public LocalDateTime Date;
    public String Email;
    public String Action;
    public String UserName;
    public Integer UserId;
    public Integer Salt;
    public String PasswordHash;
    public String FirstName;
    public String LastName;
}
// PORT STATUS: confidence=high todos=0
