package handler;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import core.AutoRefreshService;
import core.DebuggingMode;
import core.ParameterManager;
import core.Settings;

/**
 * HTTP handler that automatically injects and extracts tokens
 * for every request and response passing through Burp Repeater.
 * <p>
 * Only active when plugin is in Repeater Listener mode.
 * </p>
 */
public class RepeaterHttpHandler implements HttpHandler {

    private final MontoyaApi api;
    private final ParameterManager parameterManager;
    private final AutoRefreshService autoRefreshService;
    private final DebuggingMode debuggingMode;
    private final Settings settings;

    /**
     * Constructs the handler.RepeaterHttpHandler.
     *
     * @param api                Montoya API instance
     * @param parameterManager   core.ParameterManager instance
     * @param autoRefreshService core.AutoRefreshService instance
     * @param debuggingMode      core.DebuggingMode instance
     * @param settings           core.Settings instance
     */
    public RepeaterHttpHandler(MontoyaApi api,
                               ParameterManager parameterManager,
                               AutoRefreshService autoRefreshService,
                               DebuggingMode debuggingMode,
                               Settings settings) {
        this.api = api;
        this.parameterManager = parameterManager;
        this.autoRefreshService = autoRefreshService;
        this.debuggingMode = debuggingMode;
        this.settings = settings;
    }

    /**
     * Injects fresh tokens into every Repeater request.
     * Only runs in Repeater Listener mode.
     */
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent request) {
        if (request.toolSource().toolType() == ToolType.REPEATER
                && settings.isRepeaterListenerMode()
                && !parameterManager.getAllParameters().isEmpty()) {

            HttpRequest updatedRequest = parameterManager.applyToRequest(request);
            if (updatedRequest != null) {
                if (debuggingMode.isDebuggingModeEnabled()) {
                    api.logging().logToOutput("Auto-inject: applied parameters to Repeater request");
                }
                return RequestToBeSentAction.continueWith(updatedRequest);
            }
        }
        return RequestToBeSentAction.continueWith(request);
    }

    /**
     * Extracts fresh tokens from every Repeater response.
     * Only runs in Repeater Listener mode.
     */
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived response) {
        if (response.toolSource().toolType() == ToolType.REPEATER
                && settings.isRepeaterListenerMode()
                && !parameterManager.getAllParameters().isEmpty()) {

            HttpRequestResponse reqRes = HttpRequestResponse.httpRequestResponse(
                    response.initiatingRequest(), response
            );

            int updated = parameterManager.updateFromResponse(reqRes).size();

            if (debuggingMode.isDebuggingModeEnabled() && updated > 0) {
                api.logging().logToOutput("Auto-extract: updated " + updated + " parameters from Repeater response");
            }

        }
        return ResponseReceivedAction.continueWith(response);
    }
}