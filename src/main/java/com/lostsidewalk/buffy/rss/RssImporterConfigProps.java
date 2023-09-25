package com.lostsidewalk.buffy.rss;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for RSS Importer properties.
 * This class is used to configure properties related to RSS importing.
 */
@Configuration
@ConfigurationProperties(prefix = "rss.importer")
public class RssImporterConfigProps {

    private boolean disabled; // false

    private boolean importMockData;

    /**
     * Get the value of the 'disabled' property.
     *
     * @return True if RSS importing is disabled, otherwise false.
     */
    public boolean getDisabled() {
        return disabled;
    }

    /**
     * Set the 'disabled' property to enable or disable RSS importing.
     *
     * @param disabled True to disable RSS importing, false to enable it.
     */
    @SuppressWarnings("unused")
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /**
     * Get the value of the 'importMockData' property.
     *
     * @return True if importing mock data is enabled, otherwise false.
     */
    public boolean getImportMockData() {
        return importMockData;
    }

    /**
     * Set the 'importMockData' property to enable or disable importing of mock data.
     *
     * @param importMockData True to enable importing of mock data, false to disable it.
     */
    @SuppressWarnings("unused")
    public void setImportMockData(boolean importMockData) {
        this.importMockData = importMockData;
    }
}
