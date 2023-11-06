package com.lostsidewalk.buffy.rss;

import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService.SyndFeedResponse;
import com.lostsidewalk.buffy.subscription.SubscriptionDefinition;
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

    static SyndFeedResponse buildMockResponse(SubscriptionDefinition q) {
        SyndFeed mockResponse = new SyndFeedImpl();
        mockResponse.setEntries(buildMockArticles(q, mockResponse));

        return SyndFeedResponse.from(mockResponse, 200, "OK");
    }

    @SuppressWarnings("StringConcatenationMissingWhitespace")
    private static List<SyndEntry> buildMockArticles(SubscriptionDefinition q, SyndFeed parentFeed) {
        Long queueId = q.getQueueId();
        SyndEntry mockArticle = new SyndEntryImpl();
        mockArticle.setAuthor("test-author" + queueId);
        mockArticle.setDescription(buildSyndContent("test-description" + queueId));
        mockArticle.setSource(parentFeed);
        mockArticle.setPublishedDate(new Date());
        mockArticle.setUri("test-url" + queueId);
        mockArticle.setModules(buildMediaModule());
        mockArticle.setTitle("test-title" + queueId);

        return singletonList(mockArticle);
    }

    private static SyndContent buildSyndContent(String str) {
        SyndContent syndContent = new SyndContentImpl();
        syndContent.setValue(str);
        return syndContent;
    }

    private static List<com.rometools.rome.feed.module.Module> buildMediaModule() {
        MediaEntryModuleImpl mm = new MediaEntryModuleImpl();
        mm.setMetadata(buildMetadata());

        return singletonList(mm);
    }

    private static Metadata buildMetadata() {
        com.rometools.modules.mediarss.types.Metadata metadata = new Metadata();
        metadata.setThumbnail(new Thumbnail[] { new Thumbnail(URI.create("test-image")) });
        return metadata;
    }
}
