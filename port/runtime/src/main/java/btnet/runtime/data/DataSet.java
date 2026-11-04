package btnet.runtime.data;

/** .NET {@code System.Data.DataSet}: an ordered set of {@link DataTable}s ({@code ds.Tables()}). */
public class DataSet {
    private String dataSetName;
    private final DataTableCollection tables = new DataTableCollection(this);

    public DataSet() {
        this("NewDataSet");
    }

    public DataSet(String dataSetName) {
        this.dataSetName = dataSetName == null ? "" : dataSetName;
    }

    public DataTableCollection Tables() {
        return tables;
    }

    public String DataSetName() {
        return dataSetName;
    }

    public void DataSetName(String value) {
        this.dataSetName = value == null ? "" : value;
    }

    public void Clear() {
        for (DataTable t : tables) t.Clear();
    }

    public void AcceptChanges() {
        // no row versions are kept by the port
    }

    @Override
    public String toString() {
        return "System.Data.DataSet";
    }
}
