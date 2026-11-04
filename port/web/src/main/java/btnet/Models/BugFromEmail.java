// Source: src/BugTracker.Web/Models/BugFromEmail.cs
package btnet.Models;

import btnet.runtime.web.Required;

public class BugFromEmail
{
    // inventory: src/BugTracker.Web/Models/BugFromEmail.cs L11
    public Integer ProjectId;
    public String Comment;

    @Required
    public String FromAddress;
    public String CcAddress;
    public String Message;
    public byte[] Attachment;
    public String AttachmentContentType;
    public String AttachmentFileName;
    public String AttachmentDescription;
    public int BugId;
    public String ShortDescription;

    // inventory: src/BugTracker.Web/Models/BugFromEmail.cs L25
    public Integer OrganizationId;
    // inventory: src/BugTracker.Web/Models/BugFromEmail.cs L26
    public Integer CategoryId;
    // inventory: src/BugTracker.Web/Models/BugFromEmail.cs L27
    public Integer PriorityId;
    // inventory: src/BugTracker.Web/Models/BugFromEmail.cs L28
    public Integer StatusId;
    // inventory: src/BugTracker.Web/Models/BugFromEmail.cs L29
    public Integer AssignedTo;
    // inventory: src/BugTracker.Web/Models/BugFromEmail.cs L30
    public Integer UdfId;
}
// PORT STATUS: confidence=high todos=0
