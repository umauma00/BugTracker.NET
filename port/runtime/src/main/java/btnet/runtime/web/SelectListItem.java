package btnet.runtime.web;

/**
 * ASP.NET MVC {@code System.Web.Mvc.SelectListItem} (RULEBOOK §2A object
 * initializer row, §3 [C6]). Public fields, as the C# auto-properties; Jackson and
 * Thymeleaf read the fields.
 */
public class SelectListItem {
    public String Text;
    public String Value;
    public boolean Selected = false;

    public SelectListItem() {
    }

    public SelectListItem(String text, String value) {
        this.Text = text;
        this.Value = value;
    }
}
