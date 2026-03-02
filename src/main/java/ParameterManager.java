import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.core.ByteArray;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterManager {
    private final MontoyaApi api;
    private final List<Parameter> parameters = new CopyOnWriteArrayList<>();
    private boolean autoUpdateEnabled = true;
    private DebuggingMode debuggingMode;

    // Patterns for finding ANY parameter values in responses
    private static final Pattern[] VALUE_PATTERNS = {
            // URL pattern: param=value
            Pattern.compile("([a-zA-Z0-9_]+)=([^&\\s\"'<>&]+)"),

            // JSON pattern: "param":"value"
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\""),

            // HTML input pattern: name="param" value="value"
            Pattern.compile("name=[\"']([^\"']+)[\"']\\s+value=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
    };

    public ParameterManager(MontoyaApi api, DebuggingMode debuggingMode) {
        this.api = api;
        this.debuggingMode = debuggingMode;
    }

    /**
     * Extract parameters from request (URL)
     */
    public void extractFromRequest(HttpRequest request) {
        parameters.clear();

        // Extract from URL
        extractFromUrl(request.url());

        // Extract from body
        //extractFromBody(request);
        if (debuggingMode.isDebuggingModeEnabled() == true) {
            api.logging().logToOutput("Extracted " + parameters.size() + " parameters from request");
        }
        }

    /**
     * Extract parameters from ANY URL
     */
    public void extractFromUrl(String url) {
        if (!url.contains("?")) return;

        String queryString = url.split("\\?", 2)[1];

        for (String param : queryString.split("&")) {
            String[] keyValue = param.split("=", 2);
            String name = keyValue[0];
            String value = keyValue.length > 1 ? keyValue[1] : "";

            addParameter(new Parameter(name, value, Parameter.ParameterSource.URL_QUERY));
        }
    }

    /**
     * Extract parameters from request body
     */
    /**
     * private void extractFromBody(HttpRequest request) {
     *         String body = request.bodyToString();
     *         if (body.isEmpty()) return;
     *
     *         String contentType = request.headerValue("Content-Type");
     *
     *         if (contentType != null) {
     *             if (contentType.contains("application/x-www-form-urlencoded")) {
     *                 return;
     *                 //extractFromFormBody(body);
     *             } else if (contentType.contains("application/json")) {
     *                 return;
     *                 //extractFromJsonBody(body);
     *             }
     *         }
     *     }
     */

    /**
     * private void extractFromFormBody(String body) {
     *         for (String param : body.split("&")) {
     *             String[] keyValue = param.split("=", 2);
     *             if (keyValue.length == 2) {
     *                 addParameter(new Parameter(keyValue[0], keyValue[1],
     *                         Parameter.ParameterSource.BODY_FORM));
     *             }
     *         }
     *     }
     */

    /**
     * private void extractFromJsonBody(String json) {
     *         for (Pattern pattern : VALUE_PATTERNS) {
     *             Matcher matcher = pattern.matcher(json);
     *             while (matcher.find()) {
     *                 if (matcher.groupCount() >= 2) {
     *                     String name = matcher.group(1);
     *                     String value = matcher.group(2);
     *                     addParameter(new Parameter(name, value, Parameter.ParameterSource.BODY_JSON));
     *                 }
     *             }
     *         }
     *     }
     * @param param
     */

    private void addParameter(Parameter param) {
        // Check if already exists (by name only - works for ANY name)
        Optional<Parameter> existing = parameters.stream()
                .filter(p -> p.getName().equals(param.getName()))
                .findFirst();

        if (!existing.isPresent()) {
            parameters.add(param);
        }
    }

    /**
     * Update ANY parameters from response - finds ALL parameter values
     */
    public Map<String, String> updateFromResponse(HttpRequestResponse response) {
        if (!autoUpdateEnabled || response == null || response.response() == null) {
            return Collections.emptyMap();
        }

        String responseStr = response.response().toString();
        Map<String, String> updatedValues = new HashMap<>();

        // Loop through ALL parameters in the table
        for (Parameter param : parameters) {
            if (!param.isEnabled() || !param.isAutoUpdate()) continue;

            String foundValue = findValueInResponse(responseStr, param.getName());

            if (foundValue != null && !foundValue.equals(param.getValue())) {
                String oldValue = param.getValue();
                param.setValue(foundValue);
                updatedValues.put(param.getName(), foundValue);
                if (debuggingMode.isDebuggingModeEnabled() == true) {
                    api.logging().logToOutput(String.format(
                            "Auto-updated '%s': %s -> %s",
                            param.getName(), oldValue, foundValue));
                }
            }
        }

        if (!updatedValues.isEmpty()) {
            if (debuggingMode.isDebuggingModeEnabled() == true) {
                api.logging().logToOutput("Total auto-updates: " + updatedValues.size());
            }
        }

        return updatedValues;
    }

    /**
     * Find ANY parameter value in response.
     */
    private String findValueInResponse(String response, String paramName) {
        for (Pattern pattern : VALUE_PATTERNS) {
            Matcher matcher = pattern.matcher(response);

            while (matcher.find()) {
                if (matcher.groupCount() >= 2) {
                    String foundName = matcher.group(1).trim();
                    String foundValue = matcher.group(2).trim();

                    if (foundName.equals(paramName)) {
                        return foundValue;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Apply ALL parameters to request
     */
    public HttpRequest applyToRequest(HttpRequest originalRequest) {
        String requestStr = originalRequest.toString();
        String[] lines = requestStr.split("\\r?\\n");

        if (lines.length == 0) return originalRequest;

        // Update URL in first line with ALL parameters
        lines[0] = updateUrlInFirstLine(lines[0]);

        return HttpRequest.httpRequest(ByteArray.byteArray(String.join("\r\n", lines)));
    }

    private String updateUrlInFirstLine(String firstLine) {
        String[] parts = firstLine.split(" ");
        if (parts.length < 2) return firstLine;

        String method = parts[0];
        String fullPath = parts[1];
        String httpVersion = parts.length > 2 ? parts[2] : "HTTP/1.1";

        // Update URL with ALL enabled parameters
        if (fullPath.contains("?")) {
            String[] urlParts = fullPath.split("\\?", 2);
            String baseUrl = urlParts[0];
            String queryString = urlParts[1];

            Map<String, String> paramMap = new LinkedHashMap<>();

            // Parse existing parameters
            for (String param : queryString.split("&")) {
                String[] kv = param.split("=", 2);
                String name = kv[0];
                String value = kv.length > 1 ? kv[1] : "";
                paramMap.put(name, value);
            }

            // Update with ALL our parameters
            for (Parameter param : getEnabledParameters()) {
                paramMap.put(param.getName(), param.getValue());
            }

            // Rebuild query string
            StringBuilder newQuery = new StringBuilder();
            for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                if (newQuery.length() > 0) newQuery.append("&");
                newQuery.append(entry.getKey()).append("=").append(entry.getValue());
            }

            fullPath = baseUrl + "?" + newQuery.toString();
        } else {
            // Add parameters if none exist
            StringBuilder queryString = new StringBuilder();
            for (Parameter param : getEnabledParameters()) {
                if (queryString.length() > 0) queryString.append("&");
                queryString.append(param.getName()).append("=").append(param.getValue());
            }
            if (queryString.length() > 0) {
                fullPath = fullPath + "?" + queryString.toString();
            }
        }

        return method + " " + fullPath + " " + httpVersion;
    }

    /**
     * Add custom parameter
     */
    public void addCustomParameter(String name, String value) {

        // Check if parameter already exists
        boolean exists = parameters.stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
        if (exists) {
            JOptionPane.showMessageDialog(
                    null,
                    "The parameter already exists!",
                    "Warning",
                    JOptionPane.WARNING_MESSAGE
            );
            api.logging().logToOutput("Parameter already exists: " + name);
            return;
        }
        parameters.add(new Parameter(name, value, Parameter.ParameterSource.CUSTOM));
        if (debuggingMode.isDebuggingModeEnabled() == true) {
            api.logging().logToOutput("Added custom parameter: " + name);
        }

    }

    /**
     * Remove ANY parameter
     */
    public void removeParameter(String name) {
        parameters.removeIf(p -> p.getName().equals(name));
        if (debuggingMode.isDebuggingModeEnabled() == true) {
            api.logging().logToOutput("Removed parameter: " + name);
        }
    }

    /**
     * Update ANY parameter value
     */
    public void updateParameter(String name, String newValue) {
        parameters.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .ifPresent(p -> p.setValue(newValue));
    }

    /**
     * Toggle auto-update for ALL parameters
     */
    public void toggleAutoUpdate() {
        this.autoUpdateEnabled = !this.autoUpdateEnabled;
        if (debuggingMode.isDebuggingModeEnabled() == true) {
            api.logging().logToOutput("Auto-Update " + (autoUpdateEnabled ? "ENABLED" : "DISABLED"));
            }
        }

    public boolean isAutoUpdateEnabled() { return autoUpdateEnabled; }

    public List<Parameter> getAllParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public List<Parameter> getEnabledParameters() {
        return parameters.stream().filter(Parameter::isEnabled).toList();
    }
}