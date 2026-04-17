package gui;

import core.Parameter;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * Table model responsible for displaying and editing {@link Parameter} objects
 * inside the core.Parameter Manager dialog.
 *
 * <p>
 * Columns:
 * <ul>
 *     <li>Name</li>
 *     <li>Current Value</li>
 *     <li>Source</li>
 *     <li>Enabled</li>
 *     <li>Auto Update</li>
 *     <li>Update Count</li>
 * </ul>
 * </p>
 */
public class ParameterTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "Name",
            "Current Value",
            "Source",
            "Enabled",
            "Auto",
            "Updates"
    };

    private final List<Parameter> parameters;

    /**
     * Constructs a table model for the given list of parameters.
     *
     * @param parameters List of {@link Parameter} objects to display
     */
    public ParameterTableModel(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    @Override
    public int getRowCount() {
        return parameters.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 3: // Enabled
            case 4: // Auto Update
                return Boolean.class;
            case 5: // Update Count
                return Integer.class;
            default:
                return String.class;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // Only Value, Enabled, and Auto Update columns are editable
        return columnIndex == 1 || columnIndex == 3 || columnIndex == 4;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Parameter param = parameters.get(rowIndex);

        switch (columnIndex) {
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
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        Parameter param = parameters.get(rowIndex);

        switch (columnIndex) {
            case 1: // Current Value
                param.setValue(String.valueOf(value));
                break;
            case 3: // Enabled
                param.setEnabled((Boolean) value);
                break;
            case 4: // Auto Update
                param.setAutoUpdate((Boolean) value);
                break;
            default:
                return; // Non-editable columns ignored
        }

        fireTableCellUpdated(rowIndex, columnIndex);
    }
}