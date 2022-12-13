package com.lostsidewalk.buffy.rss;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lostsidewalk.buffy.Importer;
import com.lostsidewalk.buffy.post.StagingPost;
import com.lostsidewalk.buffy.query.QueryDefinition;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedException;
import com.lostsidewalk.buffy.rss.syndfeed.SyndFeedService;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.types.*;
import com.rometools.rome.feed.synd.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.apache.commons.lang3.SerializationUtils.serialize;
import static org.apache.commons.lang3.StringUtils.*;

@Slf4j
@Component
public class RssImporter implements Importer {

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

    @PostConstruct
    public void postConstruct() {
        //
        // banner message
        //
        log.info("RSS importer constructed at {}", Instant.now());
    }

    @Override
    public void doImport(List<QueryDefinition> queryDefinitions) {
        if (this.configProps.getDisabled()) {
            log.warn("RSS importer is administratively disabled");
            if (this.configProps.getImportMockData()) {
                log.warn("RSS importer importing mock records");
                queryDefinitions.forEach(q ->
                        getArticlesResponseHandler(q.getFeedIdent(), q.getQueryText(), q.getQueryType(), q.getUsername())
                            .onSuccess(rssMockDataGenerator.buildMockResponse(q)));
            }
            return;
        }

        log.info("RSS importer running at {}", Instant.now());
        queryDefinitions.stream()
                .filter(q -> supportsQueryType(q.getQueryType()))
                .forEach(q ->
                    this.performImport(q, getArticlesResponseHandler(q.getFeedIdent(), String.format("[query: %s]", q.getQueryText()), q.getQueryType(), q.getUsername()))
                );

        log.info("RSS importer finished at {}", Instant.now());
    }

    private boolean supportsQueryType(String queryType) {
        return equalsAnyIgnoreCase(queryType, SUPPORTED_QUERY_TYPES);
    }

    interface SyndFeedResponseCallback {

        void onSuccess(SyndFeed response);

        void onFailure(Throwable throwable);
    }

    private SyndFeedResponseCallback getArticlesResponseHandler(String feedIdent, String query, String queryType, String username) {
        return new SyndFeedResponseCallback() {
            @Override
            public void onSuccess(SyndFeed response) {
                try {
                    AtomicInteger importCt = new AtomicInteger(0);
                    importArticleResponse(feedIdent, query, response, username).forEach(s -> {
                        log.debug("Adding post hash={} to queue for feedIdent={}, username={}", s.getPostHash(), feedIdent, username);
                        successAggregator.offer(s);
                        importCt.getAndIncrement();
                    });
                    log.info("Import success, feedIdent={}, username={}, queryType={}, queryText={}, importCt={}", feedIdent, username, queryType, query, importCt.intValue());
                } catch (Exception e) {
                    log.error("Import failure, feedIdent={}, username={}, queryType={}, queryText={} due to: {}", feedIdent, username, queryType, query, e.getMessage());
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                errorAggregator.offer(throwable);
            }
        };
    }

    private static Set<StagingPost> importArticleResponse(String feedIdent, String query, SyndFeed response, String username) throws NoSuchAlgorithmException {
        Set<StagingPost> stagingPosts = new HashSet<>();
        MessageDigest md = MessageDigest.getInstance("MD5");
        for (SyndEntry e : response.getEntries()) {
            JsonObject objectSrc = new JsonObject(); // GSON.toJsonTree(e);
            objectSrc.addProperty("title", e.getTitle());
            objectSrc.addProperty("description", ofNullable(e.getDescription()).map(SyndContent::getValue).orElse(EMPTY));
            objectSrc.addProperty("link", e.getLink());
            //
            String authorName = e.getAuthor();
            String authorEmail = null;
            List<SyndPerson> authors = e.getAuthors();
            if (isNotEmpty(authors)) {
                SyndPerson author = authors.get(0);
                authorEmail = author == null ? null : author.getEmail();
            }
            //
            List<SyndPerson> contributors = e.getContributors();
            SyndPerson contributor = isNotEmpty(contributors) ? contributors.get(0) : null;
            //
            List<SyndCategory> categories = e.getCategories();
            SyndCategory category = isNotEmpty(categories) ? categories.get(0) : null;
            //
            List<SyndEnclosure> enclosures = e.getEnclosures();
            SyndEnclosure enclosure = isNotEmpty(enclosures) ? enclosures.get(0) : null;
            //
            StagingPost p = StagingPost.from(
                    RSS_ATOM_IMPORTER_ID, // importer Id
                    feedIdent, // feed ident
                    getImporterDesc(query), // importer desc
                    objectSrc.toString(), // source
                    ofNullable(e.getSource()).map(SyndFeed::getTitle).map(StringUtils::trim).orElse(null), // source name
                    ofNullable(e.getSource()).map(SyndFeed::getLink).map(StringUtils::trim).orElse(null), // source url
                    trim(e.getTitle()), // post title
                    ofNullable(e.getDescription()).map(SyndContent::getValue).map(StringUtils::trim).orElse(getPostContents(e)), // post description
                    trim(e.getLink()), // post URL
                    getThumbnailUrl(e), // post img URL
                    new Date(), // import timestamp
                    computeHash(md, feedIdent, objectSrc), // post hash
                    username, // username
                    trim(e.getComments()), // post comment
                    false, // isPublished
                    null, // post rights // TODO: figure this out
                    null, // xml base // TODO: figure this out
                    ofNullable(contributor).map(SyndPerson::getName).map(StringUtils::trim).orElse(null), // contributor name
                    ofNullable(contributor).map(SyndPerson::getEmail).map(StringUtils::trim).orElse(null), // contributor email
                    authorName,
                    authorEmail,
                    ofNullable(category).map(SyndCategory::getName).map(StringUtils::trim).orElse(null), // post category
                    e.getPublishedDate(), // publish timestamp
                    null, // expiration timestamp // TODO: figure this out
                    ofNullable(enclosure).map(SyndEnclosure::getUrl).map(StringUtils::trim).orElse(null),  // enclosure url
                    e.getUpdatedDate() // updated timestamp 
            );
            stagingPosts.add(p);
        }

        return stagingPosts;
    }

    private static String getImporterDesc(String query) {
        return String.format("[query=%s]", query);
    }

    private static String getPostContents(SyndEntry e) {
        List<SyndContent> contents = e.getContents();
        return isNotEmpty(contents) ? contents.get(0).getValue() : EMPTY;
    }

    private static String getThumbnailUrl(SyndEntry e) {
        for (com.rometools.rome.feed.module.Module m : e.getModules()) {
            if (m instanceof MediaEntryModule mm) {
                Metadata md = mm.getMetadata();
                if (md != null) {
                    Thumbnail[] thumbnails = md.getThumbnail();
                    if (isNotEmpty(thumbnails)) {
                        URI uri = thumbnails[0].getUrl();
                        if (uri != null) {
                            return uri.toString();
                        }
                    } else {
                        MediaGroup[] mediaGroups = mm.getMediaGroups();
                        if (isNotEmpty(mediaGroups)) {
                            MediaContent[] mediaContents = mediaGroups[0].getContents();
                            if (isNotEmpty(mediaContents)) {
                                Reference ref = mediaContents[0].getReference();
                                return ref == null ? null : ref.toString();
                            }
                        } else {
                            MediaContent[] mediaContents = mm.getMediaContents();
                            if (isNotEmpty(mediaContents)) {
                                Reference ref = mediaContents[0].getReference();
                                return ref == null ? null : ref.toString();
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private static String computeHash(MessageDigest md, String feedIdent, JsonElement objectSrc) {
        return printHexBinary(md.digest(serialize(String.format("%s:%s", feedIdent, objectSrc.toString()))));
    }

    public static final String RSS = "RSS";

    public static final String ATOM = "ATOM";

    private static final String[] SUPPORTED_QUERY_TYPES = new String[] {
            RSS, ATOM
    };

    private static final String RSS_ATOM_IMPORTER_ID = "RssAtom";

    @Override
    public ImporterMetrics performImport(QueryDefinition queryDefinition, ImportResponseCallback importResponseCallback) {
        requireNonNull(queryDefinition, "Query definition must not be null");
        requireNonNull(importResponseCallback, "Import response callback must not be null");
        return this.performImport(queryDefinition, new SyndFeedResponseCallback() {
            @Override
            public void onSuccess(SyndFeed response) {
                try {
                    Set<StagingPost> stagingPosts = importArticleResponse(queryDefinition.getFeedIdent(), queryDefinition.getQueryText(), response, queryDefinition.getUsername());
                    importResponseCallback.onSuccess(stagingPosts);
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                importResponseCallback.onFailure(throwable);
            }
        });
    }

    private ImporterMetrics performImport(QueryDefinition queryDefinition, SyndFeedResponseCallback syndFeedResponseCallback) {
        int successCt = 0, errorCt = 0;
        String username = queryDefinition.getUsername();
        String feedIdent = queryDefinition.getFeedIdent();
        log.info("Importing feedIdent={}, username={}, queryDefinition={}", feedIdent, username, queryDefinition);

        String queryType = queryDefinition.getQueryType();
        String q = queryDefinition.getQueryText();
        switch (queryType) {
            case ATOM, RSS -> {
                try {
                    log.info("Fetching RSS feed from url={}", q);
                    syndFeedResponseCallback.onSuccess(syndFeedService.fetch(q));
                    successCt++;
                } catch (SyndFeedException e) {
                    syndFeedResponseCallback.onFailure(e);
                    errorCt++;
                }
            }
            default -> log.error("Query type not supported by this importer: queryType={}, importerId={}", queryType, getImporterId());
        }
        return new ImporterMetrics(successCt, errorCt);
    }

    @Override
    public String getImporterId() {
        return RSS_ATOM_IMPORTER_ID;
    }
}
