package com.lostsidewalk.buffy.rss.syndfeed;

import com.lostsidewalk.buffy.rss.RssImporterConfigProps;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
@SuppressWarnings("unused")
public class SyndFeedService {

    @Autowired
    RssImporterConfigProps configProps;

    public SyndFeed fetch(String url, int subscriberCt, String username, String password) throws SyndFeedException {
        try {
            URL feedUrl = new URL(url);
            URLConnection feedConnection = feedUrl.openConnection();
            if (username != null && password != null) {
                feedConnection.setRequestProperty("Authorization",
                        "Basic " + new String(Base64.getEncoder().encode((username + ":" + password).getBytes(UTF_8)))
                );
            }
            // TODO: make this property-configurable
            String userAgentTemplate = "Lost Sidewalk FeedGears RSS Aggregator v.0.3, fetching on behalf of %d subscriber(s)";
            feedConnection.setRequestProperty("User-Agent", String.format(userAgentTemplate, subscriberCt));
            SyndFeedInput input = new SyndFeedInput();
            byte[] allBytes = feedConnection.getInputStream().readAllBytes();
            ByteArrayInputStream bais = new ByteArrayInputStream(allBytes);
            return input.build(new XmlReader(bais));
        } catch (Exception e) {
            throw new SyndFeedException(e.getMessage());
        }
    }
}
