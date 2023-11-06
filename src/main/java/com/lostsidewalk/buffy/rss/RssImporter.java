package com.lostsidewalk.buffy.rss;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lostsidewalk.buffy.importer.Importer;
import com.lostsidewalk.buffy.discovery.FeedDiscoveryInfo;
import com.lostsidewalk.buffy.post.*;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedException;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService.SyndFeedResponse;
import com.lostsidewalk.buffy.subscription.SubscriptionDefinition;
import com.lostsidewalk.buffy.subscription.SubscriptionMetrics;
import com.rometools.modules.itunes.ITunes;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.MediaModule;
import com.rometools.modules.mediarss.types.*;
import com.rometools.rome.feed.synd.*;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
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

import static java.util.Collections.*;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toMap;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.CollectionUtils.size;
import static org.apache.commons.collections4.MapUtils.isEmpty;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.apache.commons.lang3.SerializationUtils.serialize;
import static org.apache.commons.lang3.StringUtils.*;

/**
 * This class represents an RSS importer that is responsible for fetching and importing RSS feeds.
 * It provides functionality to import RSS feeds, parse them, and store the content for further processing.
 */
@Slf4j
@Component
public class RssImporter implements Importer {

    private static final Gson GSON = new Gson();

    /**
     * Default constructor; initializes the object.
     */
    RssImporter() {
    }

    @Data
    static class RssQuery {

        private final String url;

        private final String queryType;

        private final Serializable queryConfig;

        private RssQuery(SubscriptionDefinition subscriptionDefinition) {
            url = subscriptionDefinition.getUrl();
            queryType = subscriptionDefinition.getQueryType();
            queryConfig = subscriptionDefinition.getQueryConfig();
        }

        static RssQuery from(SubscriptionDefinition subscriptionDefinition) {
            return new RssQuery(subscriptionDefinition);
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

    /**
     * Initializes the RSS importer after construction.
     * It sets up a thread pool for concurrent feed imports.
     */
    @PostConstruct
    protected final void postConstruct() {
        //
        // banner message
        //
        log.info("RSS importer constructed at {}", Instant.now());
        //
        // thread pool setup
        //
        int processorCt = Runtime.getRuntime().availableProcessors() - 1;
        processorCt = processorCt > 0 ? processorCt : 1;
        log.info("Starting RSS importer thread pool: processCount={}", processorCt);
        rssThreadPool = newFixedThreadPool(processorCt, new ThreadFactoryBuilder().setNameFormat("rss-importer-%d").build());
    }

    /**
     * Imports RSS feeds based on the provided subscription definitions and feed discovery information.
     *
     * @param subscriptionDefinitions The list of subscription definitions to import.
     * @param discoveryCache          A map containing feed discovery information.
     * @return An ImportResult object containing imported staging posts and subscription metrics.
     */
    @Override
    public final ImportResult doImport(List<SubscriptionDefinition> subscriptionDefinitions, Map<String, FeedDiscoveryInfo> discoveryCache) {
        if (configProps.getDisabled()) {
            log.warn("RSS importer is administratively disabled");
            if (configProps.getImportMockData()) {
                log.warn("RSS importer importing mock records");
                Set<StagingPost> allStagingPosts = new HashSet<>(size(subscriptionDefinitions));
                List<SubscriptionMetrics> allSubscriptionMetrics = new ArrayList<>(size(subscriptionDefinitions));
                CountDownLatch latch = new CountDownLatch(size(subscriptionDefinitions));
                subscriptionDefinitions.forEach(q -> {
                    ImportResult ir = getArticlesResponseHandler(new HashSet<>(subscriptionDefinitions), latch)
                        .onSuccess(RssMockDataGenerator.buildMockResponse(q));
                    allStagingPosts.addAll(ir.getImportSet());
                    allSubscriptionMetrics.addAll(ir.getSubscriptionMetrics());
                });
                return ImportResult.from(allStagingPosts, allSubscriptionMetrics);
            }

            return ImportResult.from(emptySet(), emptyList());
        }

        log.info("RSS importer running at {}", Instant.now());

        List<SubscriptionDefinition> supportedSubscriptionDefinitions = subscriptionDefinitions.parallelStream()
                .filter(q -> supportsQueryType(q.getQueryType()))
                .toList();
        //
        Map<SubscriptionDefinition, RssQuery> allQueryMap = supportedSubscriptionDefinitions.stream().collect(toMap(q -> q, RssQuery::from));
        //
        Map<RssQuery, Set<SubscriptionDefinition>> uniqueQueryMap = new HashMap<>(size(allQueryMap));
        //
        allQueryMap.forEach((key, value) -> uniqueQueryMap.computeIfAbsent(value, ignored -> new HashSet<>(16)).add(key));
        //
        Set<StagingPost> allStagingPosts = synchronizedSet(new HashSet<>(size(uniqueQueryMap.keySet()) << 4));
        List<SubscriptionMetrics> allSubscriptionMetrics = synchronizedList(new ArrayList<>(size(supportedSubscriptionDefinitions)));
        //
        CountDownLatch latch = new CountDownLatch(size(uniqueQueryMap.keySet()) << 1);
        log.info("RSS import latch initialized to: {}", latch.getCount());
        uniqueQueryMap.forEach((r, q) -> rssThreadPool.submit(() -> {
            if (containsKey(discoveryCache, r.getUrl())) {
                log.info("Importing RSS/ATOM feed from cache, url={}", r.getUrl());
                FeedDiscoveryInfo discoveryInfo = discoveryCache.get(r.getUrl());
                List<StagingPost> sampleEntries = discoveryInfo.getSampleEntries();
                q.forEach(subscriptionDefinition -> {
                    Set<StagingPost> importCopy = copySampleEntries(subscriptionDefinition, sampleEntries);
                    SubscriptionMetrics subscriptionMetrics = SubscriptionMetrics.from(
                            subscriptionDefinition.getId(),
                            discoveryInfo.getHttpStatusCode(),
                            discoveryInfo.getHttpStatusMessage(),
                            discoveryInfo.getRedirectFeedUrl(),
                            discoveryInfo.getRedirectHttpStatusCode(),
                            discoveryInfo.getRedirectHttpStatusMessage(),
                            new Date(),
                            null,
                            size(importCopy)
                    );
                    ImportResult importResult = ImportResult.from(importCopy, singletonList(subscriptionMetrics));
                    allStagingPosts.addAll(importResult.getImportSet());
                    allSubscriptionMetrics.addAll(importResult.getSubscriptionMetrics());
                });
                latch.countDown();
            } else if (isEmpty(discoveryCache)) {
                ImportResult importResult = performImport(r, size(q), getArticlesResponseHandler(q, latch));
                allStagingPosts.addAll(importResult.getImportSet());
                allSubscriptionMetrics.addAll(importResult.getSubscriptionMetrics());
            } else {
                latch.countDown(); // result is not in cache, yet cache is present -> skip
            }
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

        return ImportResult.from(allStagingPosts, allSubscriptionMetrics);
    }

    private static <K> boolean containsKey(Map<K, ?> map, K key) {
        return map != null && map.containsKey(key);
    }

    private static Set<StagingPost> copySampleEntries(SubscriptionDefinition subscriptionDefinition, Collection<? extends StagingPost> sampleEntries) {
        Set<StagingPost> copySet = new HashSet<>(size(sampleEntries));
        if (isNotEmpty(sampleEntries)) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                for (StagingPost stagingPost : sampleEntries) {
                    String objectSource = getObjectSource(stagingPost);
                    String postHash = computeHash(md, subscriptionDefinition.getQueueId(), objectSource);
                    StagingPost copy = StagingPost.from(stagingPost, subscriptionDefinition, postHash);
                    copySet.add(copy);
                }
            } catch (NoSuchAlgorithmException ignored) {
            }
        }
        return copySet;
    }

    private static boolean supportsQueryType(String queryType) {
        return equalsAnyIgnoreCase(queryType, SUPPORTED_QUERY_TYPES);
    }

    interface SyndFeedResponseCallback {

        ImportResult onSuccess(SyndFeedResponse response);

        ImportResult onFailure(SyndFeedException error);
    }

    private SyndFeedResponseCallback getArticlesResponseHandler(Collection<? extends SubscriptionDefinition> subscriptionDefinitions, CountDownLatch latch) {
        return new SyndFeedResponseCallback() {
            @Override
            public ImportResult onSuccess(SyndFeedResponse response) {
                Set<StagingPost> importSet = new HashSet<>(size(subscriptionDefinitions) << 4);
                List<SubscriptionMetrics> subscriptionMetrics = new ArrayList<>(size(subscriptionDefinitions));
                Date importTimestamp = new Date();
                // for ea. query,
                for (SubscriptionDefinition q : subscriptionDefinitions) {
                    // convert the syndfeed response into a stream of staging posts for that query, and send them to the success agg. queue
                    Set<StagingPost> importedArticles = importArticleResponse(q.getQueueId(), q.getId(), q.getUrl(), q.getTitle(), response.getSyndFeed(), q.getUsername(), importTimestamp);
                    importSet.addAll(importedArticles);
                    // update query metrics
                    subscriptionMetrics.add(SubscriptionMetrics.from(
                            q.getId(),
                            response.getHttpStatusCode(),
                            response.getHttpStatusMessage(),
                            response.getRedirectUrl(),
                            response.getRedirectHttpStatusCode(),
                            response.getRedirectHttpStatusMessage(),
                            importTimestamp,
                            q.getImportSchedule(),
                            size(importedArticles)
                        ));
                    log.info("Import success, username={}, queueId={}, subscriptionId={}, queryType={}, url={}, importCt={}",
                            q.getUsername(), q.getQueueId(), q.getId(), q.getQueryType(), q.getUrl(), size(importedArticles));
                }
                latch.countDown();

                return ImportResult.from(importSet, subscriptionMetrics);
            }

            @Override
            public ImportResult onFailure(SyndFeedException exception) {
                log.error("Import failure due to: {}", exception.getMessage());
                errorAggregator.offer(exception);
                List<SubscriptionMetrics> subscriptionMetrics = new ArrayList<>(size(subscriptionDefinitions));
                subscriptionDefinitions.stream()
                    .map(q -> SubscriptionMetrics.from(
                        q.getId(),
                        exception.httpStatusCode,
                        exception.httpStatusMessage,
                        exception.redirectUrl,
                        exception.redirectHttpStatusCode,
                        exception.redirectHttpStatusMessage,
                        new Date(), // import timestamp
                        q.getImportSchedule(),
                        0 // import ct
                    )).forEach(metric -> {
                        metric.setErrorType(exception.exceptionType);
                        metric.setErrorDetail(exception.getMessage());
                        subscriptionMetrics.add(metric);
                    });
                latch.countDown();

                return ImportResult.from(emptySet(), subscriptionMetrics);
            }
        };
    }

    static Set<StagingPost> importArticleResponse(Long queueId, Long subscriptionId, String url, String subscriptionTitle, SyndFeed response, String username, Date importTimestamp) {
        List<SyndEntry> responseEntries = response.getEntries();
        Set<StagingPost> stagingPosts = new HashSet<>(size(responseEntries));
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (SyndEntry e : responseEntries) {
                //
                StagingPost p = StagingPost.from(
                        RSS_ATOM_IMPORTER_ID, // importer Id
                        queueId, // feed Id
                        getImporterDesc(subscriptionTitle, url), // importer desc (feed subscription title)
                        subscriptionId, // url Id
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
                        computeHash(md, queueId, getObjectSource(e)), // post hash
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
        return getObjectSource(
                e.getTitle(),
                ofNullable(e.getDescription()).map(SyndContent::getValue).orElse(EMPTY),
                e.getLink(),
                e.getPublishedDate(),
                e.getUpdatedDate());
    }

    private static String getObjectSource(StagingPost stagingPost) {
        return getObjectSource(
                stagingPost.getPostTitle().getValue(),
                ofNullable(stagingPost.getPostDesc()).map(ContentObject::getValue).orElse(EMPTY),
                stagingPost.getPostUrl(),
                stagingPost.getPublishTimestamp(),
                stagingPost.getLastUpdatedTimestamp());
    }

    private static String getObjectSource(String title, String description, String link, Date publishTimestamp, Date lastUpdatedTimestamp) {
        JsonObject objectSrc = new JsonObject();
        objectSrc.addProperty("title", title);
        objectSrc.addProperty("description", description);
        objectSrc.addProperty("link", link);
        //
        if (publishTimestamp != null) {
            objectSrc.addProperty("published", publishTimestamp.getTime());
        }
        if (lastUpdatedTimestamp != null) {
            objectSrc.addProperty("updated", lastUpdatedTimestamp.getTime());
        }
        return objectSrc.toString();
    }

    private static PostMedia getPostMedia(SyndEntry e) {
        PostMedia pm = null;
        MediaEntryModule mm = (MediaEntryModule) e.getModule(MediaModule.URI);
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
        MediaEntryModule mm = (MediaEntryModule) e.getModule(MediaModule.URI);
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

    private static List<ContentObject> convertContentList(Collection<? extends SyndContent> contents) {
        List<ContentObject> list = null;
        if (isNotEmpty(contents)) {
            list = new ArrayList<>(size(contents));
            for (SyndContent syndContent : contents) {
                list.add(ContentObject.from(syndContent.getType(), syndContent.getValue()));
            }
        }
        return list;
    }

    private static List<PostUrl> convertLinkList(Collection<? extends SyndLink> links) {
        List<PostUrl> list = null;
        if (isNotEmpty(links)) {
            list = new ArrayList<>(size(links));
            for (SyndLink syndLink : links) {
                if (!"alternate".equals(syndLink.getRel())) {
                    PostUrl p = new PostUrl();
                    p.setTitle(syndLink.getTitle());
                    p.setType(syndLink.getType());
                    p.setHref(syndLink.getHref());
                    p.setHreflang(syndLink.getHreflang());
                    p.setRel(syndLink.getRel());
                    list.add(p);
                }
            }
        }
        return list;
    }

    private static List<PostPerson> convertPersonList(Collection<? extends SyndPerson> persons) {
        List<PostPerson> list = null;
        if (isNotEmpty(persons)) {
            list = new ArrayList<>(size(persons));
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

    private static List<String> convertCategoryList(Collection<? extends SyndCategory> categories) {
        List<String> list = null;
        if (isNotEmpty(categories)) {
            list = new ArrayList<>(size(categories));
            for (SyndCategory syndCategory : categories) {
                list.add(syndCategory.getName());
            }
        }
        return list;
    }

    private static List<PostEnclosure> convertEnclosureList(Collection<? extends SyndEnclosure> enclosures) {
        List<PostEnclosure> list = null;
        if (CollectionUtils.isNotEmpty(enclosures)) {
            list = new ArrayList<>(size(enclosures));
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

    private static String computeHash(MessageDigest md, Long queueId, Serializable objectSrc) {
        return printHexBinary(md.digest(serialize(String.format("%s:%s", queueId, objectSrc))));
    }

    /**
     * Designated query type value for RSS endpoints.
     */
    public static final String RSS = "RSS";

    /**
     * Designated query type value of ATOM endpoints.
     */
    public static final String ATOM = "ATOM";

    private static final String[] SUPPORTED_QUERY_TYPES = {
            RSS, ATOM
    };

    private static final String RSS_ATOM_IMPORTER_ID = "RssAtom";

    static ImportResult performImport(SubscriptionDefinition subscriptionDefinition, ImportResponseCallback importResponseCallback) {
        requireNonNull(subscriptionDefinition, "Subscription definition must not be null");
        requireNonNull(importResponseCallback, "Import response callback must not be null");
        return performImport(RssQuery.from(subscriptionDefinition), 1, new SyndFeedResponseCallback() {
            @Override
            public ImportResult onSuccess(SyndFeedResponse fullResponse) {
                Set<StagingPost> stagingPosts = importArticleResponse(
                        subscriptionDefinition.getQueueId(),
                        subscriptionDefinition.getId(),
                        subscriptionDefinition.getTitle(),
                        subscriptionDefinition.getUrl(),
                        fullResponse.getSyndFeed(),
                        subscriptionDefinition.getUsername(),
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

    private static ImportResult performImport(RssQuery rssQuery, int subscriberCt, SyndFeedResponseCallback syndFeedResponseCallback) {
        log.info("Importing rssQuery={}", rssQuery);

        String queryType = rssQuery.getQueryType();
        String queryText = rssQuery.getUrl();
        JsonObject queryConfigObj = ofNullable(rssQuery.getQueryConfig())
                .map(Object::toString)
                .map(s -> GSON.fromJson(s, JsonObject.class))
                .orElse(null);
        String feedUsername = getStringProperty(queryConfigObj, "username");
        String feedPassword = getStringProperty(queryConfigObj, "password");

        ImportResult importResult = null;
        //noinspection SwitchStatement
        switch (queryType) {
            case ATOM, RSS -> {
                try {
                    log.info("Fetching RSS feed from url={}", queryText);
                    String userAgent = String.format(RSS_ATOM_IMPORTER_USER_AGENT, subscriberCt);
                    importResult = syndFeedResponseCallback.onSuccess(SyndFeedService.fetch(queryText, feedUsername, feedPassword, userAgent, true));
                } catch (SyndFeedException e) {
                    importResult = syndFeedResponseCallback.onFailure(e);
                }
            }
            default -> log.error("Query type not supported by this importer: queryType={}, importerId={}", queryType, RSS_ATOM_IMPORTER_ID);
        }
        return importResult;
    }

    private static String getStringProperty(JsonObject obj, String propName) {
        JsonElement elem = obj != null && obj.has(propName) ? obj.get(propName) : null;
        return (elem != null && !elem.isJsonNull()) ? elem.getAsString() : null;
    }

    /**
     * Gets the unique identifier of the RSS importer.
     *
     * @return The importer identifier.
     */
    @Override
    public final String getImporterId() {
        return RSS_ATOM_IMPORTER_ID;
    }

    @Override
    public final String toString() {
        return "RssImporter{" +
                "configProps=" + configProps +
                ", successAggregator=" + successAggregator +
                ", errorAggregator=" + errorAggregator +
                ", rssMockDataGenerator=" + rssMockDataGenerator +
                ", syndFeedService=" + syndFeedService +
                ", rssThreadPool=" + rssThreadPool +
                '}';
    }
}
