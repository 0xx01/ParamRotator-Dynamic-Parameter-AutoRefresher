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

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        api = montoyaApi;
        api.extension().setName("ParamRotator");

        parameterManager = new ParameterManager(api);
        autoRefreshService = new AutoRefreshService(api, parameterManager);
        uiController = new UICONTROLLER(api, parameterManager, autoRefreshService);

        api.logging().logToOutput("ParamRotator loaded successfully!");
    }
}