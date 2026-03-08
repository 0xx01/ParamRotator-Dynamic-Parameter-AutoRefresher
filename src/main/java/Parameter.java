/**
 * Represents an HTTP parameter that can be tracked, updated, and applied to requests.
 */
public class Parameter {

    /** Parameter name */
    private final String name;

    /** Current value of the parameter */
    private String value;

    /** Flag indicating whether the parameter is enabled */
    private boolean enabled;

    /** Flag indicating whether the parameter should auto-update from responses */
    private boolean autoUpdate;

    /** Last seen value before the most recent update */
    private String lastSeenValue;

    /** Count of how many times this parameter was updated */
    private int updateCount;

    /** Source type of the parameter */
    private final ParameterSource source;

    /** True if this parameter is a matrix parameter (;param=value) */
    private final boolean isMatrixParameter;

    /**
     * Enum representing the source of a parameter.
     */
    public enum ParameterSource {
        URL_QUERY("URL Query"),
        LOCATION_HEADER("Location Header"),
        MATRIX_PARAMETER("Matrix Parameter"),
        CUSTOM("Custom");

        private final String displayName;

        ParameterSource(String displayName) {
            this.displayName = displayName;
        }

        /**
         * @return Display-friendly name of the parameter source
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Constructs a Parameter instance.
     *
     * @param name   Parameter name
     * @param value  Initial value
     * @param source Source of the parameter
     */
    public Parameter(String name, String value, ParameterSource source) {
        this.name = name;
        this.value = value;
        this.source = source;
        this.enabled = true;
        this.autoUpdate = true;
        this.lastSeenValue = value;
        this.updateCount = 0;
        this.isMatrixParameter = (source == ParameterSource.MATRIX_PARAMETER);
    }

    /** @return Parameter name */
    public String getName() {
        return name;
    }

    /** @return Current value of the parameter */
    public String getValue() {
        return value;
    }

    /**
     * Updates the parameter's value.
     * Increments the update count and stores previous value if changed.
     *
     * @param value New value to set
     */
    public void setValue(String value) {
        if (!this.value.equals(value)) {
            this.lastSeenValue = this.value;
            this.value = value;
            this.updateCount++;
        }
    }

    /** @return true if the parameter is enabled */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled Set whether the parameter is enabled */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return true if auto-update is enabled for this parameter */
    public boolean isAutoUpdate() {
        return autoUpdate;
    }

    /** @param autoUpdate Set whether auto-update is enabled */
    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    /** @return Number of times this parameter was updated */
    public int getUpdateCount() {
        return updateCount;
    }

    /** @return Source type of the parameter */
    public ParameterSource getSource() {
        return source;
    }

    @Override
    public String toString() {
        return name + "=" + value + " [" + source.getDisplayName() + "]";
    }
}