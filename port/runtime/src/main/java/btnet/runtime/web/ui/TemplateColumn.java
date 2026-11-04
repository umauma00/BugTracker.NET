package btnet.runtime.web.ui;

import btnet.runtime.data.DataRowView;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * {@code System.Web.UI.WebControls.TemplateColumn} [J-U1]: {@code <ItemTemplate>} becomes an item
 * factory that creates the row's controls ({@code item.Add(c)} puts them into this column's cell) and
 * a binding run at {@code DataBind} over the row's {@code DataRowView} (the {@code <%# %>} property
 * bindings). On postback the factory runs again (no binding) and the controls' state comes from view
 * state, before posted values load.
 */
public class TemplateColumn extends DataGridColumn<TemplateColumn> {
    private final Consumer<DataGridItem> itemFactory;
    private final BiConsumer<DataGridItem, DataRowView> binding;

    public TemplateColumn(String headerText, Consumer<DataGridItem> itemFactory,
                          BiConsumer<DataGridItem, DataRowView> binding) {
        super(headerText);
        this.itemFactory = itemFactory;
        this.binding = binding;
    }

    @Override
    protected void InitializeCell(TableCell cell, int columnIndex, ListItemType itemType, DataGridItem item) {
        super.InitializeCell(cell, columnIndex, itemType, item);
        if (itemType == ListItemType.Header || itemType == ListItemType.Footer || itemFactory == null) return;
        item.currentCell(cell);
        try {
            itemFactory.accept(item);
        } finally {
            item.currentCell(null);
        }
    }

    @Override
    protected void BindCell(TableCell cell, DataGridItem item, Object dataItem) {
        if (binding != null) binding.accept(item, dataItem instanceof DataRowView drv ? drv : null);
    }
}
