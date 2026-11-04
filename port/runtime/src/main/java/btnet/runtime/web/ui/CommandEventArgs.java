package btnet.runtime.web.ui;

import btnet.runtime.EventArgs;

/** {@code System.Web.UI.WebControls.CommandEventArgs}: {@code CommandName()}, {@code CommandArgument()}. */
public class CommandEventArgs extends EventArgs {
    private final String commandName;
    private final Object commandArgument;

    public CommandEventArgs(String commandName, Object commandArgument) {
        this.commandName = commandName;
        this.commandArgument = commandArgument;
    }

    public CommandEventArgs(CommandEventArgs e) {
        this(e.CommandName(), e.CommandArgument());
    }

    public String CommandName() { return commandName; }

    public Object CommandArgument() { return commandArgument; }
}
