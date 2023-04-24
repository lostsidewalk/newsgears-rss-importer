package com.lostsidewalk.buffy.rss;

import com.lostsidewalk.buffy.importer.Importer.ImportResponseCallback;
import com.lostsidewalk.buffy.importer.Importer.ImportResult;
import com.lostsidewalk.buffy.post.StagingPost;
import com.lostsidewalk.buffy.query.QueryDefinition;
import com.lostsidewalk.buffy.query.QueryMetrics;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService.SyndFeedResponse;
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
import java.util.Date;
import java.util.Queue;
import java.util.Set;

import static java.util.Collections.*;
import static org.apache.commons.collections4.CollectionUtils.size;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ContextConfiguration(classes = {RssImporter.class})
public class RssImporterTest_RSS2 {

    @MockBean
    RssImporterConfigProps configProps;

    @MockBean
    Queue<StagingPost> successAggregator;

    @MockBean
    Queue<Throwable> errorAggregator;

    @MockBean
    Queue<QueryMetrics> queryMetricsAggregator;

    @MockBean
    RssMockDataGenerator rssMockDataGenerator;

    @MockBean
    SyndFeedService syndFeedService;

    @Autowired
    RssImporter rssImporter;

    static final QueryDefinition TEST_RSS_QUERY =  QueryDefinition.from(669L, "me", "testQuery", "http://localhost/test.rss", "RSS", null);

    static final String TEST_RSS_RESPONSE =
            "<rss" +
            " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
            " xmlns:media=\"http://search.yahoo.com/mrss/\"" +
            " xmlns:atom=\"http://www.w3.org/2005/Atom\"" +
            " xmlns:nyt=\"http://www.nytimes.com/namespaces/rss/2.0\" version=\"2.0\">" +
            " <channel>" +
            "  <title>NYT > World News</title>" +
            "  <link>https://www.nytimes.com/section/world</link>" +
            "  <atom:link href=\"https://rss.nytimes.com/services/xml/rss/nyt/World.xml\" rel=\"self\" type=\"application/rss+xml\"/>" +
            "  <description/>" +
            "  <language>en-us</language>" +
            "  <copyright>Copyright 2022 The New York Times Company</copyright>" +
            "  <lastBuildDate>Tue, 29 Nov 2022 16:13:29 +0000</lastBuildDate>" +
            "  <pubDate>Tue, 29 Nov 2022 16:08:05 +0000</pubDate>" +
            "  <image>" +
            "   <title>NYT > World News</title>" +
            "   <url>https://static01.nyt.com/images/misc/NYT_logo_rss_250x40.png</url>" +
            "   <link>https://www.nytimes.com/section/world</link>" +
            "  </image>" +
            "  <item>" +
            "   <title>China Uses Surveillance, Intimidation to Snuff Out Covid Protests</title>" +
            "   <link>https://www.nytimes.com/2022/11/29/world/asia/china-protest-covid-security.html</link>" +
            "   <guid isPermaLink=\"true\">https://www.nytimes.com/2022/11/29/world/asia/china-protest-covid-security.html</guid>" +
            "   <atom:link href=\"https://www.nytimes.com/2022/11/29/world/asia/china-protest-covid-security.html\" rel=\"standout\"/>" +
            "   <description type=\"text\">Communist Party officials are using decades-old tactics, along with some new ones, to quash the most widespread protests in decades. But Xi Jinping is silent.</description>" +
            "   <dc:creator>Chris Buckley</dc:creator>" +
            "   <pubDate>Tue, 29 Nov 2022 15:29:27 +0000</pubDate>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/des\">Politics and Government</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/des\">Demonstrations, Protests and Riots</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/des\">Censorship</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/des\">Freedom of the Press</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/des\">Social Media</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_org\">Communist Party of China</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_org\">Tsinghua University</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_per\">Xi Jinping</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_geo\">China</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_geo\">Guangzhou (China)</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_geo\">Hangzhou (China)</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_geo\">Shanghai (China)</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_geo\">Tiananmen Square (Beijing)</category>" +
            "   <category domain=\"http://www.nytimes.com/namespaces/keywords/nyt_geo\">Urumqi (China)</category>" +
            "   <media:content height=\"151\" medium=\"image\" url=\"https://static01.nyt.com/images/2022/11/29/world/29CHINA-SECURITY-01/29CHINA-SECURITY-01-moth.jpg\" width=\"151\"/>" +
            "   <media:credit>Kevin Frayer/Getty Images</media:credit>" +
            "   <media:description>Protesters holding up a white piece of paper against censorship as they march during a protest against Chinas strict zero COVID measures in Beijing, China, on Sunday.</media:description>" +
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
            when(this.syndFeedService.fetch(
                    eq(TEST_RSS_QUERY.getQueryText()),
                    isNull(),
                    isNull(),
                    eq("Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of 1 users"),
                    eq(true))
                ).thenReturn(syndFeedResponse);
            // carry out test
            ImportResult importResult = rssImporter.performImport(TEST_RSS_QUERY, new ImportResponseCallback() {
                @Override
                public ImportResult onSuccess(Set<StagingPost> set) {
                    assertNotNull(set);
                    assertEquals(1, set.size());
                    StagingPost s = set.iterator().next();
                    assertEquals("RssAtom", s.getImporterId());
                    assertEquals(669L, s.getFeedId());
                    assertEquals("http://localhost/test.rss", s.getImporterDesc());
                    assertNotNull(s.getPostTitle());
                    assertNull(s.getPostTitle().getType());
                    assertEquals("China Uses Surveillance, Intimidation to Snuff Out Covid Protests", s.getPostTitle().getValue());
                    assertNotNull(s.getPostDesc());
                    assertEquals("text", s.getPostDesc().getType());
                    assertEquals("Communist Party officials are using decades-old tactics, along with some new ones, to quash the most widespread protests in decades. But Xi Jinping is silent.", s.getPostDesc().getValue());
                    assertEquals("https://www.nytimes.com/2022/11/29/world/asia/china-protest-covid-security.html", s.getPostUrl());
                    assertEquals("https://static01.nyt.com/images/2022/11/29/world/29CHINA-SECURITY-01/29CHINA-SECURITY-01-moth.jpg", s.getPostImgUrl());
                    assertEquals("394DF6AFE22C93D0FE40E0C7B011D889", s.getPostImgTransportIdent());
                    assertNotNull(s.getImportTimestamp());
                    assertEquals("DB5BDAE5DECF74EDCDE597418793475F", s.getPostHash());
                    assertEquals("me", s.getUsername());
                    assertNotNull(s.getPublishTimestamp());
                    assertEquals("Tue Nov 29 09:29:27 CST 2022", s.getPublishTimestamp().toString());
                    assertNull(s.getExpirationTimestamp());
                    assertFalse(s.isPublished());

                    return ImportResult.from(emptySet(), singletonList(QueryMetrics.from(1L, new Date(), 1)));
                }

                @Override
                public ImportResult onFailure(Throwable throwable) {
                    fail(throwable.getMessage());
                    return ImportResult.from(emptySet(), emptyList());
                }
            });
            assertNotNull(importResult);
            assertEquals(1, size(importResult.getQueryMetrics()));
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
            when(this.syndFeedService.fetch(
                    eq(TEST_RSS_QUERY.getQueryText()),
                    isNull(),
                    isNull(),
                    eq("Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of 1 users"),
                    eq(true))
                ).thenReturn(syndFeedResponse);
            rssImporter.doImport(singletonList(TEST_RSS_QUERY), emptyMap());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
