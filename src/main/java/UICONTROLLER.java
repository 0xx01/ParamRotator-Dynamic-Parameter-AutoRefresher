import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;

public class UICONTROLLER {

    private final MontoyaApi api;
    private final ParameterManager parameterManager;
    private final AutoRefreshService autoRefreshService;

    public UICONTROLLER(MontoyaApi api,
                        ParameterManager parameterManager,
                        AutoRefreshService autoRefreshService) {

        this.api = api;
        this.parameterManager = parameterManager;
        this.autoRefreshService = autoRefreshService;

        registerMenuItems();
    }

    private void registerMenuItems() {

        api.extension().registerUnloadingHandler(() -> {
            if (autoRefreshService.isRunning()) {
                autoRefreshService.stop();
            }
            api.logging().logToOutput("Extension unloaded cleanly");
        });

        api.userInterface().registerContextMenuItemsProvider(new ContextMenuItemsProvider() {

            @Override
            public List<Component> provideMenuItems(ContextMenuEvent event) {

                JMenuItem setReferenceButton = new JMenuItem("Set as Reference");
                JMenuItem extractParamsButton = new JMenuItem("Extract Parameters from Request");
                JMenuItem extractLocationButton = new JMenuItem("Extract Parameters from Location Header");
                JMenuItem manageParamsButton = new JMenuItem("Manage Parameters...");
                JMenuItem startAutoRefreshButton = new JMenuItem("Start Auto-Refresh");
                JMenuItem stopAutoRefreshButton = new JMenuItem("Stop Auto-Refresh");
                JMenuItem setIntervalButton = new JMenuItem("Set Interval");

                // =========================
                // SET REFERENCE
                // =========================

                setReferenceButton.addActionListener(e -> {

                    Optional<HttpRequestResponse> rr = getRequestResponse(event);

                    if (rr.isPresent()) {

                        HttpRequest request = rr.get().request();

                        autoRefreshService.setReferenceRequest(request);

                        JOptionPane.showMessageDialog(getParentFrame(),
                                "Reference request saved!",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                });

                // =========================
                // EXTRACT PARAMETERS
                // =========================

                extractParamsButton.addActionListener(e -> {

                    Optional<HttpRequestResponse> rr = getRequestResponse(event);

                    if (rr.isPresent()) {

                        parameterManager.extractFromRequest(rr.get().request());

                        JOptionPane.showMessageDialog(getParentFrame(),
                                "Extracted " + parameterManager.getAllParameters().size() + " parameters.",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                });

                // =========================
                // MANAGE PARAMETERS
                // =========================

                manageParamsButton.addActionListener(e -> {
                    SwingUtilities.invokeLater(() -> {
                        ParameterDialog dialog =
                                new ParameterDialog(getParentFrame(), api, parameterManager);
                        dialog.setVisible(true);
                    });
                });

                // =========================
                // START AUTO REFRESH
                // =========================

                startAutoRefreshButton.addActionListener(e -> {

                    if (!event.messageEditorRequestResponse().isPresent()) {
                        JOptionPane.showMessageDialog(getParentFrame(),
                                "Use inside Repeater!",
                                "Warning",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    var editor = event.messageEditorRequestResponse().get();
                    HttpRequest currentRequest = editor.requestResponse().request();

                    autoRefreshService.setCurrentRepeater(editor, currentRequest);

                    if (!autoRefreshService.isRunning()) {
                        autoRefreshService.start();
                        JOptionPane.showMessageDialog(getParentFrame(),
                                "Auto-Refresh has started and is now actively monitoring tokens.",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                });

                // =========================
                // STOP AUTO REFRESH
                // =========================

                stopAutoRefreshButton.addActionListener(e -> {

                    if (autoRefreshService.isRunning()) {
                        autoRefreshService.stop();
                        JOptionPane.showMessageDialog(getParentFrame(),
                                "Auto-Refresh has been stopped.",
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                });
                // =========================
                // Set Interval
                // =========================

                setIntervalButton.addActionListener(e -> {

                    String input = JOptionPane.showInputDialog(
                            getParentFrame(),
                            "Interval (seconds):",
                            autoRefreshService.getInterval()
                    );

                    if (input != null) {
                        try {
                            int newInterval = Integer.parseInt(input);
                            if (newInterval > 0) {
                                autoRefreshService.setInterval(newInterval);

                                if (autoRefreshService.isRunning()) {
                                    autoRefreshService.stop();
                                    autoRefreshService.start();
                                }

                                JOptionPane.showMessageDialog(getParentFrame(),
                                        "Interval updated to " + newInterval + " seconds");
                            }
                        } catch (NumberFormatException ex) {
                            JOptionPane.showMessageDialog(getParentFrame(), "Invalid number!");
                        }
                    }
                });

                // =========================
                // Extracted parameters from Location header
                // =========================
                extractLocationButton.addActionListener(e -> {

                    Optional<HttpRequestResponse> rr = getRequestResponse(event);

                    if (rr.isPresent() && rr.get().response() != null) {

                        String location = rr.get().response().headers().stream()
                                .filter(h -> h.name().equalsIgnoreCase("Location"))
                                .map(h -> h.value())
                                .findFirst()
                                .orElse(null);

                        if (location != null && location.contains("?")) {

                            parameterManager.extractFromUrl(location);

                            JOptionPane.showMessageDialog(getParentFrame(),
                                    "Extracted parameters from Location header.",
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(getParentFrame(),
                                    "No Location header with parameters found.",
                                    "Warning",
                                    JOptionPane.WARNING_MESSAGE);
                        }
                    }
                });

                return List.of(
                        setReferenceButton,
                        extractParamsButton,
                        extractLocationButton,
                        manageParamsButton,
                        new JSeparator(),
                        startAutoRefreshButton,
                        stopAutoRefreshButton,
                        setIntervalButton
                );
            }

            private Frame getParentFrame() {
                return api.userInterface().swingUtils().suiteFrame();
            }

            private Optional<HttpRequestResponse> getRequestResponse(ContextMenuEvent event) {

                if (event.messageEditorRequestResponse().isPresent()) {
                    return Optional.of(event.messageEditorRequestResponse().get().requestResponse());
                }

                List<HttpRequestResponse> selected = event.selectedRequestResponses();

                if (selected != null && !selected.isEmpty()) {
                    return Optional.of(selected.get(0));
                }

                return Optional.empty();
            }
        });
    }
}