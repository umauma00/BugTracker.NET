package btnet.runtime.web.ui;

/**
 * {@code System.Web.UI.WebControls.DataGridCommandEventArgs}: {@code e.Item} → {@code e.Item()},
 * {@code e.CommandName} → {@code e.CommandName()}, {@code e.CommandSource} → {@code e.CommandSource()}.
 */
public class DataGridCommandEventArgs extends CommandEventArgs {
    private final DataGridItem item;
    private final Object commandSource;

    public DataGridCommandEventArgs(DataGridItem item, Object commandSource, CommandEventArgs originalArgs) {
        super(originalArgs);
        this.item = item;
        this.commandSource = commandSource;
    }

    public DataGridItem Item() { return item; }

    public Object CommandSource() { return commandSource; }
}
