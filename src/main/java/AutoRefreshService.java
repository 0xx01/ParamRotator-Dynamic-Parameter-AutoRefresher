import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import javax.swing.*;
import java.util.concurrent.*;

/**
 * Service that handles automatic refreshing of HTTP requests in Burp Repeater.
 * <p>
 * Sends a reference request at regular intervals, updates parameters from the response,
 * and applies them to the current request in Repeater.
 * Supports debug logging and configurable interval.
 * </p>
 */
public class AutoRefreshService {

    private final MontoyaApi api;
    private final ParameterManager parameterManager;
    private final DebuggingMode debuggingMode;

    /** Reference request used to fetch updated parameters */
    private HttpRequest referenceRequest;

    /** Clean base request to avoid parameter accumulation */
    private volatile HttpRequest baseRequest;

    /** Current request displayed in Repeater editor */
    private volatile HttpRequest currentRequestInRepeater;

    private burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse currentMessageEditor;

    /** Scheduler for periodic refreshes */
    private ScheduledExecutorService scheduler;

    /** Interval in seconds between refreshes */
    private int interval = 30;

    /** Flag indicating if auto-refresh is running */
    private volatile boolean running = false;

    /**
     * Constructs the AutoRefreshService.
     *
     * @param api              Montoya API instance
     * @param parameterManager ParameterManager instance
     * @param debuggingMode    DebuggingMode instance
     */
    public AutoRefreshService(MontoyaApi api, ParameterManager parameterManager, DebuggingMode debuggingMode) {
        this.api = api;
        this.parameterManager = parameterManager;
        this.debuggingMode = debuggingMode;
    }


    /** Sets the reference request used for refreshing parameters */
    public void setReferenceRequest(HttpRequest referenceRequest) {
        this.referenceRequest = referenceRequest;
    }

    /**
     * Sets the current Repeater editor and request.
     * Saves a clean base request to avoid parameter accumulation.
     *
     * @param editor  The Repeater message editor
     * @param current Current HttpRequest in Repeater
     */
    public void setCurrentRepeater(
            burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse editor,
            HttpRequest current
    ) {
        this.currentMessageEditor = editor;
        this.currentRequestInRepeater = current;
        this.baseRequest = current; // Save clean copy
    }

    /** Returns the refresh interval in seconds */
    public int getInterval() {
        return interval;
    }

    /** Sets the refresh interval in seconds */
    public void setInterval(int interval) {
        this.interval = interval;
    }

    /** Returns whether auto-refresh is currently running */
    public boolean isRunning() {
        return running;
    }

    /**
     * Starts the auto-refresh scheduler.
     * <p>
     * Validates that a reference request, base request, editor, and parameters exist.
     * Otherwise shows a warning dialog and logs in debugging mode.
     * </p>
     */
    public void start() {
        if (running) stop();

        if (referenceRequest == null || baseRequest == null || currentMessageEditor == null
                || parameterManager.getAllParameters().isEmpty()) {

            if (debuggingMode.isDebuggingModeEnabled()) {
                api.logging().logToOutput("Auto-Refresh cannot start: missing references or parameters");
            }

            JOptionPane.showMessageDialog(null,
                    "Auto-Refresh cannot start: missing parameters or current request",
                    "Warning",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoRefresh-Thread");
            t.setDaemon(true);
            return t;
        });

        running = true;

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                refreshTokens();
            } catch (Exception ex) {
                api.logging().logToError("Auto-Refresh failed: " + ex.getMessage());
            }
        }, 5, interval, TimeUnit.SECONDS);

        if (debuggingMode.isDebuggingModeEnabled()) {
            api.logging().logToOutput("Auto-Refresh started with interval: " + interval + "s");
        }

        JOptionPane.showMessageDialog(null,
                "Auto-Refresh started successfully.",
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Stops the auto-refresh scheduler if running.
     * Waits briefly for termination and logs in debugging mode.
     */
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

        if (debuggingMode.isDebuggingModeEnabled()) {
            api.logging().logToOutput("Auto-Refresh stopped");
        }
    }

    /**
     * Core logic to refresh parameters and update the Repeater request.
     * <p>
     * Sends the reference request, updates parameters, and applies them to the
     * current Repeater request on the Swing EDT.
     * </p>
     */
    private void refreshTokens() {

        if (referenceRequest == null || baseRequest == null || currentMessageEditor == null) {
            if (debuggingMode.isDebuggingModeEnabled()) {
                api.logging().logToOutput("Auto-Refresh: missing references, skipping...");
            }
            return;
        }

        if (debuggingMode.isDebuggingModeEnabled()) {
            api.logging().logToOutput("Auto-Refresh: fetching new tokens...");
        }

        // Send reference request
        HttpRequestResponse response = api.http().sendRequest(referenceRequest);

        // Update parameters from response if auto-update is enabled
        if (parameterManager.isAutoUpdateEnabled()) {
            int updated = parameterManager.updateFromResponse(response).size();
            if (updated > 0 && debuggingMode.isDebuggingModeEnabled()) {
                api.logging().logToOutput("Auto-Refresh: updated " + updated + " parameters");
            }
        }

        // Apply updated parameters to CLEAN base request
        HttpRequest updatedRequest = parameterManager.applyToRequest(baseRequest);

        if (updatedRequest != null) {
            SwingUtilities.invokeLater(() -> {
                currentMessageEditor.setRequest(updatedRequest);
                currentRequestInRepeater = updatedRequest;

                if (debuggingMode.isDebuggingModeEnabled()) {
                    api.logging().logToOutput("Auto-Refresh: request updated with latest parameters");
                }
            });
        }
    }
}