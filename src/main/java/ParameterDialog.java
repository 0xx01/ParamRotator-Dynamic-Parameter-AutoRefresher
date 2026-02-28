import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ParameterDialog extends JDialog {
    private final ParameterManager parameterManager;
    private final ParameterTableModel tableModel;
    private JTable table;
    private JLabel statusLabel;

    public ParameterDialog(Frame parent, MontoyaApi api, ParameterManager manager) {
        super(parent, "Parameter Manager", true);
        this.parameterManager = manager;
        this.tableModel = new ParameterTableModel(manager.getAllParameters());

        setupUI();
        setSize(750, 450);
        setLocationRelativeTo(parent);
    }

    private void setupUI() {
        setLayout(new BorderLayout());

        // Toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton addButton = new JButton("Add Custom");
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton enableButton = new JButton("Enable");
        JButton disableButton = new JButton("Disable");
        JButton refreshButton = new JButton("Refresh");

        toolBar.add(addButton);
        toolBar.add(editButton);
        toolBar.add(deleteButton);
        toolBar.addSeparator();
        toolBar.add(enableButton);
        toolBar.add(disableButton);
        toolBar.addSeparator();
        toolBar.add(refreshButton);

        add(toolBar, BorderLayout.NORTH);

        // Table - shows ALL parameters
        table = new JTable(tableModel);
        table.setRowHeight(25);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(70);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(5).setPreferredWidth(60);

        // Checkbox for Enabled column
        table.getColumnModel().getColumn(3).setCellRenderer(table.getDefaultRenderer(Boolean.class));
        table.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JCheckBox()));

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Bottom panel with status
        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        statusLabel = new JLabel("Auto-Update: " +
                (parameterManager.isAutoUpdateEnabled() ? "ON" : "OFF") +
                " | Total Parameters: " + parameterManager.getAllParameters().size());
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        bottomPanel.add(statusLabel, BorderLayout.WEST);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // Button actions
        addButton.addActionListener(e -> showAddDialog());
        editButton.addActionListener(e -> showEditDialog());
        deleteButton.addActionListener(e -> deleteSelected());
        enableButton.addActionListener(e -> setSelectedEnabled(true));
        disableButton.addActionListener(e -> setSelectedEnabled(false));
        refreshButton.addActionListener(e -> {
            tableModel.fireTableDataChanged();
            updateStatusLabel();
        });
    }

    private void showAddDialog() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));

        JTextField nameField = new JTextField(20);
        JTextField valueField = new JTextField(20);
        JCheckBox enabledBox = new JCheckBox("Enabled", true);
        JCheckBox autoUpdateBox = new JCheckBox("Auto-Update", true);

        panel.add(new JLabel("Parameter Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Initial Value:"));
        panel.add(valueField);
        panel.add(new JLabel("Status:"));
        panel.add(enabledBox);
        panel.add(new JLabel("Auto-Update:"));
        panel.add(autoUpdateBox);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Add Custom Parameter", JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String value = valueField.getText().trim();

            if (!name.isEmpty()) {
                parameterManager.addCustomParameter(name, value);

                // Set options for the new parameter
                parameterManager.getAllParameters().stream()
                        .filter(p -> p.getName().equals(name))
                        .findFirst()
                        .ifPresent(p -> {
                            p.setEnabled(enabledBox.isSelected());
                            p.setAutoUpdate(autoUpdateBox.isSelected());
                        });

                tableModel.fireTableDataChanged();
                updateStatusLabel();
            }
        }
    }

    private void showEditDialog() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a parameter to edit!");
            return;
        }

        String name = (String) tableModel.getValueAt(row, 0);
        String currentValue = (String) tableModel.getValueAt(row, 1);

        String newValue = JOptionPane.showInputDialog(this,
                "New value for " + name + ":", currentValue);

        if (newValue != null) {
            parameterManager.updateParameter(name, newValue);
            tableModel.fireTableDataChanged();
        }
    }

    private void deleteSelected() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete " + rows.length + " selected parameter(s)?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            for (int i = rows.length - 1; i >= 0; i--) {
                String name = (String) tableModel.getValueAt(rows[i], 0);
                parameterManager.removeParameter(name);
            }
            tableModel.fireTableDataChanged();
            updateStatusLabel();
        }
    }

    private void setSelectedEnabled(boolean enable) {
        int[] rows = table.getSelectedRows();
        for (int row : rows) {
            String name = (String) tableModel.getValueAt(row, 0);
            parameterManager.getAllParameters().stream()
                    .filter(p -> p.getName().equals(name))
                    .findFirst()
                    .ifPresent(p -> p.setEnabled(enable));
        }
        tableModel.fireTableDataChanged();
    }

    private void updateStatusLabel() {
        statusLabel.setText("Auto-Update: " +
                (parameterManager.isAutoUpdateEnabled() ? "ON" : "OFF") +
                " | Total Parameters: " + parameterManager.getAllParameters().size());
    }
}