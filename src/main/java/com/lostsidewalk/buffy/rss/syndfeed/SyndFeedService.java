package com.lostsidewalk.buffy.rss.syndfeed;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.springframework.stereotype.Service;

import java.net.URL;

@Service
@SuppressWarnings("unused")
public class SyndFeedService {

    public SyndFeed fetch(String q) throws SyndFeedException {
        try {
            URL feedSource = new URL(q);
            SyndFeedInput input = new SyndFeedInput();
            return input.build(new XmlReader(feedSource));
        } catch (Exception e) {
            throw new SyndFeedException(e.getMessage());
        }
    }
}
