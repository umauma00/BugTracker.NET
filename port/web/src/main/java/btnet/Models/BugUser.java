// Source: src/BugTracker.Web/Models/BugUser.cs
package btnet.Models;

import java.time.LocalDateTime;

public class BugUser
{
    public int Id;
    public int BugId;
    public int UserId;
    public int Flag;
    public LocalDateTime FlagDate;
    public int Seen;
    public LocalDateTime SeenDate;
    public int Vote;
    public LocalDateTime VoteDate;
}
// PORT STATUS: confidence=high todos=0
