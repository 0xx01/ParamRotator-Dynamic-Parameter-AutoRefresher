import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.core.ByteArray;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.*;

/**
 * Manages extraction, storage, updating, and injection of parameters
 * into HTTP requests.
 *
 * Supported parameter sources:
 * - Matrix parameters (;param=value)
 * - Query parameters (?param=value)
 * - Custom parameters
 */
public class ParameterManager {

    private final MontoyaApi api;
    private final DebuggingMode debug;

    private final List<Parameter> parameters = new CopyOnWriteArrayList<>();
    private boolean autoUpdateEnabled = true;

    /** Regex patterns used to extract values from responses */
    private static final Pattern[] VALUE_PATTERNS = {
            Pattern.compile("([a-zA-Z0-9_]+)=([^&\\s\"'<>&?;#/]+)"),
            Pattern.compile("name=[\"']([^\"']+)[\"']\\s+value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"")
    };

    /** Pattern for extracting matrix parameters from path */
    private static final Pattern PATH_PATTERN = Pattern.compile(";([^=]+)=([^;?/&\\s]+)");

    /**
     * Constructs a ParameterManager.
     *
     * @param api   Montoya API
     * @param debug DebuggingMode instance
     */
    public ParameterManager(MontoyaApi api, DebuggingMode debug) {
        this.api = api;
        this.debug = debug;
    }

    /**
     * Extracts parameters from a request URL (matrix + query).
     *
     * @param request HTTP request to extract from
     */
    public void extractFromRequest(HttpRequest request) {
        parameters.clear();
        String url = request.url();
        extractPathParams(url);
        extractQueryParams(url);
        log("Extracted parameters: " + parameters.size());
    }

    /**
     * Updates parameters using response content.
     *
     * @param response HttpRequestResponse containing body
     * @return Map of updated parameter names and new values
     */
    public Map<String, String> updateFromResponse(HttpRequestResponse response) {
        if (!autoUpdateEnabled || response == null || response.response() == null)
            return Collections.emptyMap();

        String body = response.response().toString();
        Map<String, String> updates = new HashMap<>();

        for (Parameter p : parameters) {
            if (!p.isEnabled() || !p.isAutoUpdate()) continue;
            String newValue = findValue(body, p.getName());
            if (newValue != null && !newValue.equals(p.getValue())) {
                updates.put(p.getName(), newValue);
                log(p.getName() + " updated");
                p.setValue(newValue);
            }
        }
        return updates;
    }

    /**
     * Extracts parameters from a Location header URL string.
     * Merges into existing parameters without clearing them.
     *
     * @param locationUrl URL string from Location header
     * @return Number of newly added parameters
     */
    public int extractFromLocationHeader(String locationUrl) {
        int before = parameters.size();
        log("Extracting from Location header: " + locationUrl);
        extractPathParams(locationUrl);
        extractQueryParams(locationUrl);
        int added = parameters.size() - before;
        log("Added " + added + " parameters from Location header");
        return added;
    }

    /**
     * Applies stored parameters to a given request.
     *
     * @param request Base HTTP request
     * @return Updated HttpRequest with parameters injected
     */
    public HttpRequest applyToRequest(HttpRequest request) {
        String normalized = request.toString().replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        if (lines.length == 0) return request;

        lines[0] = rebuildRequestLine(lines[0]);

        return HttpRequest.httpRequest(ByteArray.byteArray(String.join("\r\n", lines)));
    }

    /**
     * Adds a user-defined custom parameter.
     *
     * @param name  Parameter name
     * @param value Initial value
     */
    public void addCustomParameter(String name, String value) {
        if (parameterExists(name)) {
            JOptionPane.showMessageDialog(null, "Parameter already exists");
            return;
        }
        parameters.add(new Parameter(name, value, Parameter.ParameterSource.CUSTOM));
    }

    /**
     * Removes a parameter by name.
     *
     * @param name Parameter name
     */
    public void removeParameter(String name) {
        parameters.removeIf(p -> p.getName().equals(name));
    }

    /**
     * @return true if auto-update is enabled
     */
    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }

    /**
     * @return Unmodifiable list of all parameters
     */
    public List<Parameter> getAllParameters() {
        return Collections.unmodifiableList(parameters);
    }

    /**
     * Updates an existing parameter value.
     *
     * @param name     Parameter name
     * @param newValue New value to set
     */
    public void updateParameter(String name, String newValue) {
        parameters.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .ifPresent(p -> p.setValue(newValue));
    }

    // ===================== Extraction Helpers =====================

    /** Extracts matrix parameters from URL path */
    private void extractPathParams(String url) {
        String path = url.split("\\?")[0];
        Matcher m = PATH_PATTERN.matcher(path);
        while (m.find()) {
            addParam(m.group(1), m.group(2), Parameter.ParameterSource.MATRIX_PARAMETER);
        }
    }

    /** Extracts query parameters from URL */
    private void extractQueryParams(String url) {
        if (!url.contains("?")) return;
        String query = url.split("\\?", 2)[1];
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            String paramName = kv[0].trim();
            if (paramName.isEmpty()) continue; // skip malformed pairs
            String paramValue = kv.length > 1 ? kv[1].trim() : "";
            addParam(paramName, paramValue, Parameter.ParameterSource.URL_QUERY);
        }
    }

    // ===================== Request Rebuilding Helpers =====================

    /**
     * Rebuilds the HTTP request line after applying stored parameters.
     * <p>
     * This method preserves the original HTTP method and protocol version
     * while replacing the path with the updated matrix and query parameters.
     *
     * @param firstLine the original request line (e.g. {@code GET /path HTTP/1.1})
     * @return the rebuilt request line with updated parameters
     */
    private String rebuildRequestLine(String firstLine) {

        if (firstLine == null || firstLine.isEmpty()) {
            return firstLine;
        }

        /*
         * Split request line into:
         * method | path | version
         *
         * Using limit=3 prevents unnecessary splitting
         * and preserves the full protocol version.
         */
        String[] parts = firstLine.split(" ", 3);

        if (parts.length < 2) {
            return firstLine;
        }

        String method = parts[0];
        String path = parts[1];
        String version = parts.length == 3 ? parts[2] : "";

        /*
         * Extract the base path before matrix or query parameters.
         */
        String basePath = path.split("[?;]", 2)[0];

        /*
         * Apply matrix parameters first:
         * /path;param=value
         */
        String withMatrix = appendParams(
                basePath,
                Parameter.ParameterSource.MATRIX_PARAMETER,
                ";"
        );

        /*
         * Then apply query parameters:
         * /path?key=value
         */
        String withQuery = appendParams(
                withMatrix,
                Parameter.ParameterSource.URL_QUERY,
                "&"
        );

        /*
         * Preserve the original HTTP version if present.
         */
        if (version.isEmpty()) {
            return method + " " + withQuery;
        }

        return method + " " + withQuery + " " + version;
    }

    /** Appends parameters of a specific type to a base path */
    private String appendParams(String base, Parameter.ParameterSource source, String separator) {
        List<Parameter> list = parameters.stream()
                .filter(p -> p.isEnabled() && p.getSource() == source)
                .toList();

        if (list.isEmpty()) return base;

        StringBuilder result = new StringBuilder(base);
        if (source == Parameter.ParameterSource.URL_QUERY) result.append("?");

        boolean first = true;
        for (Parameter p : list) {
            if (!first) result.append(separator);
            if (source == Parameter.ParameterSource.MATRIX_PARAMETER) result.append(";");
            result.append(p.getName()).append("=").append(p.getValue());
            first = false;
        }
        return result.toString();
    }

    /** Adds a parameter if not already present */
    private void addParam(String name, String value, Parameter.ParameterSource source) {
        if (!parameters.stream().anyMatch(p -> p.getName().equals(name) && p.getSource() == source)) {
            parameters.add(new Parameter(name, value, source));
        }
    }

    /** Checks if a parameter already exists */
    private boolean parameterExists(String name) {
        return parameters.stream().anyMatch(p -> p.getName().equals(name));
    }

    /** Searches for a parameter value in response body */
    private String findValue(String body, String param) {
        for (Pattern p : VALUE_PATTERNS) {
            Matcher m = p.matcher(body);
            while (m.find()) {
                if (m.groupCount() >= 2 && m.group(1).equals(param)) {
                    return m.group(2);
                }
            }
        }
        return null;
    }

    /** Logs messages only if debugging mode is enabled */
    private void log(String msg) {
        if (debug.isDebuggingModeEnabled()) {
            api.logging().logToOutput(msg);
        }
    }
}