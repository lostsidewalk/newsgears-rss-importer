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
public class RssImporterTest_ATOM2 {

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

    static final QueryDefinition TEST_ATOM_QUERY = QueryDefinition.from("testFeedIdent", "me", "http://localhost/test.atom", "ATOM", null);

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
            "  <content type=\"html\">&lt;table&gt; &lt;tr&gt;&lt;td&gt; &lt;a href=&quot;https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/&quot;&gt; &lt;img src=&quot;https://external-preview.redd.it/MB4ENdAYnVeh73R-rB3g7AaNIApDyShCt6nbxK2J1dE.jpg?width=216&amp;amp;crop=smart&amp;amp;auto=webp&amp;amp;s=72e95ec50ab5e077e0916b299df29bda1bbb1f2e&quot; alt=&quot;Announcement: Official r/milf discord&quot; title=&quot;Announcement: Official r/milf discord&quot; /&gt; &lt;/a&gt; &lt;/td&gt;&lt;td&gt; &amp;#32; submitted by &amp;#32; &lt;a href=&quot;https://old.reddit.com/user/Pissmittens&quot;&gt; /u/Pissmittens &lt;/a&gt; &lt;br/&gt; &lt;span&gt;&lt;a href=&quot;https://discord.gg/M7MPQyZPfR&quot;&gt;[link]&lt;/a&gt;&lt;/span&gt; &amp;#32; &lt;span&gt;&lt;a href=&quot;https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/&quot;&gt;[comments]&lt;/a&gt;&lt;/span&gt; &lt;/td&gt;&lt;/tr&gt;&lt;/table&gt;</content>" +
            "  <id>t3_yszhsp</id>" +
            "  <media:thumbnail url=\"https://external-preview.redd.it/MB4ENdAYnVeh73R-rB3g7AaNIApDyShCt6nbxK2J1dE.jpg?width=216&amp;crop=smart&amp;auto=webp&amp;s=72e95ec50ab5e077e0916b299df29bda1bbb1f2e\" />" +
            "  <link href=\"https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/\" />" +
            "  <updated>2022-11-12T07:16:52+00:00</updated>" +
            "  <published>2022-11-12T07:16:52+00:00</published>" +
            "  <title>Announcement: Official r/milf discord</title>" +
            " </entry>" +
            "</feed>";

    @Test
    public void testRssImporter_performAtomFeedImport() {
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed syndFeedResponse = syndFeedInput.build(new StringReader(TEST_ATOM_RESPONSE));
            when(this.syndFeedService.fetch(TEST_ATOM_QUERY.getQueryText())).thenReturn(syndFeedResponse);
            // carry out test
            ImporterMetrics importerMetrics = rssImporter.performImport(TEST_ATOM_QUERY, new Importer.ImportResponseCallback() {
                @Override
                public void onSuccess(Set<StagingPost> set) {
                    assertNotNull(set);
                    assertEquals(1, set.size());
                    StagingPost s = set.iterator().next();
                    assertEquals("RssAtom", s.getImporterId());
                    assertEquals("testFeedIdent", s.getFeedIdent());
                    assertEquals("[query=http://localhost/test.atom]", s.getImporterDesc());
                    assertEquals("Announcement: Official r/milf discord", s.getPostTitle());
                    assertEquals("<table> <tr><td> <a href=\"https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/\"> <img src=\"https://external-preview.redd.it/MB4ENdAYnVeh73R-rB3g7AaNIApDyShCt6nbxK2J1dE.jpg?width=216&amp;crop=smart&amp;auto=webp&amp;s=72e95ec50ab5e077e0916b299df29bda1bbb1f2e\" alt=\"Announcement: Official r/milf discord\" title=\"Announcement: Official r/milf discord\" /> </a> </td><td> &#32; submitted by &#32; <a href=\"https://old.reddit.com/user/Pissmittens\"> /u/Pissmittens </a> <br/> <span><a href=\"https://discord.gg/M7MPQyZPfR\">[link]</a></span> &#32; <span><a href=\"https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/\">[comments]</a></span> </td></tr></table>", s.getPostDesc());
                    assertEquals("https://old.reddit.com/r/milf/comments/yszhsp/announcement_official_rmilf_discord/", s.getPostUrl());
                    assertEquals("https://external-preview.redd.it/MB4ENdAYnVeh73R-rB3g7AaNIApDyShCt6nbxK2J1dE.jpg?width=216&crop=smart&auto=webp&s=72e95ec50ab5e077e0916b299df29bda1bbb1f2e", s.getPostImgUrl());
                    assertNotNull(s.getImportTimestamp());
                    assertEquals("C8D44AB6805DC75F1AA0F26061E13D86", s.getPostHash());
                    assertEquals("me", s.getUsername());
                    assertEquals("/u/Pissmittens", s.getAuthorName());
                    assertEquals("milf", s.getPostCategory());
                    assertNotNull(s.getPublishTimestamp());
                    assertEquals("Sat Nov 12 01:16:52 CST 2022", s.getPublishTimestamp().toString());
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
    public void testRssImporter_doAtomFeedImport() {
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed syndFeedResponse = syndFeedInput.build(new StringReader(TEST_ATOM_RESPONSE));
            when(this.syndFeedService.fetch(TEST_ATOM_QUERY.getQueryText())).thenReturn(syndFeedResponse);
            rssImporter.doImport(singletonList(TEST_ATOM_QUERY));
            verify(this.successAggregator, times(1)).offer(any());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
