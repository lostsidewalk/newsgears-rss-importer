package com.lostsidewalk.buffy.rss;

import com.lostsidewalk.buffy.Importer.ImportResponseCallback;
import com.lostsidewalk.buffy.Importer.ImportResult;
import com.lostsidewalk.buffy.post.PostPerson;
import com.lostsidewalk.buffy.post.PostUrl;
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
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static java.util.Collections.*;
import static org.apache.commons.collections4.CollectionUtils.size;
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
    Queue<QueryMetrics> queryMetricsAggregator;

    @MockBean
    RssMockDataGenerator rssMockDataGenerator;

    @MockBean
    SyndFeedService syndFeedService;

    @Autowired
    RssImporter rssImporter;

    static final QueryDefinition TEST_ATOM_QUERY = QueryDefinition.from(666L, "me", "testQuery", "http://localhost/test.atom", "ATOM", null);

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
            "  <content type=\"text/html\">&lt;!-- SC_OFF --&gt;&lt;div class=&quot;md&quot;&gt;&lt;h1&gt;&lt;a href=&quot;/r/java&quot;&gt;/r/java&lt;/a&gt; is not for programming help or learning Java&lt;/h1&gt; &lt;ul&gt; &lt;li&gt;&lt;strong&gt;Programming related questions&lt;/strong&gt; do not belong here. They belong in &lt;strong&gt;&lt;a href=&quot;/r/javahelp&quot;&gt;/r/javahelp&lt;/a&gt;&lt;/strong&gt;. &lt;/li&gt; &lt;li&gt;&lt;strong&gt;Learning related questions&lt;/strong&gt; belong in &lt;strong&gt;&lt;a href=&quot;/r/learnjava&quot;&gt;/r/learnjava&lt;/a&gt;&lt;/strong&gt;&lt;/li&gt; &lt;/ul&gt; &lt;p&gt;Such posts will be removed.&lt;/p&gt; &lt;p&gt;&lt;strong&gt;To the community willing to help:&lt;/strong&gt;&lt;/p&gt; &lt;p&gt;Instead of immediately jumping in and helping, please &lt;strong&gt;direct the poster to the appropriate subreddit&lt;/strong&gt; and &lt;strong&gt;report the post&lt;/strong&gt;.&lt;/p&gt; &lt;/div&gt;&lt;!-- SC_ON --&gt; &amp;#32; submitted by &amp;#32; &lt;a href=&quot;https://old.reddit.com/user/desrtfx&quot;&gt; /u/desrtfx &lt;/a&gt; &lt;br/&gt; &lt;span&gt;&lt;a href=&quot;https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/&quot;&gt;[link]&lt;/a&gt;&lt;/span&gt; &amp;#32; &lt;span&gt;&lt;a href=&quot;https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/&quot;&gt;[comments]&lt;/a&gt;&lt;/span&gt;</content>" +
            "  <id>t3_j7h9er</id>" +
            "  <link href=\"https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/\" />" +
            "  <updated>2020-10-08T17:21:51+00:00</updated>" +
            "  <published>2020-10-08T17:21:51+00:00</published>" +
            "  <title type=\"text\">URLs don’t belong in &lt;meta&gt; elements</title>" +
            " </entry>" +
            "</feed>";

    @Test
    public void testRssImporter_performAtomFeedImport() {
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed response = syndFeedInput.build(new StringReader(TEST_ATOM_RESPONSE));
            SyndFeedResponse syndFeedResponse = SyndFeedResponse.from(response, 200, "OK");
            // String url, String username, String password, String userAgent, boolean followUnsecureRedirects
            when(this.syndFeedService.fetch(
                    eq(TEST_ATOM_QUERY.getQueryText()),
                    isNull(),
                    isNull(),
                    eq("Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of 1 users"),
                    eq(false))
                ).thenReturn(syndFeedResponse);
            // carry out test
            ImportResult importResult = rssImporter.performImport(TEST_ATOM_QUERY, new ImportResponseCallback() {
                @Override
                public ImportResult onSuccess(Set<StagingPost> set) {
                    assertNotNull(set);
                    assertEquals(1, set.size());
                    StagingPost s = set.iterator().next();
                    assertEquals("RssAtom", s.getImporterId());
                    assertEquals(666L, s.getFeedId());
                    assertEquals("http://localhost/test.atom", s.getImporterDesc());
                    //
                    assertNotNull(s.getPostTitle());
                    assertEquals("text", s.getPostTitle().getType());
                    assertEquals("URLs don’t belong in <meta> elements", s.getPostTitle().getValue());
                    //
                    assertNull(s.getPostDesc());
                    //
                    assertNotNull(s.getPostContents());
                    assertEquals(1, s.getPostContents().size());
                    assertEquals("text/html", s.getPostContents().get(0).getType());
                    assertEquals("<!-- SC_OFF --><div class=\"md\"><h1><a href=\"/r/java\">/r/java</a> is not for programming help or learning Java</h1> <ul> <li><strong>Programming related questions</strong> do not belong here. They belong in <strong><a href=\"/r/javahelp\">/r/javahelp</a></strong>. </li> <li><strong>Learning related questions</strong> belong in <strong><a href=\"/r/learnjava\">/r/learnjava</a></strong></li> </ul> <p>Such posts will be removed.</p> <p><strong>To the community willing to help:</strong></p> <p>Instead of immediately jumping in and helping, please <strong>direct the poster to the appropriate subreddit</strong> and <strong>report the post</strong>.</p> </div><!-- SC_ON --> &#32; submitted by &#32; <a href=\"https://old.reddit.com/user/desrtfx\"> /u/desrtfx </a> <br/> <span><a href=\"https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/\">[link]</a></span> &#32; <span><a href=\"https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/\">[comments]</a></span>",
                            s.getPostContents().get(0).getValue()
                    );
                    //
                    assertEquals("https://old.reddit.com/r/java/comments/j7h9er/psarjava_is_not_for_programming_help_learning/", s.getPostUrl());
                    //
                    List<PostUrl> postUrls = s.getPostUrls();
                    assertNotNull(postUrls);
                    assertEquals(0, postUrls.size());
                    //
                    assertNull(s.getPostImgUrl());
                    assertNotNull(s.getImportTimestamp());
                    assertEquals("DEC847424E648C7F730D73C1EE50105B", s.getPostHash());
                    assertEquals("me", s.getUsername());
                    //
                    List<PostPerson> authors = s.getAuthors();
                    assertNotNull(authors);
                    assertEquals(1, authors.size());
                    PostPerson author = authors.get(0);
                    assertEquals("/u/desrtfx", author.getName());
                    assertNull(author.getEmail());
                    assertEquals("https://old.reddit.com/user/desrtfx", author.getUri());
                    //
                    assertNotNull(s.getPublishTimestamp());
                    assertEquals("Thu Oct 08 12:21:51 CDT 2020", s.getPublishTimestamp().toString());
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
    public void testRssImporter_doAtomFeedImport() {
        try {
            // setup mocks
            SyndFeedInput syndFeedInput = new SyndFeedInput();
            SyndFeed response = syndFeedInput.build(new StringReader(TEST_ATOM_RESPONSE));
            SyndFeedResponse syndFeedResponse = SyndFeedResponse.from(response, 200, "OK");
            when(this.syndFeedService.fetch(
                    eq(TEST_ATOM_QUERY.getQueryText()),
                    isNull(),
                    isNull(),
                    eq("Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of 1 users"),
                    eq(false))
                ).thenReturn(syndFeedResponse);
            rssImporter.doImport(singletonList(TEST_ATOM_QUERY));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
