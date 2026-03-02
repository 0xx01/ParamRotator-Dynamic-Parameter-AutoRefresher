import burp.api.montoya.MontoyaApi;

public class DebuggingMode {

    private boolean debuggingModeEnabled = false;
    private final MontoyaApi api;

    public DebuggingMode(MontoyaApi api){
        this.api = api;
    }

    public void debuggingModeUpdate() {
        debuggingModeEnabled = !debuggingModeEnabled;
        api.logging().logToOutput("Debugging Mode: "
                + (debuggingModeEnabled ? "ENABLED" : "DISABLED"));    }

    public boolean isDebuggingModeEnabled() { return debuggingModeEnabled; }


}
