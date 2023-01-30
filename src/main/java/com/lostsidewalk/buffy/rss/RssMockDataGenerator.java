package com.lostsidewalk.buffy.rss;

import com.lostsidewalk.buffy.query.QueryDefinition;
import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.types.Metadata;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.synd.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Date;
import java.util.List;

import static java.util.Collections.singletonList;

@Slf4j
@Component
@SuppressWarnings("unused")
class RssMockDataGenerator {

    SyndFeed buildMockResponse(QueryDefinition q) {
        SyndFeedImpl mockResponse = new SyndFeedImpl();
        mockResponse.setEntries(buildMockArticle(q, mockResponse));

        return mockResponse;
    }

    private List<SyndEntry> buildMockArticle(QueryDefinition q, SyndFeed parentFeed) {
        Long feedId = q.getFeedId();
        SyndEntryImpl mockArticle = new SyndEntryImpl();
        mockArticle.setAuthor("test-author" + feedId);
        mockArticle.setDescription(buildSyndContent("test-description" + feedId));
        mockArticle.setSource(parentFeed);
        mockArticle.setPublishedDate(new Date());
        mockArticle.setUri("test-url" + feedId);
        mockArticle.setModules(buildMediaModule());
        mockArticle.setTitle("test-title" + feedId);

        return singletonList(mockArticle);
    }

    private static SyndContent buildSyndContent(String str) {
        SyndContentImpl s = new SyndContentImpl();
        s.setValue(str);
        return s;
    }

    private List<com.rometools.rome.feed.module.Module> buildMediaModule() {
        MediaEntryModuleImpl mm = new MediaEntryModuleImpl();
        mm.setMetadata(buildMetadata());

        return singletonList(mm);
    }

    private Metadata buildMetadata() {
        com.rometools.modules.mediarss.types.Metadata m = new Metadata();
        m.setThumbnail(new Thumbnail[] { new Thumbnail(URI.create("test-image")) });
        return m;
    }
}
