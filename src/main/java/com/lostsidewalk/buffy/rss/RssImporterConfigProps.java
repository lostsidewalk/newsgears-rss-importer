package com.lostsidewalk.buffy.rss;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "rss.importer")
public class RssImporterConfigProps {

    private boolean disabled; // false

    private boolean importMockData;

    public boolean getDisabled() {
        return disabled;
    }

    @SuppressWarnings("unused")
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean getImportMockData() {
        return importMockData;
    }

    @SuppressWarnings("unused")
    public void setImportMockData(boolean importMockData) {
        this.importMockData = importMockData;
    }
}
