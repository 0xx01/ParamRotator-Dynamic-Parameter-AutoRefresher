import javax.swing.table.AbstractTableModel;
import java.util.List;

public class ParameterTableModel extends AbstractTableModel {
    private final List<Parameter> parameters;
    private final String[] columns = {
            "Name", "Current Value", "Source", "Enabled", "Auto", "Updates"
    };

    public ParameterTableModel(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    @Override
    public int getRowCount() { return parameters.size(); }

    @Override
    public int getColumnCount() { return columns.length; }

    @Override
    public String getColumnName(int col) { return columns[col]; }

    @Override
    public Class<?> getColumnClass(int col) {
        if (col == 3 || col == 4) return Boolean.class;
        if (col == 5) return Integer.class;
        return String.class;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col == 1 || col == 3 || col == 4; // Value, Enabled, Auto
    }

    @Override
    public Object getValueAt(int row, int col) {
        Parameter param = parameters.get(row);
        switch (col) {
            case 0: return param.getName();
            case 1: return param.getValue();
            case 2: return param.getSource().getDisplayName();
            case 3: return param.isEnabled();
            case 4: return param.isAutoUpdate();
            case 5: return param.getUpdateCount();
            default: return null;
        }
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        Parameter param = parameters.get(row);
        if (col == 1) {
            param.setValue(value.toString());
        } else if (col == 3) {
            param.setEnabled((Boolean) value);
        } else if (col == 4) {
            param.setAutoUpdate((Boolean) value);
        }
        fireTableCellUpdated(row, col);
    }
}