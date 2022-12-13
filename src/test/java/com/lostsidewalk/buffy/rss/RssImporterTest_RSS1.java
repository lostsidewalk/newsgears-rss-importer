package com.lostsidewalk.buffy.rss;

import com.lostsidewalk.buffy.Importer;
import com.lostsidewalk.buffy.Importer.ImporterMetrics;
import com.lostsidewalk.buffy.post.StagingPost;
import com.lostsidewalk.buffy.query.QueryDefinition;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import java.io.StringReader;
import java.util.Queue;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ContextConfiguration(classes = {RssImporter.class})
public class RssImporterTest_RSS1 {

    @MockBean
    RssImporterConfigProps configProps;

    @MockBean
    Queue<StagingPost> successAggregator;

    @MockBean
    Queue<Throwable> errorAggregator;

    @MockBean
    RssMockDataGenerator rssMockDataGenerator;

    @MockBean
    SyndFeedService syndFeedService;

    @Autowired
    RssImporter rssImporter;

    static final QueryDefinition TEST_RSS_QUERY = QueryDefinition.from("testFeedIdent", "me", "http://localhost/test.rss", "RSS", null);

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
            SyndFeed syndFeedResponse = syndFeedInput.build(new StringReader(TEST_RSS_RESPONSE));
            when(this.syndFeedService.fetch(TEST_RSS_QUERY.getQueryText())).thenReturn(syndFeedResponse);
            // carry out test
            ImporterMetrics importerMetrics = rssImporter.performImport(TEST_RSS_QUERY, new Importer.ImportResponseCallback() {
                @Override
                public void onSuccess(Set<StagingPost> set) {
                    assertNotNull(set);
                    assertEquals(1, set.size());
                    StagingPost s = set.iterator().next();
                    assertEquals("RssAtom", s.getImporterId());
                    assertEquals("testFeedIdent", s.getFeedIdent());
                    assertEquals("[query=http://localhost/test.rss]", s.getImporterDesc());
                    assertEquals("US Soccer's attempt to highlight the struggles of women protestors inside the Islamic Republic may have backfired", s.getPostTitle());
                    assertEquals("• Iran threatened families of national soccer team, according to security source • US to play Iran at 2 p.m. ET in politically charged World Cup match", s.getPostDesc());
                    assertEquals("https://www.cnn.com/2022/11/28/world/iran-us-soccer-world-cup-analysis-intl-spt/index.html", s.getPostUrl());
                    assertEquals("https://cdn.cnn.com/cnnnext/dam/assets/221127112511-iran-flag-world-cup-1125-super-169.jpg", s.getPostImgUrl());
                    assertEquals("CBAB8C3E106008CFCC17D41141FBA8B4", s.getPostImgTransportIdent());
                    assertNotNull(s.getImportTimestamp());
                    assertEquals("A7A11D0BF5290773BE48BFAA12AF8790", s.getPostHash());
                    assertEquals("me", s.getUsername());
                    assertNotNull(s.getPublishTimestamp());
                    assertEquals("Tue Nov 29 07:18:37 CST 2022", s.getPublishTimestamp().toString());
                    assertNull(s.getExpirationTimestamp());
                    assertFalse(s.isPublished());
                }

                @Override
                public void onFailure(Throwable throwable) {
                    fail(throwable.getMessage());
                }
            });
            assertNotNull(importerMetrics);
            assertEquals(1, importerMetrics.getSuccessCt());
            assertEquals(0, importerMetrics.getErrorCt());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testRssImporter_doRssChannelImport() {
        rssImporter.doImport(singletonList(TEST_RSS_QUERY));
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed syndFeedResponse = syndFeedInput.build(new StringReader(TEST_RSS_RESPONSE));
            when(this.syndFeedService.fetch(TEST_RSS_QUERY.getQueryText())).thenReturn(syndFeedResponse);
            rssImporter.doImport(singletonList(TEST_RSS_QUERY));
            verify(this.successAggregator, times(1)).offer(any());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
