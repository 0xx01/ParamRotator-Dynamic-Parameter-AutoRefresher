import burp.api.montoya.MontoyaApi;

/**
 * Handles the extension's debugging mode.
 * <p>
 * Provides the ability to toggle debugging on/off and log messages
 * via Burp's Montoya API.
 * </p>
 */
public class DebuggingMode {

    /** Flag indicating whether debugging mode is enabled */
    private boolean debuggingModeEnabled = false;

    /** Montoya API for logging purposes */
    private final MontoyaApi api;

    /**
     * Constructs a DebuggingMode instance.
     *
     * @param api Montoya API instance
     */
    public DebuggingMode(MontoyaApi api) {
        this.api = api;
    }

    /**
     * Toggles debugging mode on or off.
     * Logs the current status to Burp's output.
     */
    public void toggleDebuggingMode() {
        debuggingModeEnabled = !debuggingModeEnabled;
        api.logging().logToOutput("Debugging Mode: " + (debuggingModeEnabled ? "ENABLED" : "DISABLED"));
    }

    /**
     * Checks if debugging mode is currently enabled.
     *
     * @return true if debugging mode is enabled, false otherwise
     */
    public boolean isDebuggingModeEnabled() {
        return debuggingModeEnabled;
    }
}