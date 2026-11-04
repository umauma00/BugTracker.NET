package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.WebControls.ButtonColumn}:
 * {@code <asp:ButtonColumn HeaderText="Delete" ButtonType="LinkButton" Text="Delete" CommandName="dlt"/>}
 * → {@code new ButtonColumn("Delete", "Delete", "dlt")}. Each item cell holds a {@link LinkButton}
 * (or a {@link Button} for {@code PushButton}) whose click bubbles to the grid's {@code ItemCommand}
 * with that {@code CommandName}.
 */
public class ButtonColumn extends DataGridColumn<ButtonColumn> {
    private String text;
    private String commandName;
    private ButtonColumnType buttonType = ButtonColumnType.LinkButton;
    private String dataTextField = "";
    private String dataTextFormatString = "";
    private boolean causesValidation = true;

    public ButtonColumn(String headerText, String text, String commandName) {
        super(headerText);
        this.text = text == null ? "" : text;
        this.commandName = commandName == null ? "" : commandName;
    }

    public ButtonColumn() {
        this("", "", "");
    }

    public String Text() { return text; }

    public ButtonColumn Text(String v) { this.text = v == null ? "" : v; return this; }

    public String CommandName() { return commandName; }

    public ButtonColumn CommandName(String v) { this.commandName = v == null ? "" : v; return this; }

    public ButtonColumnType ButtonType() { return buttonType; }

    public ButtonColumn ButtonType(ButtonColumnType v) { this.buttonType = v; return this; }

    public String DataTextField() { return dataTextField; }

    public ButtonColumn DataTextField(String v) { this.dataTextField = v == null ? "" : v; return this; }

    public String DataTextFormatString() { return dataTextFormatString; }

    public ButtonColumn DataTextFormatString(String v) { this.dataTextFormatString = v == null ? "" : v; return this; }

    public boolean CausesValidation() { return causesValidation; }

    public ButtonColumn CausesValidation(boolean v) { this.causesValidation = v; return this; }

    @Override
    protected void InitializeCell(TableCell cell, int columnIndex, ListItemType itemType, DataGridItem item) {
        super.InitializeCell(cell, columnIndex, itemType, item);
        if (itemType == ListItemType.Header || itemType == ListItemType.Footer) return;
        if (buttonType == ButtonColumnType.LinkButton) {
            LinkButton b = new LinkButton();
            b.Text(text);
            b.CommandName(commandName);
            b.CausesValidation(causesValidation);
            cell.Controls().Add(b);
        } else {
            Button b = new Button();
            b.Text(text);
            b.CommandName(commandName);
            b.CausesValidation(causesValidation);
            b.UseSubmitBehavior(false);
            cell.Controls().Add(b);
        }
    }

    @Override
    protected void BindCell(TableCell cell, DataGridItem item, Object dataItem) {
        if (dataTextField.isEmpty()) return;
        String t = DataBinder.format(DataBinder.eval(dataItem, dataTextField), dataTextFormatString);
        Control<?> c = cell.Controls().get(0);
        if (c instanceof LinkButton lb) lb.Text(t);
        else if (c instanceof Button b) b.Text(t);
    }
}
