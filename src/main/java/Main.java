import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

/**
 * Main entry point for the ParamRotator Burp Suite extension.
 * Initializes core components:
 * - DebuggingMode
 * - ParameterManager
 * - AutoRefreshService
 * - UIController
 */
public class Main implements BurpExtension {

    /** The Burp API object for interacting with the host application */
    private MontoyaApi api;

    /** Handles automatic token refresh and parameter updates */
    private AutoRefreshService autoRefreshService;

    /** Manages all parameters extracted or added by the user */
    private ParameterManager parameterManager;

    /** Handles UI elements and context menu actions */
    private UIController uiController;

    /** Controls logging and debug messages */
    private DebuggingMode debuggingMode;

    /**
     * Initialize the Burp extension and its core components.
     *
     * @param montoyaApi The Montoya API instance provided by Burp Suite
     */
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        this.api = montoyaApi;
        api.extension().setName("ParamRotator");

        this.debuggingMode = new DebuggingMode(api);
        this.parameterManager = new ParameterManager(api, debuggingMode);
        this.autoRefreshService = new AutoRefreshService(api, parameterManager, debuggingMode);
        this.uiController = new UIController(api, parameterManager, autoRefreshService, debuggingMode);

        api.logging().logToOutput("ParamRotator loaded successfully!");
    }
}