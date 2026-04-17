package gui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.*;
import core.ParameterManager;
import core.Settings;

import javax.swing.*;
import java.awt.*;

/**
 * Custom request editor tab shown inside Burp Repeater.
 * Displays the current request with updated tokens applied.
 */

public class ParamRotatorRequestEditor implements ExtensionProvidedHttpRequestEditor {

    private final MontoyaApi api;
    private final ParameterManager parameterManager;
    private final JTextArea textArea;
    private HttpRequest currentRequest;

    public ParamRotatorRequestEditor(MontoyaApi api, ParameterManager parameterManager) {
        this.api = api;
        this.parameterManager = parameterManager;
        this.textArea = new JTextArea();
        this.textArea.setEditable(false);
        this.textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        this.textArea.setBackground(Color.decode("#1e1e1e"));
        this.textArea.setForeground(Color.decode("#d4d4d4"));
    }

    @Override
    public HttpRequest getRequest() {
        if (currentRequest == null) return null;
        // Apply latest tokens before returning
        HttpRequest updated = parameterManager.applyToRequest(currentRequest);
        return updated != null ? updated : currentRequest;
    }


    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        this.currentRequest = requestResponse.request();
        // Apply latest tokens and display
        HttpRequest updated = parameterManager.applyToRequest(currentRequest);
        HttpRequest display = updated != null ? updated : currentRequest;
        SwingUtilities.invokeLater(() ->
                textArea.setText(display.toString())
        );
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        // Show tab only if we have extracted parameters & check the mode
        return Settings.isRepeaterListenerMode()
                && !parameterManager.getAllParameters().isEmpty();
    }

    @Override
    public String caption() {
        return "ParamRotator";
    }

    @Override
    public Component uiComponent() {
        return new JScrollPane(textArea);
    }

    @Override
    public Selection selectedData() {
        return null;
    }

    @Override
    public boolean isModified() {
        return false;
    }
}