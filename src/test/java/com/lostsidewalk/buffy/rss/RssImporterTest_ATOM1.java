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
@ContextConfiguration(classes = {RssImporter.class, RssImporterConfigProps.class})
public class RssImporterTest_ATOM1 {

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
            " <category term=\"java\" label=\"r/java\"/>" +
            " <updated>2022-11-16T14:12:26+00:00</updated>" +
            " <icon>https://www.redditstatic.com/icon.png/</icon>" +
            " <id>/r/java.rss</id>" +
            " <link rel=\"self\" href=\"https://old.reddit.com/r/java.rss\" type=\"application/atom+xml\" />" +
            " <link rel=\"alternate\" href=\"https://old.reddit.com/r/java\" type=\"text/html\" />" +
            " <logo>https://c.thumbs.redditmedia.com/gCMzcSXDxoDQ4e62.png</logo>" +
            " <subtitle>News, Technical discussions, research papers and assorted things of interest related to the Java programming language NO programming help, NO learning Java related questions, NO installing or downloading Java questions, NO JVM languages - Exclusively Java!</subtitle>" +
            " <title>Java News/Tech/Discussion/etc. No programming help, no learning Java</title>" +
            " <entry>" +
            "  <author>" +
            "   <name>/u/desrtfx</name>" +
            "   <uri>https://old.reddit.com/user/desrtfx</uri>" +
            "  </author>" +
            "  <category term=\"java\" label=\"r/java\"/>" +
            "  <content type=\"html\">&lt;!-- SC_OFF --&gt;&lt;div class=&quot;md&quot;&gt;&lt;h1&gt;&lt;a href=&quot;/r/java&quot;&gt;/r/java&lt;/a&gt; is not for programming help or learning Java&lt;/h1&gt; &lt;ul&gt; &lt;li&gt;&lt;strong&gt;Programming related questions&lt;/strong&gt; do not belong here. They belong in &lt;strong&gt;&lt;a href=&quot;/r/javahelp&quot;&gt;/r/javahelp&lt;/a&gt;&lt;/strong&gt;. &lt;/li&gt; &lt;li&gt;&lt;strong&gt;Learning related questions&lt;/strong&gt; belong in &lt;strong&gt;&lt;a href=&quot;/r/learnjava&quot;&gt;/r/learnjava&lt;/a&gt;&lt;/strong&gt;&lt;/li&gt; &lt;/ul&gt; &lt;p&gt;Such posts will be removed.&lt;/p&gt; &lt;p&gt;&lt;strong&gt;To the community willing to help:&lt;/strong&gt;&lt;/p&gt; &lt;p&gt;Instead of immediately jumping in and helping, please &lt;strong&gt;direct the poster to the appropriate subreddit&lt;/strong&gt; and &lt;strong&gt;report the post&lt;/strong&gt;.&lt;/p&gt; &lt;/div&gt;&lt;!-- SC_ON --&gt; &amp;#32; submitted by &amp;#32; &lt;a href=&quot;https://old.reddit.com/user/desrtfx&quot;&gt; /u/desrtfx &lt;/a&gt; &lt;br/&gt; &lt;span&gt;&lt;a href=&quot;https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/&quot;&gt;[link]&lt;/a&gt;&lt;/span&gt; &amp;#32; &lt;span&gt;&lt;a href=&quot;https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/&quot;&gt;[comments]&lt;/a&gt;&lt;/span&gt;</content>" +
            "  <id>t3_j7h9er</id>" +
            "  <link href=\"https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/\" />" +
            "  <updated>2020-10-08T17:21:51+00:00</updated>" +
            "  <published>2020-10-08T17:21:51+00:00</published>" +
            "  <title>[PSA]/r/java is not for programming help, learning questions, or installing Java questions</title>" +
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
                    assertEquals("[PSA]/r/java is not for programming help, learning questions, or installing Java questions", s.getPostTitle());
                    assertEquals("<!-- SC_OFF --><div class=\"md\"><h1><a href=\"/r/java\">/r/java</a> is not for programming help or learning Java</h1> <ul> <li><strong>Programming related questions</strong> do not belong here. They belong in <strong><a href=\"/r/javahelp\">/r/javahelp</a></strong>. </li> <li><strong>Learning related questions</strong> belong in <strong><a href=\"/r/learnjava\">/r/learnjava</a></strong></li> </ul> <p>Such posts will be removed.</p> <p><strong>To the community willing to help:</strong></p> <p>Instead of immediately jumping in and helping, please <strong>direct the poster to the appropriate subreddit</strong> and <strong>report the post</strong>.</p> </div><!-- SC_ON --> &#32; submitted by &#32; <a href=\"https://old.reddit.com/user/desrtfx\"> /u/desrtfx </a> <br/> <span><a href=\"https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/\">[link]</a></span> &#32; <span><a href=\"https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/\">[comments]</a></span>", s.getPostDesc());
                    assertEquals("https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/", s.getPostUrl());
                    assertNull(s.getPostImgUrl());
                    assertNotNull(s.getImportTimestamp());
                    assertEquals("D4D5F7300384B4E20A62C2A27267E6BB", s.getPostHash());
                    assertEquals("me", s.getUsername());
                    assertEquals("/u/desrtfx", s.getAuthorName());
                    assertEquals("java", s.getPostCategory());
                    assertNotNull(s.getPublishTimestamp());
                    assertEquals("Thu Oct 08 12:21:51 CDT 2020", s.getPublishTimestamp().toString());
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
