public class Parameter {
    String name;
    private String value;
    private boolean enabled;
    private boolean autoUpdate;
    private String lastSeenValue;
    private int updateCount;
    private ParameterSource source;


    public enum ParameterSource {
        URL_QUERY("URL Query"),
        LOCATION_HEADER("Location Header"),
        //BODY_FORM("Body Form"),
        //BODY_JSON("Body JSON"),
        CUSTOM("Custom");

        private final String displayName;
        ParameterSource(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public Parameter(String name, String value, ParameterSource source) {
        this.name = name;
        this.value = value;
        this.source = source;
        this.enabled = true;
        this.autoUpdate = true;
        this.lastSeenValue = value;
        this.updateCount = 0;
    }

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getValue() { return value; }
    public void setValue(String value) {
        if (!this.value.equals(value)) {
            this.lastSeenValue = this.value;
            this.value = value;
            this.updateCount++;
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoUpdate() { return autoUpdate; }
    public void setAutoUpdate(boolean autoUpdate) { this.autoUpdate = autoUpdate; }

    public String getLastSeenValue() { return lastSeenValue; }
    public int getUpdateCount() { return updateCount; }

    public ParameterSource getSource() { return source; }
    public void setSource(ParameterSource source) { this.source = source; }

    @Override
    public String toString() {
        return name + "=" + value + " [" + source.getDisplayName() + "]";
    }
}