package gui;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import core.ParameterManager;
import core.Settings;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom request editor tab shown inside Burp Repeater.
 *
 * Displays the current request with updated parameters applied.
 *
 * Request processing is performed asynchronously to avoid blocking
 * Burp's Swing EDT.
 */
public class ParamRotatorRequestEditor implements ExtensionProvidedHttpRequestEditor {

    private final MontoyaApi api;
    private final ParameterManager parameterManager;

    /**
     * Native Burp HTTP request editor.
     */
    private final HttpRequestEditor requestEditor;

    /**
     * Current request received from Burp.
     */
    private volatile HttpRequest currentRequest;

    /**
     * Latest request after parameter injection.
     */
    private volatile HttpRequest processedRequest;

    /**
     * Used to prevent an old background task from overwriting
     * a newer request.
     */
    private final AtomicLong requestGeneration = new AtomicLong(0);

    public ParamRotatorRequestEditor(
            MontoyaApi api,
            ParameterManager parameterManager) {

        this.api = api;
        this.parameterManager = parameterManager;

        this.requestEditor = api.userInterface()
                .createHttpRequestEditor(EditorOptions.READ_ONLY);
    }

    /**
     * Returns the latest processed request.
     *
     * No parameter processing is performed here because Burp may
     * invoke this method on the Swing EDT.
     */
    @Override
    public HttpRequest getRequest() {

        HttpRequest processed = processedRequest;

        if (processed != null) {
            return processed;
        }

        return currentRequest;
    }

    /**
     * Receives a new request/response from Burp.
     *
     * Request processing is performed on a background thread
     * so that applyToRequest() does not block the EDT.
     */
    @Override
    public void setRequestResponse(
            HttpRequestResponse requestResponse) {

        if (requestResponse == null ||
                requestResponse.request() == null) {

            currentRequest = null;
            processedRequest = null;
            requestGeneration.incrementAndGet();

            return;
        }

        final HttpRequest request = requestResponse.request();

        currentRequest = request;

        /*
         * Keep the original request available while the background
         * processing is running.
         */
        processedRequest = request;

        /*
         * Generate a unique ID for this request.
         */
        final long generation =
                requestGeneration.incrementAndGet();

        /*
         * Run the potentially expensive operation outside
         * the Swing EDT.
         */
        CompletableFuture
                .supplyAsync(() -> applyParametersSafely(request))
                .thenAccept(processed -> {

                    if (processed == null) {
                        return;
                    }

                    /*
                     * Ignore stale results.
                     */
                    if (generation != requestGeneration.get()) {
                        return;
                    }

                    processedRequest = processed;

                    /*
                     * UI updates must happen on the EDT.
                     */
                    SwingUtilities.invokeLater(() -> {

                        /*
                         * Make sure this is still the newest request.
                         */
                        if (generation != requestGeneration.get()) {
                            return;
                        }

                        requestEditor.setRequest(processed);
                    });
                })
                .exceptionally(throwable -> {

                    api.logging().logToError(
                            "ParamRotator request processing failed: "
                                    + throwable.getMessage()
                    );

                    return null;
                });
    }

    /**
     * Applies the stored parameters to a request.
     *
     * This method runs outside the Swing EDT.
     */
    private HttpRequest applyParametersSafely(
            HttpRequest request) {

        try {

            HttpRequest updated =
                    parameterManager.applyToRequest(request);

            return updated != null ? updated : request;

        } catch (RuntimeException e) {

            api.logging().logToError(
                    "Failed to apply ParamRotator parameters: "
                            + e.getMessage()
            );

            return request;
        }
    }

    /**
     * Determines whether this editor should be displayed.
     */
    @Override
    public boolean isEnabledFor(
            HttpRequestResponse requestResponse) {

        return Settings.isRepeaterListenerMode()
                && requestResponse != null
                && requestResponse.request() != null
                && !parameterManager
                .getAllParameters()
                .isEmpty();
    }

    /**
     * Caption displayed in Burp.
     */
    @Override
    public String caption() {
        return "ParamRotator";
    }

    /**
     * Returns Burp's native HTTP request editor.
     */
    @Override
    public Component uiComponent() {
        return requestEditor.uiComponent();
    }

    /**
     * Returns the current selection.
     */
    @Override
    public Selection selectedData() {
        return requestEditor.selection().orElse(null);
    }

    /**
     * The editor is read-only.
     */
    @Override
    public boolean isModified() {
        return false;
    }
}

