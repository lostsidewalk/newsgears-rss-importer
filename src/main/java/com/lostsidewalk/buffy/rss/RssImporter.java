package com.lostsidewalk.buffy.rss;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lostsidewalk.buffy.Importer;
import com.lostsidewalk.buffy.post.*;
import com.lostsidewalk.buffy.query.QueryDefinition;
import com.lostsidewalk.buffy.query.QueryMetrics;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedException;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService.SyndFeedResponse;
import com.rometools.modules.itunes.ITunes;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.MediaEntryModuleImpl;
import com.rometools.modules.mediarss.types.*;
import com.rometools.rome.feed.synd.*;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toMap;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.CollectionUtils.size;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.apache.commons.lang3.SerializationUtils.serialize;
import static org.apache.commons.lang3.StringUtils.*;

@Slf4j
@Component
public class RssImporter implements Importer {

    private static final Gson GSON = new Gson();

    @Data
    static class RssQuery {

        private final String queryText;

        private final String queryType;

        private final Serializable queryConfig;

        private RssQuery(QueryDefinition queryDefinition) {
            this.queryText = queryDefinition.getQueryText();
            this.queryType = queryDefinition.getQueryType();
            this.queryConfig = queryDefinition.getQueryConfig();
        }

        static RssQuery from(QueryDefinition queryDefinition) {
            return new RssQuery(queryDefinition);
        }
    }

    @Autowired
    RssImporterConfigProps configProps;

    @Autowired
    private Queue<StagingPost> successAggregator;

    @Autowired
    private Queue<Throwable> errorAggregator;

    @Autowired
    private RssMockDataGenerator rssMockDataGenerator;

    @Autowired
    private SyndFeedService syndFeedService;

    private ExecutorService rssThreadPool;

    @PostConstruct
    public void postConstruct() {
        //
        // banner message
        //
        log.info("RSS importer constructed at {}", Instant.now());
        //
        // thread pool setup
        //
        int processorCt = Runtime.getRuntime().availableProcessors() - 1;
        log.info("Starting discovery thread pool: processCount={}", processorCt);
        this.rssThreadPool = newFixedThreadPool(processorCt, new ThreadFactoryBuilder().setNameFormat("rss-importer-%d").build());
    }

    @Override
    public ImportResult doImport(List<QueryDefinition> queryDefinitions) {
        if (this.configProps.getDisabled()) {
            log.warn("RSS importer is administratively disabled");
            if (this.configProps.getImportMockData()) {
                log.warn("RSS importer importing mock records");
                Set<StagingPost> allStagingPosts = new HashSet<>();
                List<QueryMetrics> allQueryMetrics = new ArrayList<>(size(queryDefinitions));
                CountDownLatch latch = new CountDownLatch(size(queryDefinitions));
                queryDefinitions.forEach(q -> {
                    ImportResult ir = getArticlesResponseHandler(new HashSet<>(queryDefinitions), latch)
                        .onSuccess(rssMockDataGenerator.buildMockResponse(q));
                    allStagingPosts.addAll(ir.getImportSet());
                    allQueryMetrics.addAll(ir.getQueryMetrics());
                });
                return ImportResult.from(allStagingPosts, allQueryMetrics);
            }

            return ImportResult.from(emptySet(), emptyList());
        }

        log.info("RSS importer running at {}", Instant.now());

        List<QueryDefinition> supportedQueryDefinitions = queryDefinitions.parallelStream()
                .filter(q -> supportsQueryType(q.getQueryType()))
                .toList();
        //
        Map<QueryDefinition, RssQuery> allQueryMap = supportedQueryDefinitions.stream().collect(toMap(q -> q, RssQuery::from));
        //
        Map<RssQuery, Set<QueryDefinition>> uniqueQueryMap = new HashMap<>();
        //
        allQueryMap.forEach((key, value) -> uniqueQueryMap.computeIfAbsent(value, ignored -> new HashSet<>()).add(key));
        //
        Set<StagingPost> allStagingPosts = new HashSet<>();
        List<QueryMetrics> allQueryMetrics = new ArrayList<>(size(supportedQueryDefinitions));
        //
        CountDownLatch latch = new CountDownLatch(size(uniqueQueryMap.keySet()) * 2);
        log.info("RSS import latch initialized to: {}", latch.getCount());
        uniqueQueryMap.forEach((r, q) -> rssThreadPool.submit(() -> {
            ImportResult importResult = this.performImport(r, size(q), getArticlesResponseHandler(q, latch));
            allStagingPosts.addAll(importResult.getImportSet());
            allQueryMetrics.addAll(importResult.getQueryMetrics());
            latch.countDown();
            if (latch.getCount() % 50 == 0) {
                log.info("RSS import latch currently at {}: ", latch.getCount());
            }
        }));
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("RSS import process interrupted due to: {}", e.getMessage());
        }

        log.info("RSS importer finished at {}", Instant.now());

        return ImportResult.from(allStagingPosts, allQueryMetrics);
    }

    private boolean supportsQueryType(String queryType) {
        return equalsAnyIgnoreCase(queryType, SUPPORTED_QUERY_TYPES);
    }

    interface SyndFeedResponseCallback {

        ImportResult onSuccess(SyndFeedResponse response);

        ImportResult onFailure(SyndFeedException error);
    }

    private SyndFeedResponseCallback getArticlesResponseHandler(Set<QueryDefinition> queryDefinitions, CountDownLatch latch) {
        return new SyndFeedResponseCallback() {
            @Override
            public ImportResult onSuccess(SyndFeedResponse response) {
                Set<StagingPost> importSet = new HashSet<>();
                List<QueryMetrics> queryMetrics = new ArrayList<>(size(queryDefinitions));
                Date importTimestamp = new Date();
                // for ea. query,
                for (QueryDefinition q : queryDefinitions) {
                    // convert the syndfeed response into a stream of staging posts for that query, and send them to the success agg. queue
                    Set<StagingPost> importedArticles = importArticleResponse(q.getFeedId(), q.getQueryText(), q.getQueryTitle(), response.getSyndFeed(), q.getUsername(), importTimestamp);
                    importSet.addAll(importedArticles);
                    latch.countDown();
                    // update query metrics
                    queryMetrics.add(QueryMetrics.from(
                            q.getId(),
                            response.getHttpStatusCode(),
                            response.getHttpStatusMessage(),
                            response.getRedirectUrl(),
                            response.getRedirectHttpStatusCode(),
                            response.getRedirectHttpStatusMessage(),
                            importTimestamp,
                            size(importedArticles)
                        ));
                    log.info("Import success, feedId={}, username={}, queryType={}, queryText={}, importCt={}", q.getFeedId(), q.getUsername(), q.getQueryType(), q.getQueryText(), size(importedArticles));
                }

                return ImportResult.from(importSet, queryMetrics);
            }

            @Override
            public ImportResult onFailure(SyndFeedException exception) {
                log.error("Import failure due to: {}", exception.getMessage());
                errorAggregator.offer(exception);
                List<QueryMetrics> queryMetrics = new ArrayList<>(size(queryDefinitions));
                queryDefinitions.stream()
                    .map(q -> QueryMetrics.from(
                        q.getId(),
                        exception.httpStatusCode,
                        exception.httpStatusMessage,
                        exception.redirectUrl,
                        exception.redirectHttpStatusCode,
                        exception.redirectHttpStatusMessage,
                        new Date(), // import timestamp
                        0 // import ct
                    )).forEach(m -> {
                        latch.countDown();
                        m.setErrorType(exception.exceptionType);
                        m.setErrorDetail(exception.getMessage());
                        queryMetrics.add(m);
                    });

                return ImportResult.from(emptySet(), queryMetrics);
            }
        };
    }

    private static Set<StagingPost> importArticleResponse(Long feedId, String query, String queryTitle, SyndFeed response, String username, Date importTimestamp) {
        Set<StagingPost> stagingPosts = new HashSet<>();
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (SyndEntry e : response.getEntries()) {
                //
                StagingPost p = StagingPost.from(
                        RSS_ATOM_IMPORTER_ID, // importer Id
                        feedId, // feed Id
                        getImporterDesc(queryTitle, query), // importer desc (feed title)
                        getObjectSource(e), // source
                        ofNullable(e.getSource()).map(SyndFeed::getTitle).map(StringUtils::trim).orElse(response.getTitle()), // source name (or feed title)
                        ofNullable(e.getSource()).map(SyndFeed::getLink).map(StringUtils::trim).orElse(response.getLink()), // source url (or feed link)
                        // HERE: post_title_type
                        ofNullable(e.getTitleEx()).map(RssImporter::convertContentObject).orElse(null), // post title
                        // HERE: description_type
                        ofNullable(e.getDescription()).map(RssImporter::convertContentObject).orElse(null),
                        // HERE: List<String> postContents now needs to List<String, String> so that content type can ride along
                        ofNullable(e.getContents()).map(RssImporter::convertContentList).orElse(null), // post contents
                        getPostMedia(e), // post media
                        getPostITunes(e), // post iTunes
                        trim(e.getLink()), // post URL
                        ofNullable(e.getLinks()).map(RssImporter::convertLinkList).orElse(null), // post URLs
                        getThumbnailUrl(e), // post img URL
                        importTimestamp, // import timestamp
                        computeHash(md, feedId, getObjectSource(e)), // post hash
                        username, // username
                        trim(e.getComments()), // post comments
                        response.getCopyright(), // post rights
                        ofNullable(e.getContributors()).map(RssImporter::convertPersonList).orElse(null), // contributors
                        ofNullable(getAuthors(e)).map(RssImporter::convertPersonList).orElse(null), // authors
                        ofNullable(e.getCategories()).map(RssImporter::convertCategoryList).orElse(null), // post categories
                        e.getPublishedDate(), // publish timestamp
                        null, // expiration timestamp (none)
                        ofNullable(e.getEnclosures()).map(RssImporter::convertEnclosureList).orElse(null), // enclosures
                        e.getUpdatedDate() // updated timestamp
                );
                stagingPosts.add(p);
            }
        } catch (NoSuchAlgorithmException ignored) {}

        return stagingPosts;
    }

    private static String getImporterDesc(String desc, String defaultDesc) {
        return trimToEmpty(defaultString(desc, defaultDesc));
    }

    private static String getObjectSource(SyndEntry e) {
        JsonObject objectSrc = new JsonObject();
        objectSrc.addProperty("title", e.getTitle());
        objectSrc.addProperty("description", ofNullable(e.getDescription()).map(SyndContent::getValue).orElse(EMPTY));
        objectSrc.addProperty("link", e.getLink());
        return objectSrc.toString();
    }

    private static PostMedia getPostMedia(SyndEntry e) {
        PostMedia pm = null;
        MediaEntryModuleImpl mm = (MediaEntryModuleImpl) e.getModule(MediaEntryModule.URI);
        if (mm != null) {
            pm = PostMedia.from(mm);
        }
        return pm;
    }

    private static PostITunes getPostITunes(SyndEntry e) {
        PostITunes pi = null;
        ITunes im = (ITunes) e.getModule(ITunes.URI);
        if (im != null) {
            pi = PostITunes.from(im);
        }
        return pi;
    }

    private static String getThumbnailFromMetadata(Metadata md) {
        if (md != null) {
            Thumbnail[] thumbnails = md.getThumbnail();
            if (isNotEmpty(thumbnails)) {
                URI uri = thumbnails[0].getUrl();
                if (uri != null) {
                    return uri.toString();
                }
            }
        }
        return null;
    }

    private static String getThumbnailUrl(SyndEntry e) {
        String thumbnailUrl;
        // get the media module, if any
        MediaEntryModule mm = (MediaEntryModule) e.getModule(MediaEntryModule.URI);
        if (mm != null) {
            // check top-level metadata for thumbnail
            Metadata topLevelMetadata = mm.getMetadata();
            thumbnailUrl = getThumbnailFromMetadata(topLevelMetadata);
            if (thumbnailUrl != null) {
                return thumbnailUrl;
            }
            // check ea. top-level media content metadata for thumbnail
            MediaContent[] topLevelMediaContents = mm.getMediaContents();
            if (isNotEmpty(topLevelMediaContents)) {
                for (MediaContent mediaContent : topLevelMediaContents) {
                    Metadata mcMd = mediaContent.getMetadata();
                    thumbnailUrl = getThumbnailFromMetadata(mcMd);
                    if (thumbnailUrl != null) {
                        return thumbnailUrl;
                    }
                }
            }
            // check ea. top-level media group metadata for thumbnail
            MediaGroup[] topLevelMediaGroups = mm.getMediaGroups();
            if (isNotEmpty(topLevelMediaGroups)) {
                for (MediaGroup mg : topLevelMediaGroups) {
                    Metadata mgMd = mg.getMetadata();
                    thumbnailUrl = getThumbnailFromMetadata(mgMd);
                    if (thumbnailUrl != null) {
                        return thumbnailUrl;
                    }
                }
            }
            // check ea. top-level media contents for first non-null reference
            if (isNotEmpty(topLevelMediaContents)) {
                for (MediaContent mediaContent : topLevelMediaContents) {
                    Reference reference = mediaContent.getReference();
                    if (reference != null) {
                        return reference.toString();
                    }
                }
            }
            // check ea. top-level media group for first meda content w/non-null reference
            if (isNotEmpty(topLevelMediaGroups)) {
                for (MediaGroup mediaGroup : topLevelMediaGroups) {
                    for (MediaContent mediaContent : mediaGroup.getContents()) {
                        Reference reference = mediaContent.getReference();
                        if (reference != null) {
                            return reference.toString();
                        }
                    }
                }
            }
        }

        return null;
    }

    private static List<SyndPerson> getAuthors(SyndEntry e) {
        List<SyndPerson> authors = e.getAuthors(); // marshall up the authors
        String primaryAuthorName = e.getAuthor(); // grab the 'primary author' name
        if (isNotBlank(primaryAuthorName)) {
            boolean found = false;
            for (SyndPerson a : authors) {
                if (a.getName().equals(primaryAuthorName)) {
                    found = true;
                    break;
                }
            }
            // ensure that the primary author is part of the set of authors
            if (!found) {
                SyndPerson primaryAuthor = new SyndPersonImpl();
                primaryAuthor.setName(primaryAuthorName);
                authors.add(primaryAuthor);
            }
        }

        return authors;
    }

    private static ContentObject convertContentObject(SyndContent content) {
        ContentObject contentObject = null;
        if (content != null) {
            contentObject = ContentObject.from(content.getType(), content.getValue());
        }
        return contentObject;
    }

    private static List<ContentObject> convertContentList(List<SyndContent> contents) {
        List<ContentObject> list = null;
        if (isNotEmpty(contents)) {
            list = new ArrayList<>(size(contents));
            for (SyndContent c : contents) {
                list.add(ContentObject.from(c.getType(), c.getValue()));
            }
        }
        return list;
    }

    private static List<PostUrl> convertLinkList(List<SyndLink> links) {
        List<PostUrl> list = null;
        if (isNotEmpty(links)) {
            list = new ArrayList<>();
            for (SyndLink l : links) {
                if (l.getRel().equals("alternate")) {
                    continue;
                }
                PostUrl p = new PostUrl();
                p.setTitle(l.getTitle());
                p.setType(l.getType());
                p.setHref(l.getHref());
                p.setHreflang(l.getHreflang());
                p.setRel(l.getRel());
                list.add(p);
            }
        }
        return list;
    }

    private static List<PostPerson> convertPersonList(List<SyndPerson> persons) {
        List<PostPerson> list = null;
        if (isNotEmpty(persons)) {
            list = new ArrayList<>();
            for (SyndPerson p : persons) {
                PostPerson pp = new PostPerson();
                pp.setName(p.getName());
                pp.setEmail(p.getEmail());
                pp.setUri(p.getUri());
                list.add(pp);
            }
        }
        return list;
    }

    private static List<String> convertCategoryList(List<SyndCategory> categories) {
        List<String> list = null;
        if (isNotEmpty(categories)) {
            list = new ArrayList<>();
            for (SyndCategory c : categories) {
                list.add(c.getName());
            }
        }
        return list;
    }

    private static List<PostEnclosure> convertEnclosureList(List<SyndEnclosure> enclosures) {
        List<PostEnclosure> list = null;
        if (CollectionUtils.isNotEmpty(enclosures)) {
            list = new ArrayList<>();
            for (SyndEnclosure e : enclosures) {
                PostEnclosure p = new PostEnclosure();
                p.setUrl(e.getUrl());
                p.setType(e.getType());
                p.setLength(e.getLength());
                list.add(p);
            }
        }
        return list;
    }

    private static String computeHash(MessageDigest md, Long feedId, Serializable objectSrc) {
        return printHexBinary(md.digest(serialize(String.format("%s:%s", feedId, objectSrc))));
    }

    public static final String RSS = "RSS";

    public static final String ATOM = "ATOM";

    private static final String[] SUPPORTED_QUERY_TYPES = new String[] {
            RSS, ATOM
    };

    private static final String RSS_ATOM_IMPORTER_ID = "RssAtom";

    ImportResult performImport(QueryDefinition queryDefinition, ImportResponseCallback importResponseCallback) {
        requireNonNull(queryDefinition, "Query definition must not be null");
        requireNonNull(importResponseCallback, "Import response callback must not be null");
        return this.performImport(RssQuery.from(queryDefinition), 1, new SyndFeedResponseCallback() {
            @Override
            public ImportResult onSuccess(SyndFeedResponse fullResponse) {
                Set<StagingPost> stagingPosts = importArticleResponse(
                        queryDefinition.getFeedId(),
                        queryDefinition.getQueryTitle(),
                        queryDefinition.getQueryText(),
                        fullResponse.getSyndFeed(),
                        queryDefinition.getUsername(),
                        new Date() // import timestamp
                );
                return importResponseCallback.onSuccess(stagingPosts);
            }

            @Override
            public ImportResult onFailure(SyndFeedException exception) {
                return importResponseCallback.onFailure(exception);
            }
        });
    }

    private static final String RSS_ATOM_IMPORTER_USER_AGENT = "Lost Sidewalk FeedGears RSS Aggregator v.0.4 feed import process, on behalf of %d users";

    private ImportResult performImport(RssQuery rssQuery, int subscriberCt, SyndFeedResponseCallback syndFeedResponseCallback) {
        log.info("Importing rssQuery={}", rssQuery);

        String queryType = rssQuery.getQueryType();
        String queryText = rssQuery.getQueryText();
        JsonObject queryConfigObj = ofNullable(rssQuery.getQueryConfig())
                .map(Object::toString)
                .map(s -> GSON.fromJson(s, JsonObject.class))
                .orElse(null);
        String feedUsername = getStringProperty(queryConfigObj, "username");
        String feedPassword = getStringProperty(queryConfigObj, "password");

        ImportResult importResult = null;
        switch (queryType) {
            case ATOM, RSS -> {
                try {
                    log.info("Fetching RSS feed from url={}", queryText);
                    String userAgent = String.format(RSS_ATOM_IMPORTER_USER_AGENT, subscriberCt);
                    importResult = syndFeedResponseCallback.onSuccess(syndFeedService.fetch(queryText, feedUsername, feedPassword, userAgent, false));
                } catch (SyndFeedException e) {
                    importResult = syndFeedResponseCallback.onFailure(e);
                }
            }
            default -> log.error("Query type not supported by this importer: queryType={}, importerId={}", queryType, getImporterId());
        }
        return importResult;
    }

    private static String getStringProperty(JsonObject obj, String propName) {
        return obj != null && obj.has(propName) ? obj.get(propName).getAsString() : null;
    }

    @Override
    public String getImporterId() {
        return RSS_ATOM_IMPORTER_ID;
    }
}
