import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import core.AutoRefreshService;
import core.DebuggingMode;
import core.ParameterManager;
import core.Settings;
import gui.ParamRotatorRequestEditor;
import gui.UIController;
import handler.RepeaterHttpHandler;

/**
 * Main entry point for the ParamRotator Burp Suite extension.
 * Initializes core components:
 * - core.DebuggingMode
 * - core.ParameterManager
 * - core.AutoRefreshService
 * - core.Settings
 * - gui.UIController
 * - handler.RepeaterHttpHandler
 * - gui.ParamRotatorRequestEditor
 */
public class Main implements BurpExtension {

    private MontoyaApi api;
    private AutoRefreshService autoRefreshService;
    private ParameterManager parameterManager;
    private UIController uiController;
    private DebuggingMode debuggingMode;
    private Settings settings;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        this.api = montoyaApi;
        api.extension().setName("ParamRotator");

        this.debuggingMode      = new DebuggingMode(api);
        this.settings           = new Settings(api, debuggingMode);
        this.parameterManager   = new ParameterManager(api, debuggingMode);
        this.autoRefreshService = new AutoRefreshService(api, parameterManager, debuggingMode);

        this.uiController = new UIController(
                api, parameterManager, autoRefreshService, debuggingMode, settings
        );

       if (Settings.isRepeaterListenerMode()){
           // Auto-update tokens after every Repeater send
           api.http().registerHttpHandler(
                   new RepeaterHttpHandler(api, parameterManager, autoRefreshService, debuggingMode, settings)
           );

           // Register custom request editor tab
           api.userInterface().registerHttpRequestEditorProvider(requestResponse ->
                   new ParamRotatorRequestEditor(api, parameterManager)
           );
       }

        api.logging().logToOutput("ParamRotator loaded successfully!");
    }
}