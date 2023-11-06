package com.lostsidewalk.buffy.rss;

import com.lostsidewalk.buffy.importer.Importer.ImportResponseCallback;
import com.lostsidewalk.buffy.importer.Importer.ImportResult;
import com.lostsidewalk.buffy.post.PostUrl;
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
import java.util.List;
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
public class RssImporterTest_ATOM2 {

    @MockBean
    RssImporterConfigProps configProps;

    @MockBean
    Queue<StagingPost> successAggregator;

    @MockBean
    Queue<Throwable> errorAggregator;

    @MockBean
    Queue<SubscriptionMetrics> subscriptionMetricsAggregator;

    @MockBean
    RssMockDataGenerator rssMockDataGenerator;

    @MockBean
    SyndFeedService syndFeedService;

    @Autowired
    RssImporter rssImporter;

    static final SubscriptionDefinition TEST_ATOM_SUB = SubscriptionDefinition.from(667L, "me", "testQuery", "http://localhost/test.atom", "ATOM", null, null);

    static final String TEST_ATOM_RESPONSE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<feed" +
            " xmlns=\"http://www.w3.org/2005/Atom\"" +
            " xmlns:media=\"http://search.yahoo.com/mrss/\">" +
            " <category term=\"milf\" label=\"r/milf\"/>" +
            " <updated>2022-11-29T15:42:22+00:00</updated>" +
            " <icon>https://www.redditstatic.com/icon.png/</icon>" +
            " <id>/r/milf.rss</id>" +
            " <link rel=\"self\" href=\"https://old.reddit.com/r/milf.rss\" type=\"application/atom+xml\" />" +
            " <link rel=\"alternate\" href=\"https://old.reddit.com/r/milf\" type=\"text/html\" />" +
            " <title>The MILF Reddit</title>" +
            " <entry>" +
            "  <author>" +
            "   <name>/u/Pissmittens</name>" +
            "   <uri>https://old.reddit.com/user/Pissmittens</uri>" +
            "  </author>" +
            "  <category term=\"milf\" label=\"r/milf\"/>" +
            "  <content type=\"text/html\">&lt;table&gt; &lt;tr&gt;&lt;td&gt; &lt;a href=&quot;https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/&quot;&gt; &lt;img src=&quot;https://external-preview.redd.it/MB4ENdAYnVeh73R-rB3g7AaNIApDyShCt6nbxK2J1dE.jpg?width=216&amp;amp;crop=smart&amp;amp;auto=webp&amp;amp;s=72e95ec50ab5e077e0916b299df29bda1bbb1f2e&quot; alt=&quot;Announcement: Official r/milf discord&quot; title=&quot;Announcement: Official r/milf discord&quot; /&gt; &lt;/a&gt; &lt;/td&gt;&lt;td&gt; &amp;#32; submitted by &amp;#32; &lt;a href=&quot;https://old.reddit.com/user/Pissmittens&quot;&gt; /u/Pissmittens &lt;/a&gt; &lt;br/&gt; &lt;span&gt;&lt;a href=&quot;https://discord.gg/M7MPQyZPfR&quot;&gt;[link]&lt;/a&gt;&lt;/span&gt; &amp;#32; &lt;span&gt;&lt;a href=&quot;https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/&quot;&gt;[comments]&lt;/a&gt;&lt;/span&gt; &lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;</content>" +
            "  <id>t3_yszhsp</id>" +
            "  <media:thumbnail url=\"https://external-preview.redd.it/MB4ENdAYnVeh73R-rB3g7AaNIApDyShCt6nbxK2J1dE.jpg?width=216&amp;crop=smart&amp;auto=webp&amp;s=72e95ec50ab5e077e0916b299df29bda1bbb1f2e\" />" +
            "  <link href=\"https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/\" />" +
            "  <updated>2022-11-12T07:16:52+00:00</updated>" +
            "  <published>2022-11-12T07:16:52+00:00</published>" +
            "  <title type=\"text\">Announcement: Official r/milf discord</title>" +
            " </entry>" +
            "</feed>";

    @Test
    public void testRssImporter_performAtomFeedImport() {
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed response = syndFeedInput.build(new StringReader(TEST_ATOM_RESPONSE));
            SyndFeedResponse syndFeedResponse = SyndFeedResponse.from(response, 200, "OK");
            when(SyndFeedService.fetch(
                    eq(TEST_ATOM_SUB.getUrl()),
                    isNull(),
                    isNull(),
                    eq("Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of 1 users"),
                    eq(true))
                ).thenReturn(syndFeedResponse);
            // carry out test
            ImportResult importResult = RssImporter.performImport(TEST_ATOM_SUB, new ImportResponseCallback() {
                @Override
                public ImportResult onSuccess(Set<StagingPost> set) {
                    assertNotNull(set);
                    assertEquals(1, set.size());
                    StagingPost stagingPost = set.iterator().next();
                    assertEquals("RssAtom", stagingPost.getImporterId());
                    assertEquals(667L, stagingPost.getQueueId());
                    assertEquals("http://localhost/test.atom", stagingPost.getImporterDesc());
                    assertNotNull(stagingPost.getPostTitle());
                    assertEquals("text", stagingPost.getPostTitle().getType());
                    assertEquals("Announcement: Official r/milf discord", stagingPost.getPostTitle().getValue());
                    //
                    assertNull(stagingPost.getPostDesc());
                    //
                    assertNotNull(stagingPost.getPostContents());
                    assertEquals(1, stagingPost.getPostContents().size());
                    assertEquals("text/html", stagingPost.getPostContents().get(0).getType());
                    assertEquals("<table> <tr><td> <a href=\"https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/\"> <img src=\"https://external-preview.redd.it/MB4ENdAYnVeh73R-rB3g7AaNIApDyShCt6nbxK2J1dE.jpg?width=216&amp;crop=smart&amp;auto=webp&amp;s=72e95ec50ab5e077e0916b299df29bda1bbb1f2e\" alt=\"Announcement: Official r/milf discord\" title=\"Announcement: Official r/milf discord\" /> </a> </td><td> &#32; submitted by &#32; <a href=\"https://old.reddit.com/user/Pissmittens\"> /u/Pissmittens </a> <br/> <span><a href=\"https://discord.gg/M7MPQyZPfR\">[link]</a></span> &#32; <span><a href=\"https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/\">[comments]</a></span> </td></tr></table>",
                            stagingPost.getPostContents().get(0).getValue());
                    //
                    assertEquals("https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/", stagingPost.getPostUrl());
                    //
                    List<PostUrl> postUrls = stagingPost.getPostUrls();
                    assertNotNull(postUrls);
                    assertEquals(0, postUrls.size());
                    //
                    assertEquals("https://external-preview.redd.it/MB4ENdAYnVeh73R-rB3g7AaNIApDyShCt6nbxK2J1dE.jpg?width=216&crop=smart&auto=webp&s=72e95ec50ab5e077e0916b299df29bda1bbb1f2e", stagingPost.getPostImgUrl());
                    assertNotNull(stagingPost.getImportTimestamp());
                    assertEquals("95F77180BE69B511EBBFA614D0007870", stagingPost.getPostHash());
                    assertEquals("me", stagingPost.getUsername());
                    //
                    List<String> postCategories = stagingPost.getPostCategories();
                    assertNotNull(postCategories);
                    assertEquals(1, postCategories.size());
                    String postCategory = postCategories.get(0);
                    assertEquals("milf", postCategory);
                    //
                    assertNotNull(stagingPost.getPublishTimestamp());
                    assertEquals("Sat Nov 12 01:16:52 CST 2022", stagingPost.getPublishTimestamp().toString());
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
    public void testRssImporter_doAtomFeedImport() {
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed response = syndFeedInput.build(new StringReader(TEST_ATOM_RESPONSE));
            SyndFeedResponse syndFeedResponse = SyndFeedResponse.from(response, 200, "OK");
            when(SyndFeedService.fetch(
                    eq(TEST_ATOM_SUB.getUrl()),
                    isNull(),
                    isNull(),
                    eq("Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of 1 users"),
                    eq(true))
                ).thenReturn(syndFeedResponse);
            rssImporter.doImport(singletonList(TEST_ATOM_SUB), emptyMap());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Override
    public String toString() {
        return "RssImporterTest_ATOM2{" +
                "configProps=" + configProps +
                ", successAggregator=" + successAggregator +
                ", errorAggregator=" + errorAggregator +
                ", subscriptionMetricsAggregator=" + subscriptionMetricsAggregator +
                ", rssMockDataGenerator=" + rssMockDataGenerator +
                ", syndFeedService=" + syndFeedService +
                ", rssImporter=" + rssImporter +
                '}';
    }
}
