package core;

import burp.api.montoya.MontoyaApi;

/**
 * Manages global plugin settings.
 * <p>
 * Controls the refresh mode:
 * - REFERENCE_REQUEST: uses a saved reference request to fetch parameters
 * - REPEATER_LISTENER: intercepts Repeater responses to extract parameters
 * </p>
 */
public class Settings {

    /**
     * Defines how the plugin fetches fresh parameters.
     */
    public enum RefreshMode {
        /** Send a saved reference request to obtain new parameters */
        REFERENCE_REQUEST,
        /** Extract parameters from live Repeater responses */
        REPEATER_LISTENER
    }

    private final MontoyaApi api;
    private final DebuggingMode debuggingMode;

    /** Current refresh mode — default is Repeater Listener */
    private static volatile RefreshMode mode = RefreshMode.REPEATER_LISTENER;

    /**
     * Constructs the core.Settings manager.
     *
     * @param api           Montoya API instance
     * @param debuggingMode core.DebuggingMode instance
     */
    public Settings(MontoyaApi api, DebuggingMode debuggingMode) {
        this.api = api;
        this.debuggingMode = debuggingMode;
    }

    /** Returns the current refresh mode */
    public RefreshMode getMode() {
        return mode;
    }

    /** Sets the refresh mode */
    public void setMode(RefreshMode mode) {
        this.mode = mode;
        if (debuggingMode.isDebuggingModeEnabled()) {
            api.logging().logToOutput("Mode switched to: " + mode.name());
        }
    }

    /** Toggles between the two modes */
    public void toggleMode() {
        if (mode == RefreshMode.REPEATER_LISTENER) {
            setMode(RefreshMode.REFERENCE_REQUEST);
        } else {
            setMode(RefreshMode.REPEATER_LISTENER);
        }
    }

    /** Returns true if plugin is in Reference Request mode */
    public static boolean isReferenceRequestMode() {
        return mode == RefreshMode.REFERENCE_REQUEST;
    }

    /** Returns true if plugin is in Repeater Listener mode */
    public static boolean isRepeaterListenerMode() {
        return mode == RefreshMode.REPEATER_LISTENER;
    }

    /** Returns a human-readable label for the current mode */
    public String getModeLabel() {
        return mode == RefreshMode.REFERENCE_REQUEST
                ? "Reference Request"
                : "Repeater Listener";
    }
}