import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;

public class Main implements BurpExtension {

    public static MontoyaApi api;

    public HttpRequest referenceRequest;
    public String referenceFullUrl = "";

    public AutoRefreshService autoRefreshService;
    public ParameterManager parameterManager;
    public UICONTROLLER uiController;
    public DebuggingMode debuggingMode;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        api = montoyaApi;
        api.extension().setName("ParamRotator");
        debuggingMode = new DebuggingMode(api);
        parameterManager = new ParameterManager(api, debuggingMode);
        autoRefreshService = new AutoRefreshService(api, parameterManager, debuggingMode);
        uiController = new UICONTROLLER(api, parameterManager, autoRefreshService, debuggingMode);
        api.logging().logToOutput("ParamRotator loaded successfully!");
    }
}