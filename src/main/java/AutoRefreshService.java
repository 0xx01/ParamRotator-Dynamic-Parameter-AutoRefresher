import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import javax.swing.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoRefreshService {

    private final MontoyaApi api;
    private final ParameterManager parameterManager;

    private HttpRequest referenceRequest;
    private HttpRequest currentRequestInRepeater;
    private burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse currentMessageEditor;

    private ScheduledExecutorService scheduler;
    private int interval = 30; // default 30s
    private boolean running = false;
    private DebuggingMode debuggingMode;

    public AutoRefreshService(MontoyaApi api, ParameterManager pm, DebuggingMode debuggingMode) {
        this.api = api;
        this.parameterManager = pm;
        this.debuggingMode = debuggingMode;
    }

    // --- Setters / Getters ---
    public void setReferenceRequest(HttpRequest referenceRequest) {
        this.referenceRequest = referenceRequest;
    }

    public void setCurrentRepeater(burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse editor,
                                   HttpRequest currentRequest) {
        this.currentMessageEditor = editor;
        this.currentRequestInRepeater = currentRequest;
    }

    public int getInterval() { return interval; }
    public void setInterval(int interval) { this.interval = interval; }

    public boolean isRunning() { return running; }

    // --- Start / Stop ---
    public void start() {
        if (running) stop();

        if (referenceRequest == null || currentRequestInRepeater == null || currentMessageEditor == null || parameterManager.getAllParameters().isEmpty()) {
            if (debuggingMode.isDebuggingModeEnabled() == true) {
                api.logging().logToOutput("Auto-Refresh cannot start: missing Parameters or reference request");
            }
            JOptionPane.showMessageDialog(null,
                    "Auto-Refresh cannot start: missing Parameters or current request",
                    "Warning",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        running = true;

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                refreshTokens();
            } catch (Exception ex) {
                api.logging().logToError("Auto-Refresh failed: " + ex.getMessage());
            }
        }, 0, interval, TimeUnit.SECONDS);
        if (debuggingMode.isDebuggingModeEnabled() == true) {
            api.logging().logToOutput("Auto-Refresh started with interval: " + interval + "s");
        }
        JOptionPane.showMessageDialog(null,
                "Auto-Refresh has started and is now actively monitoring tokens.",
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (debuggingMode.isDebuggingModeEnabled() == true){
            api.logging().logToOutput("Auto-Refresh stopped");
        }
    }

    // --- Token refresh logic ---
    private void refreshTokens() {
        if (referenceRequest == null || currentRequestInRepeater == null || currentMessageEditor == null) {
            if (debuggingMode.isDebuggingModeEnabled() == true) {
                api.logging().logToOutput("Auto-Refresh: missing references, skipping...");
            }
            return;
        }
        if (debuggingMode.isDebuggingModeEnabled() == true) {
            api.logging().logToOutput("Auto-Refresh: fetching new tokens...");
        }
        // send reference request
        HttpRequestResponse response = api.http().sendRequest(referenceRequest);

        // update all parameters from response
        if (parameterManager.isAutoUpdateEnabled()) {
            int updated = parameterManager.updateFromResponse(response).size();
            if (updated > 0 & debuggingMode.isDebuggingModeEnabled() == true) {
                api.logging().logToOutput("Auto-Refresh: updated " + updated + " parameters");
            }
        }

        // update current request in Repeater with latest parameters
        HttpRequest updatedRequest = parameterManager.applyToRequest(currentRequestInRepeater);

        if (updatedRequest != null) {
            SwingUtilities.invokeLater(() -> {
                currentMessageEditor.setRequest(updatedRequest);
                currentRequestInRepeater = updatedRequest;
                if (debuggingMode.isDebuggingModeEnabled() == true) {
                    api.logging().logToOutput("Auto-Refresh: request updated with latest parameters");
                }
                });
        }
    }
}