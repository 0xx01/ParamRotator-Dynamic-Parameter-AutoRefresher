package gui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationType;
import core.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles Burp Suite UI interactions and context menu registration.
 *
 * Provides menu items for:
 * - Setting reference request
 * - Extracting parameters
 * - Managing parameters
 * - Starting/stopping auto-refresh
 * - Setting interval and debugging mode
 */
public class UIController {

    private final MontoyaApi api;
    private final ParameterManager parameterManager;
    private final AutoRefreshService autoRefreshService;
    private final DebuggingMode debuggingMode;
    private final Settings settings;

    /**
     * Constructs the UI controller and registers context menu items.
     *
     * @param api                Montoya API instance
     * @param parameterManager   ParameterManager instance
     * @param autoRefreshService AutoRefreshService instance
     * @param debuggingMode      DebuggingMode instance
     * @param settings           Settings instance
     */
    public UIController(
            MontoyaApi api,
            ParameterManager parameterManager,
            AutoRefreshService autoRefreshService,
            DebuggingMode debuggingMode,
            Settings settings) {

        this.api = api;
        this.parameterManager = parameterManager;
        this.autoRefreshService = autoRefreshService;
        this.debuggingMode = debuggingMode;
        this.settings = settings;

        registerMenuItems();
    }

    /** Registers all context menu items in Burp Suite */
    private void registerMenuItems() {

        // Handle extension unload
        api.extension().registerUnloadingHandler(() -> {
            if (autoRefreshService.isRunning()) {
                autoRefreshService.stop();
            }

            api.logging().logToOutput(
                    "Extension unloaded cleanly"
            );
        });

        // Register context menu provider
        api.userInterface().registerContextMenuItemsProvider(
                new ContextMenuItemsProvider() {

                    @Override
                    public List<Component> provideMenuItems(
                            ContextMenuEvent event) {

                        // ===== Menu Items =====

                        JMenuItem setReferenceButton =
                                new JMenuItem("Set as Reference");

                        JMenuItem extractParamsButton =
                                new JMenuItem(
                                        "Extract Parameters from Request"
                                );

                        JMenuItem extractLocationButton =
                                new JMenuItem(
                                        "Extract Parameters from Location Header"
                                );

                        JMenuItem manageParamsButton =
                                new JMenuItem("Manage Parameters...");

                        JMenuItem startAutoRefreshButton =
                                new JMenuItem("Start Auto-Refresh");

                        JMenuItem stopAutoRefreshButton =
                                new JMenuItem("Stop Auto-Refresh");

                        JMenuItem setIntervalButton =
                                new JMenuItem("Set Interval");

                        JMenuItem debuggingModeButton =
                                new JMenuItem("Debugging Mode");

                        JMenuItem removeReferenceButton =
                                new JMenuItem(
                                        "Remove the current reference request"
                                );

                        JMenuItem toggleModeButton =
                                new JMenuItem(
                                        "Mode: " + settings.getModeLabel()
                                );

                        // ===== Action Listeners =====

                        debuggingModeButton.addActionListener(
                                e -> toggleDebuggingMode()
                        );

                        setReferenceButton.addActionListener(
                                e -> setReferenceRequest(event)
                        );

                        removeReferenceButton.addActionListener(
                                e -> removeReferenceRequest(event)
                        );

                        extractParamsButton.addActionListener(
                                e -> extractParameters(event)
                        );

                        extractLocationButton.addActionListener(
                                e -> extractFromLocationHeader(event)
                        );

                        manageParamsButton.addActionListener(
                                e -> SwingUtilities.invokeLater(() ->
                                        new ParameterDialog(
                                                getParentFrame(),
                                                api,
                                                parameterManager
                                        ).setVisible(true)
                                )
                        );

                        startAutoRefreshButton.addActionListener(
                                e -> startAutoRefresh(event)
                        );

                        stopAutoRefreshButton.addActionListener(
                                e -> stopAutoRefresh()
                        );

                        setIntervalButton.addActionListener(
                                e -> setAutoRefreshInterval()
                        );

                        toggleModeButton.addActionListener(e -> {
                            settings.toggleMode();

                            JOptionPane.showMessageDialog(
                                    getParentFrame(),
                                    "Switched to: "
                                            + settings.getModeLabel(),
                                    "Mode Changed",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        });

                        // ===== Build Menu =====

                        List<Component> items = new ArrayList<>();

                        /*
                         * Repeater-specific controls.
                         *
                         * These controls are only displayed inside
                         * Burp's Message Editor.
                         */
                        if (event.invocationType()
                                == InvocationType.MESSAGE_EDITOR_REQUEST
                                && event.messageEditorRequestResponse()
                                .isPresent()) {

                            // Show only if reference request is set
                            if (autoRefreshService.getReferenceRequest()
                                    != null
                                    && Settings.isReferenceRequestMode()) {

                                items.add(startAutoRefreshButton);
                                items.add(stopAutoRefreshButton);
                                items.add(setIntervalButton);
                                items.add(removeReferenceButton);
                            }
                        }

                        /*
                         * Set Reference can be used from a request/response
                         * context when reference mode is enabled.
                         */
                        if (autoRefreshService.getReferenceRequest()
                                == null
                                && Settings.isReferenceRequestMode()) {

                            items.add(setReferenceButton);
                        }

                        /*
                         * IMPORTANT:
                         *
                         * Parameter extraction requires a Message Editor
                         * request/response context.
                         *
                         * Previously these two menu items were added
                         * unconditionally, causing them to appear in
                         * Target tree, Proxy history, etc.
                         */
                        if (event.invocationType()
                                == InvocationType.MESSAGE_EDITOR_REQUEST
                                && event.messageEditorRequestResponse()
                                .isPresent()) {

                            items.add(extractParamsButton);
                            items.add(extractLocationButton);
                        }

                        // General extension controls
                        items.add(manageParamsButton);
                        items.add(toggleModeButton);
                        items.add(debuggingModeButton);

                        return items;
                    }

                    /** Gets the Burp Suite main frame */
                    private Frame getParentFrame() {
                        return api.userInterface()
                                .swingUtils()
                                .suiteFrame();
                    }

                    /**
                     * Retrieves the first available HttpRequestResponse
                     * from the event.
                     */
                    private Optional<HttpRequestResponse>
                    getRequestResponse(ContextMenuEvent event) {

                        if (event.messageEditorRequestResponse()
                                .isPresent()) {

                            return Optional.of(
                                    event.messageEditorRequestResponse()
                                            .get()
                                            .requestResponse()
                            );
                        }

                        List<HttpRequestResponse> selected =
                                event.selectedRequestResponses();

                        return (selected == null || selected.isEmpty())
                                ? Optional.empty()
                                : Optional.of(selected.get(0));
                    }

                    /** Toggle debugging mode */
                    private void toggleDebuggingMode() {

                        debuggingMode.toggleDebuggingMode();

                        String status =
                                debuggingMode.isDebuggingModeEnabled()
                                        ? "ON"
                                        : "OFF";

                        JOptionPane.showMessageDialog(
                                getParentFrame(),
                                "Debugging Mode is now " + status,
                                "Debugging Mode",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }

                    /** Save current request as reference */
                    private void setReferenceRequest(
                            ContextMenuEvent event) {

                        getRequestResponse(event).ifPresent(r -> {

                            autoRefreshService.setReferenceRequest(
                                    r.request()
                            );

                            JOptionPane.showMessageDialog(
                                    getParentFrame(),
                                    "Reference request saved!",
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        });
                    }

                    /** Remove current reference request */
                    private void removeReferenceRequest(
                            ContextMenuEvent event) {

                        getRequestResponse(event).ifPresent(r -> {

                            autoRefreshService.removeReferenceRequest();

                            JOptionPane.showMessageDialog(
                                    getParentFrame(),
                                    "Reference request is deleted now!",
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        });
                    }

                    /** Extract parameters from request */
                    private void extractParameters(
                            ContextMenuEvent event) {

                        getRequestResponse(event).ifPresent(r -> {

                            parameterManager.extractFromRequest(
                                    r.request()
                            );

                            JOptionPane.showMessageDialog(
                                    getParentFrame(),
                                    "Extracted "
                                            + parameterManager
                                            .getAllParameters()
                                            .size()
                                            + " parameters.",
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        });
                    }

                    /** Start auto-refresh — Repeater only */
                    private void startAutoRefresh(
                            ContextMenuEvent event) {

                        if (event.invocationType()
                                != InvocationType.MESSAGE_EDITOR_REQUEST
                                || event.messageEditorRequestResponse()
                                .isEmpty()) {

                            JOptionPane.showMessageDialog(
                                    getParentFrame(),
                                    "This feature can only be used inside Repeater!",
                                    "Warning",
                                    JOptionPane.WARNING_MESSAGE
                            );

                            return;
                        }

                        var editor =
                                event.messageEditorRequestResponse().get();

                        HttpRequest currentRequest =
                                editor.requestResponse().request();

                        autoRefreshService.setCurrentRepeater(
                                editor,
                                currentRequest
                        );

                        if (!autoRefreshService.isRunning()) {
                            autoRefreshService.start();
                        }
                    }

                    /** Stop auto-refresh */
                    private void stopAutoRefresh() {

                        if (autoRefreshService.isRunning()) {

                            autoRefreshService.stop();

                            JOptionPane.showMessageDialog(
                                    getParentFrame(),
                                    "Auto-Refresh has been stopped.",
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        }
                    }

                    /** Set auto-refresh interval */
                    private void setAutoRefreshInterval() {

                        String input = JOptionPane.showInputDialog(
                                getParentFrame(),
                                "Interval (seconds):",
                                autoRefreshService.getInterval()
                        );

                        if (input != null) {

                            try {

                                int newInterval =
                                        Integer.parseInt(input);

                                if (newInterval > 0) {

                                    autoRefreshService.setInterval(
                                            newInterval
                                    );

                                    if (autoRefreshService.isRunning()) {
                                        autoRefreshService.stop();
                                        autoRefreshService.start();
                                    }

                                    JOptionPane.showMessageDialog(
                                            getParentFrame(),
                                            "Interval updated to "
                                                    + newInterval
                                                    + " seconds"
                                    );
                                }

                            } catch (NumberFormatException ex) {

                                JOptionPane.showMessageDialog(
                                        getParentFrame(),
                                        "Invalid number!"
                                );
                            }
                        }
                    }

                    /** Extract parameters from Location header */
                    private void extractFromLocationHeader(
                            ContextMenuEvent event) {

                        getRequestResponse(event).ifPresent(r -> {

                            if (r.response() != null) {

                                String location =
                                        r.response()
                                                .headers()
                                                .stream()
                                                .filter(h ->
                                                        h.name()
                                                                .equalsIgnoreCase(
                                                                        "Location"
                                                                )
                                                )
                                                .map(h -> h.value())
                                                .findFirst()
                                                .orElse(null);

                                if (location != null) {

                                    int extracted =
                                            parameterManager
                                                    .extractFromLocationHeader(
                                                            location
                                                    );

                                    if (extracted > 0) {

                                        JOptionPane.showMessageDialog(
                                                getParentFrame(),
                                                "Extracted "
                                                        + extracted
                                                        + " parameters from Location header.",
                                                "Success",
                                                JOptionPane.INFORMATION_MESSAGE
                                        );

                                    } else {

                                        JOptionPane.showMessageDialog(
                                                getParentFrame(),
                                                "No new parameters found in Location header.",
                                                "Warning",
                                                JOptionPane.WARNING_MESSAGE
                                        );
                                    }

                                } else {

                                    JOptionPane.showMessageDialog(
                                            getParentFrame(),
                                            "No Location header with parameters found.",
                                            "Warning",
                                            JOptionPane.WARNING_MESSAGE
                                    );
                                }
                            }
                        });
                    }
                }
        );
    }
}