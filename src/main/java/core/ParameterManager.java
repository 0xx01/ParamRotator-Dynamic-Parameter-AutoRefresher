package core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.core.ByteArray;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
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

    private final List<Parameter> parameters =
            new CopyOnWriteArrayList<>();

    private boolean autoUpdateEnabled = true;

    /** Regex patterns used to extract values from responses */
    private static final Pattern[] VALUE_PATTERNS = {

            Pattern.compile(
                    "([a-zA-Z0-9_]+)=([^&\\s\"'<>&?;#/]+)"
            ),

            Pattern.compile(
                    "name=[\"']([^\"']+)[\"']\\s+value=[\"']([^\"']*)[\"']",
                    Pattern.CASE_INSENSITIVE
            ),

            Pattern.compile(
                    "\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\""
            )
    };

    /** Pattern for extracting matrix parameters from path */
    private static final Pattern PATH_PATTERN =
            Pattern.compile(";([^=]+)=([^;?/&\\s]+)");

    public ParameterManager(
            MontoyaApi api,
            DebuggingMode debug) {

        this.api = api;
        this.debug = debug;
    }

    /**
     * Extracts parameters from a request URL.
     */
    public void extractFromRequest(HttpRequest request) {

        parameters.clear();

        if (request == null) {
            return;
        }

        String url = request.url();

        extractPathParams(url);
        extractQueryParams(url);

        log(
                "Extracted parameters: "
                        + parameters.size()
        );
    }

    /**
     * Updates parameters using response content.
     */
    public Map<String, String> updateFromResponse(
            HttpRequestResponse response) {

        if (!autoUpdateEnabled
                || response == null
                || response.response() == null) {

            return Collections.emptyMap();
        }

        String body =
                response.response().toString();

        Map<String, String> updates =
                new LinkedHashMap<>();

        for (Parameter parameter : parameters) {

            if (!parameter.isEnabled()
                    || !parameter.isAutoUpdate()) {

                continue;
            }

            String newValue =
                    findValue(
                            body,
                            parameter.getName()
                    );

            if (newValue == null
                    || newValue.equals(
                    parameter.getValue())) {

                continue;
            }

            /*
             * Never allow CR/LF characters from a server response
             * to become part of an HTTP request line.
             */
            if (containsHttpLineBreak(newValue)) {

                log(
                        "Rejected unsafe value for parameter '"
                                + parameter.getName()
                                + "' because it contains CR/LF"
                );

                continue;
            }

            updates.put(
                    parameter.getName(),
                    newValue
            );

            parameter.setValue(newValue);

            log(
                    parameter.getName()
                            + " updated"
            );
        }

        /*
         * Show a non-blocking notification.
         *
         * We intentionally do not ask the user for confirmation
         * because automatic parameter synchronization is the
         * primary workflow of ParamRotator.
         */
        if (!updates.isEmpty()) {

            SwingUtilities.invokeLater(
                    () -> showUpdateNotification(updates)
            );
        }

        return updates;
    }

    /**
     * Extracts parameters from a Location header URL.
     */
    public int extractFromLocationHeader(
            String locationUrl) {

        if (locationUrl == null
                || locationUrl.isEmpty()) {

            return 0;
        }

        int before =
                parameters.size();

        log(
                "Extracting from Location header: "
                        + locationUrl
        );

        extractPathParams(locationUrl);
        extractQueryParams(locationUrl);

        int added =
                parameters.size() - before;

        log(
                "Added "
                        + added
                        + " parameters from Location header"
        );

        return added;
    }

    /**
     * Applies stored parameters to a request.
     */
    public HttpRequest applyToRequest(
            HttpRequest request) {

        if (request == null) {
            return null;
        }

        String normalized =
                request.toString()
                        .replace("\r\n", "\n")
                        .replace("\r", "\n");

        String[] lines =
                normalized.split(
                        "\n",
                        -1
                );

        if (lines.length == 0) {
            return request;
        }

        lines[0] =
                rebuildRequestLine(lines[0]);

        return HttpRequest.httpRequest(
                request.httpService(),
                ByteArray.byteArray(
                        String.join(
                                "\r\n",
                                lines
                        )
                )
        );
    }

    /**
     * Adds a custom parameter.
     */
    public void addCustomParameter(
            String name,
            String value) {

        if (parameterExists(name)) {

            showMessage(
                    "Parameter already exists",
                    "ParamRotator",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        parameters.add(
                new Parameter(
                        name,
                        value,
                        Parameter.ParameterSource.CUSTOM
                )
        );
    }

    /**
     * Removes a parameter by name.
     */
    public void removeParameter(String name) {

        parameters.removeIf(
                p -> p.getName().equals(name)
        );
    }

    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }

    public List<Parameter> getAllParameters() {

        return Collections.unmodifiableList(
                parameters
        );
    }

    /**
     * Updates an existing parameter value.
     */
    public void updateParameter(
            String name,
            String newValue) {

        parameters.stream()
                .filter(
                        p -> p.getName().equals(name)
                )
                .findFirst()
                .ifPresent(
                        p -> p.setValue(newValue)
                );
    }

    // ============================================================
    // Extraction Helpers
    // ============================================================

    private void extractPathParams(String url) {

        if (url == null) {
            return;
        }

        String path =
                url.split(
                        "\\?",
                        2
                )[0];

        Matcher matcher =
                PATH_PATTERN.matcher(path);

        while (matcher.find()) {

            addParam(
                    matcher.group(1),
                    matcher.group(2),
                    Parameter.ParameterSource.MATRIX_PARAMETER
            );
        }
    }

    private void extractQueryParams(String url) {

        if (url == null
                || !url.contains("?")) {

            return;
        }

        String query =
                url.split(
                        "\\?",
                        2
                )[1];

        for (String pair :
                query.split("&")) {

            String[] kv =
                    pair.split(
                            "=",
                            2
                    );

            String paramName =
                    kv[0].trim();

            if (paramName.isEmpty()) {
                continue;
            }

            String paramValue =
                    kv.length > 1
                            ? kv[1].trim()
                            : "";

            addParam(
                    paramName,
                    paramValue,
                    Parameter.ParameterSource.URL_QUERY
            );
        }
    }

    // ============================================================
    // Request Rebuilding
    // ============================================================

    private String rebuildRequestLine(
            String firstLine) {

        if (firstLine == null
                || firstLine.isEmpty()) {

            return firstLine;
        }

        String[] parts =
                firstLine.split(
                        " ",
                        3
                );

        if (parts.length < 2) {
            return firstLine;
        }

        String method =
                parts[0];

        String path =
                parts[1];

        String version =
                parts.length == 3
                        ? parts[2]
                        : "";

        /*
         * Remove existing matrix/query parameters.
         * ParamRotator rebuilds them from the current
         * parameter store.
         */
        String basePath =
                path.split(
                        "[?;]",
                        2
                )[0];

        String withMatrix =
                appendParams(
                        basePath,
                        Parameter.ParameterSource.MATRIX_PARAMETER,
                        ";"
                );

        String withQuery =
                appendParams(
                        withMatrix,
                        Parameter.ParameterSource.URL_QUERY,
                        "&"
                );

        if (version.isEmpty()) {
            return method
                    + " "
                    + withQuery;
        }

        return method
                + " "
                + withQuery
                + " "
                + version;
    }

    /**
     * Appends parameters to the request path.
     *
     * Parameter values are encoded while preserving
     * already encoded %XX sequences.
     */
    private String appendParams(
            String base,
            Parameter.ParameterSource source,
            String separator) {

        List<Parameter> list =
                parameters.stream()
                        .filter(
                                p -> p.isEnabled()
                                        && p.getSource() == source
                        )
                        .toList();

        if (list.isEmpty()) {
            return base;
        }

        StringBuilder result =
                new StringBuilder(base);

        if (source
                == Parameter.ParameterSource.URL_QUERY) {

            result.append("?");
        }

        boolean first = true;

        for (Parameter parameter : list) {

            if (!first) {
                result.append(separator);
            }

            if (source
                    == Parameter.ParameterSource.MATRIX_PARAMETER) {

                result.append(";");
            }

            result.append(
                    parameter.getName()
            );

            result.append("=");

            result.append(
                    encodeParameterValue(
                            parameter.getValue()
                    )
            );

            first = false;
        }

        return result.toString();
    }

    /**
     * Encodes a parameter value while preserving already
     * URL-encoded %XX sequences.
     *
     * Examples:
     *
     * abc:def
     *     -> abc%3Adef
     *
     * abc%3Adef
     *     -> abc%3Adef
     *
     * abc%253Adef
     *     -> abc%253Adef
     *
     * This prevents ParamRotator from performing
     * double URL encoding.
     */
    private String encodeParameterValue(
            String value) {

        if (value == null
                || value.isEmpty()) {

            return "";
        }

        StringBuilder encoded =
                new StringBuilder();

        for (int i = 0;
             i < value.length();) {

            char current =
                    value.charAt(i);

            /*
             * Preserve existing percent-encoded bytes.
             *
             * %3A remains %3A
             * instead of becoming %253A.
             */
            if (current == '%'
                    && i + 2 < value.length()
                    && isHexDigit(
                    value.charAt(i + 1)
            )
                    && isHexDigit(
                    value.charAt(i + 2)
            )) {

                encoded.append('%');

                encoded.append(
                        Character.toUpperCase(
                                value.charAt(i + 1)
                        )
                );

                encoded.append(
                        Character.toUpperCase(
                                value.charAt(i + 2)
                        )
                );

                i += 3;

                continue;
            }

            /*
             * RFC 3986 unreserved characters can remain
             * unchanged.
             */
            if (isUnreserved(current)) {

                encoded.append(current);

                i++;

                continue;
            }

            /*
             * Encode the current Unicode code point as
             * UTF-8 bytes.
             */
            int codePoint =
                    value.codePointAt(i);

            String character =
                    new String(
                            Character.toChars(
                                    codePoint
                            )
                    );

            byte[] bytes =
                    character.getBytes(
                            StandardCharsets.UTF_8
                    );

            for (byte b : bytes) {

                encoded.append('%');

                encoded.append(
                        String.format(
                                "%02X",
                                b & 0xFF
                        )
                );
            }

            i +=
                    Character.charCount(
                            codePoint
                    );
        }

        return encoded.toString();
    }

    private boolean isUnreserved(
            char c) {

        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '-'
                || c == '.'
                || c == '_'
                || c == '~';
    }

    private boolean isHexDigit(
            char c) {

        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    // ============================================================
    // Parameter Management
    // ============================================================

    private void addParam(
            String name,
            String value,
            Parameter.ParameterSource source) {

        if (!parameters.stream().anyMatch(
                p -> p.getName().equals(name)
                        && p.getSource() == source)) {

            parameters.add(
                    new Parameter(
                            name,
                            value,
                            source
                    )
            );
        }
    }

    private boolean parameterExists(
            String name) {

        return parameters.stream().anyMatch(
                p -> p.getName().equals(name)
        );
    }

    // ============================================================
    // Response Parsing
    // ============================================================

    private String findValue(
            String body,
            String param) {

        if (body == null
                || param == null) {

            return null;
        }

        for (Pattern pattern :
                VALUE_PATTERNS) {

            Matcher matcher =
                    pattern.matcher(body);

            while (matcher.find()) {

                if (matcher.groupCount() >= 2
                        && matcher.group(1)
                        .equals(param)) {

                    return matcher.group(2);
                }
            }
        }

        return null;
    }

    private boolean containsHttpLineBreak(
            String value) {

        return value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;
    }

    // ============================================================
    // Notifications
    // ============================================================

    /**
     * Displays a non-blocking notification showing only
     * the names of the parameters that were updated.
     *
     */
    private void showUpdateNotification(
            Map<String, String> updates) {

        if (updates == null || updates.isEmpty()) {
            return;
        }

        Runnable showNotification = () -> {

            Window owner =
                    api.userInterface()
                            .swingUtils()
                            .suiteFrame();

            final JDialog dialog =
                    new JDialog(
                            owner instanceof Frame
                                    ? (Frame) owner
                                    : null,
                            "ParamRotator",
                            false
                    );

            dialog.setDefaultCloseOperation(
                    WindowConstants.DISPOSE_ON_CLOSE
            );

            JPanel panel =
                    new JPanel(
                            new BorderLayout(
                                    8,
                                    8
                            )
                    );

            panel.setBorder(
                    BorderFactory.createEmptyBorder(
                            10,
                            14,
                            10,
                            14
                    )
            );

            JLabel title =
                    new JLabel("Parameters automatically updated from server response");

            title.setFont(
                    title.getFont().deriveFont(
                            Font.BOLD
                    )
            );

            panel.add(
                    title,
                    BorderLayout.NORTH
            );

            /*
             * Each parameter is displayed
             * on its own line.
             */
            JPanel parameterPanel =
                    new JPanel();

            parameterPanel.setLayout(
                    new BoxLayout(
                            parameterPanel,
                            BoxLayout.Y_AXIS
                    )
            );

            parameterPanel.setOpaque(false);

            for (String name : updates.keySet()) {

                JLabel parameterLabel =
                        new JLabel(
                                "• " + name
                        );

                parameterLabel.setAlignmentX(
                        Component.LEFT_ALIGNMENT
                );

                parameterPanel.add(
                        parameterLabel
                );
            }

            panel.add(
                    parameterPanel,
                    BorderLayout.CENTER
            );

            dialog.setContentPane(panel);

            dialog.pack();

            dialog.setResizable(false);

            /*
             * Position near the top-right of Burp.
             */
            try {

                Point location =
                        owner.getLocationOnScreen();

                int x =
                        location.x
                                + owner.getWidth()
                                - dialog.getWidth()
                                - 25;

                int y =
                        location.y
                                + 80;

                dialog.setLocation(
                        Math.max(x, 0),
                        Math.max(y, 0)
                );

            } catch (IllegalComponentStateException e) {

                dialog.setLocationRelativeTo(owner);
            }

            dialog.setVisible(true);

            /*
             * Close automatically after 2.5 seconds.
             */
            Timer timer =
                    new Timer(
                            2500,
                            e -> {

                                if (dialog.isDisplayable()) {
                                    dialog.dispose();
                                }
                            }
                    );

            timer.setRepeats(false);
            timer.start();
        };

        if (SwingUtilities.isEventDispatchThread()) {

            showNotification.run();

        } else {

            SwingUtilities.invokeLater(
                    showNotification
            );
        }
    }


    /**
     * Generic message dialog used only for user-initiated
     * actions such as duplicate custom parameters.
     */
    private void showMessage(
            String message,
            String title,
            int messageType) {

        Runnable action =
                () -> JOptionPane.showMessageDialog(
                        api.userInterface()
                                .swingUtils()
                                .suiteFrame(),
                        message,
                        title,
                        messageType
                );

        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    // ============================================================
    // Debugging
    // ============================================================

    private void log(String msg) {

        if (debug.isDebuggingModeEnabled()) {

            api.logging().logToOutput(msg);
        }
    }
}