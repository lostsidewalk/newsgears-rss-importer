package com.lostsidewalk.buffy.rss;

import com.lostsidewalk.buffy.importer.Importer.ImportResponseCallback;
import com.lostsidewalk.buffy.importer.Importer.ImportResult;
import com.lostsidewalk.buffy.post.StagingPost;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService.SyndFeedResponse;
import com.lostsidewalk.buffy.subscription.SubscriptionDefinition;
import com.lostsidewalk.buffy.subscription.SubscriptionMetrics;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import java.io.StringReader;
import java.util.Date;
import java.util.Queue;
import java.util.Set;

import static java.util.Collections.*;
import static org.apache.commons.collections4.CollectionUtils.size;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@SuppressWarnings("CallToDateToString")
@Slf4j
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ContextConfiguration(classes = RssImporter.class)
public class RssImporterTest_RSS1 {

    @MockBean
    RssImporterConfigProps configProps;

    @MockBean
    Queue<SubscriptionMetrics> subscriptionMetricsAggregator;

    @MockBean
    RssMockDataGenerator rssMockDataGenerator;

    @MockBean
    SyndFeedService syndFeedService;

    @Autowired
    RssImporter rssImporter;

    static final SubscriptionDefinition TEST_RSS_SUB = SubscriptionDefinition.from(668L, "me", "testQuery", "http://localhost/test.rss", "RSS", null, null);

    static final String TEST_RSS_RESPONSE =
            "<rss" +
            " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
            " xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"" +
            " xmlns:atom=\"http://www.w3.org/2005/Atom\"" +
            " xmlns:media=\"http://search.yahoo.com/mrss/\" version=\"2.0\">" +
            " <channel>" +
            "  <title>" +
            "   <![CDATA[ CNN.com - RSS Channel - HP Hero ]]>" +
            "  </title>" +
            "  <description>" +
            "   <![CDATA[ CNN.com delivers up-to-the-minute news and information on the latest top stories, weather, entertainment, politics and more. ]]>" +
            "  </description>" +
            "  <link>https://www.cnn.com/index.html</link>" +
            "  <image>" +
            "   <url>http://i2.cdn.turner.com/cnn/2015/images/09/24/cnn.digital.png</url>" +
            "   <title>CNN.com - RSS Channel - HP Hero</title>" +
            "   <link>https://www.cnn.com/index.html</link>" +
            "  </image>" +
            "  <generator>coredev-bumblebee</generator>" +
            "  <lastBuildDate>Tue, 29 Nov 2022 15:44:38 GMT</lastBuildDate>" +
            "  <pubDate>Tue, 29 Nov 2022 15:40:29 GMT</pubDate>" +
            "  <copyright>" +
            "   <![CDATA[ Copyright (c) 2022 Turner Broadcasting System, Inc. All Rights Reserved. ]]>" +
            "  </copyright>" +
            "  <language>" +
            "   <![CDATA[ en-US ]]>" +
            "  </language>" +
            "  <ttl>10</ttl>" +
            "  <item>" +
            "   <title>" +
            "    <![CDATA[ US Soccer's attempt to highlight the struggles of women protestors inside the Islamic Republic may have backfired ]]>" +
            "   </title>" +
            "   <description>" +
            "    <![CDATA[ • Iran threatened families of national soccer team, according to security source • US to play Iran at 2 p.m. ET in politically charged World Cup match ]]>" +
            "   </description>" +
            "   <link>https://www.cnn.com/2022/11/28/world/iran-us-soccer-world-cup-analysis-intl-spt/index.html</link>" +
            "   <guid isPermaLink=\"true\">https://www.cnn.com/2022/11/28/world/iran-us-soccer-world-cup-analysis-intl-spt/index.html</guid>" +
            "   <pubDate>Tue, 29 Nov 2022 13:18:37 GMT</pubDate>" +
            "   <media:group>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-super-169.jpg\" height=\"619\" width=\"1100\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-large-11.jpg\" height=\"300\" width=\"300\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-vertical-large-gallery.jpg\" height=\"552\" width=\"414\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-video-synd-2.jpg\" height=\"480\" width=\"640\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-live-video.jpg\" height=\"324\" width=\"576\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-t1-main.jpg\" height=\"250\" width=\"250\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-vertical-gallery.jpg\" height=\"360\" width=\"270\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-story-body.jpg\" height=\"169\" width=\"300\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-t1-main.jpg\" height=\"250\" width=\"250\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-assign.jpg\" height=\"186\" width=\"248\" type=\"image/jpeg\"/>" +
            "    <media:content medium=\"image\" url=\"https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-hp-video.jpg\" height=\"144\" width=\"256\" type=\"image/jpeg\"/>" +
            "   </media:group>" +
            "  </item>" +
            " </channel>" +
            "</rss>";

    @Test
    public void testRssImporter_performRssChannelImport() {
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed response = syndFeedInput.build(new StringReader(TEST_RSS_RESPONSE));
            SyndFeedResponse syndFeedResponse = SyndFeedResponse.from(response, 200, "OK");
            when(syndFeedService.fetch(
                    eq(TEST_RSS_SUB.getUrl()),
                    isNull(),
                    isNull(),
                    eq("Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of 1 users"),
                    eq(true))
                ).thenReturn(syndFeedResponse);
            // carry out test
            ImportResult importResult = rssImporter.performImport(TEST_RSS_SUB, new ImportResponseCallback() {
                @Override
                public ImportResult onSuccess(Set<StagingPost> set) {
                    assertNotNull(set);
                    assertEquals(1, set.size());
                    StagingPost stagingPost = set.iterator().next();
                    assertEquals("RssAtom", stagingPost.getImporterId());
                    assertEquals(668L, stagingPost.getQueueId());
                    assertEquals("http://localhost/test.rss", stagingPost.getImporterDesc());
                    assertNotNull(stagingPost.getPostTitle());
                    assertNull(stagingPost.getPostTitle().getType());
                    assertEquals("     US Soccer's attempt to highlight the struggles of women protestors inside the Islamic Republic may have backfired    ", stagingPost.getPostTitle().getValue());
                    assertNotNull(stagingPost.getPostDesc());
                    assertEquals("text/html", stagingPost.getPostDesc().getType());
                    assertEquals("     • Iran threatened families of national soccer team, according to security source • US to play Iran at 2 p.m. ET in politically charged World Cup match    ", stagingPost.getPostDesc().getValue());
                    assertEquals("https://www.cnn.com/2022/11/28/world/iran-us-soccer-world-cup-analysis-intl-spt/index.html", stagingPost.getPostUrl());
                    assertEquals("https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-super-169.jpg", stagingPost.getPostImgUrl());
                    assertEquals("CBAB8C3E106008CFCC17D41141FBA8B4", stagingPost.getPostImgTransportIdent());
                    assertNotNull(stagingPost.getImportTimestamp());
                    assertEquals("C25077AD17AEB0395507DC6F3F0E3C1B", stagingPost.getPostHash());
                    assertEquals("me", stagingPost.getUsername());
                    assertNotNull(stagingPost.getPublishTimestamp());
                    assertEquals("Tue Nov 29 07:18:37 CST 2022", stagingPost.getPublishTimestamp().toString());
                    assertNull(stagingPost.getExpirationTimestamp());
                    assertFalse(stagingPost.isPublished());

                    return ImportResult.from(emptySet(), singletonList(SubscriptionMetrics.from(1L, new Date(), "A", 1)));
                }

                @Override
                public ImportResult onFailure(Throwable throwable) {
                    fail(throwable.getMessage());
                    return ImportResult.from(emptySet(), emptyList());
                }
            });
            assertNotNull(importResult);
            assertEquals(1, size(importResult.getSubscriptionMetrics()));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testRssImporter_doRssChannelImport() {
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed response = syndFeedInput.build(new StringReader(TEST_RSS_RESPONSE));
            SyndFeedResponse syndFeedResponse = SyndFeedResponse.from(response, 200, "OK");
            when(syndFeedService.fetch(
                    eq(TEST_RSS_SUB.getUrl()),
                    isNull(),
                    isNull(),
                    eq("Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of 1 users"),
                    eq(true))
                ).thenReturn(syndFeedResponse);
            rssImporter.doImport(singletonList(TEST_RSS_SUB), emptyMap());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "RssImporterTest_RSS1{" +
                "configProps=" + configProps +
                ", subscriptionMetricsAggregator=" + subscriptionMetricsAggregator +
                ", rssMockDataGenerator=" + rssMockDataGenerator +
                ", syndFeedService=" + syndFeedService +
                ", rssImporter=" + rssImporter +
                '}';
    }
}
