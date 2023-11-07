package com.lostsidewalk.buffy.rss;

import com.google.gson.JsonObject;
import com.lostsidewalk.buffy.post.*;
import com.rometools.modules.itunes.ITunes;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.MediaModule;
import com.rometools.modules.mediarss.types.*;
import com.rometools.rome.feed.synd.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.Serializable;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static java.util.Optional.ofNullable;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static org.apache.commons.collections4.CollectionUtils.size;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang3.SerializationUtils.serialize;
import static org.apache.commons.lang3.StringUtils.*;

@Slf4j
class RssImportUtils {

    static final String RSS_ATOM_IMPORTER_ID = "RssAtom";

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
                        ofNullable(e.getTitleEx()).map(RssImportUtils::convertContentObject).orElse(null), // post title
                        // HERE: description_type
                        ofNullable(e.getDescription()).map(RssImportUtils::convertContentObject).orElse(null),
                        // HERE: List<String> postContents now needs to List<String, String> so that content type can ride along
                        ofNullable(e.getContents()).map(RssImportUtils::convertContentList).orElse(null), // post contents
                        getPostMedia(e), // post media
                        getPostITunes(e), // post iTunes
                        trim(e.getLink()), // post URL
                        ofNullable(e.getLinks()).map(RssImportUtils::convertLinkList).orElse(null), // post URLs
                        getThumbnailUrl(e), // post img URL
                        importTimestamp, // import timestamp
                        computeHash(md, queueId, getObjectSource(e)), // post hash
                        username, // username
                        trim(e.getComments()), // post comments
                        response.getCopyright(), // post rights
                        ofNullable(e.getContributors()).map(RssImportUtils::convertPersonList).orElse(null), // contributors
                        ofNullable(getAuthors(e)).map(RssImportUtils::convertPersonList).orElse(null), // authors
                        ofNullable(e.getCategories()).map(RssImportUtils::convertCategoryList).orElse(null), // post categories
                        e.getPublishedDate(), // publish timestamp
                        null, // expiration timestamp (none)
                        ofNullable(e.getEnclosures()).map(RssImportUtils::convertEnclosureList).orElse(null), // enclosures
                        e.getUpdatedDate() // updated timestamp
                );
                stagingPosts.add(p);
            }
        } catch (NoSuchAlgorithmException ignored) {}

        return stagingPosts;
    }

    static String getImporterDesc(String desc, String defaultDesc) {
        return trimToEmpty(defaultString(desc, defaultDesc));
    }

    static String getObjectSource(SyndEntry e) {
        return getObjectSource(
                e.getTitle(),
                ofNullable(e.getDescription()).map(SyndContent::getValue).orElse(EMPTY),
                e.getLink(),
                e.getPublishedDate(),
                e.getUpdatedDate());
    }

    static String getObjectSource(StagingPost stagingPost) {
        return getObjectSource(
                stagingPost.getPostTitle().getValue(),
                ofNullable(stagingPost.getPostDesc()).map(ContentObject::getValue).orElse(EMPTY),
                stagingPost.getPostUrl(),
                stagingPost.getPublishTimestamp(),
                stagingPost.getLastUpdatedTimestamp());
    }

    static String getObjectSource(String title, String description, String link, Date publishTimestamp, Date lastUpdatedTimestamp) {
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

    static PostMedia getPostMedia(SyndEntry e) {
        PostMedia pm = null;
        MediaEntryModule mm = (MediaEntryModule) e.getModule(MediaModule.URI);
        if (mm != null) {
            pm = PostMedia.from(mm);
        }
        return pm;
    }

    static PostITunes getPostITunes(SyndEntry e) {
        PostITunes pi = null;
        ITunes im = (ITunes) e.getModule(ITunes.URI);
        if (im != null) {
            pi = PostITunes.from(im);
        }
        return pi;
    }

    static String getThumbnailUrl(SyndEntry e) {
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

    static List<SyndPerson> getAuthors(SyndEntry e) {
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

    static String getThumbnailFromMetadata(Metadata md) {
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

    static ContentObject convertContentObject(SyndContent content) {
        ContentObject contentObject = null;
        if (content != null) {
            contentObject = ContentObject.from(randomAlphanumeric(8), content.getType(), content.getValue());
        }
        return contentObject;
    }

    static List<ContentObject> convertContentList(Collection<? extends SyndContent> contents) {
        List<ContentObject> list = null;
        if (CollectionUtils.isNotEmpty(contents)) {
            list = new ArrayList<>(size(contents));
            for (SyndContent syndContent : contents) {
                list.add(ContentObject.from(randomAlphanumeric(8), syndContent.getType(), syndContent.getValue()));
            }
        }
        return list;
    }

    private static List<PostUrl> convertLinkList(Collection<? extends SyndLink> links) {
        List<PostUrl> list = null;
        if (CollectionUtils.isNotEmpty(links)) {
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

    static List<PostPerson> convertPersonList(Collection<? extends SyndPerson> persons) {
        List<PostPerson> list = null;
        if (CollectionUtils.isNotEmpty(persons)) {
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

    static List<String> convertCategoryList(Collection<? extends SyndCategory> categories) {
        List<String> list = null;
        if (CollectionUtils.isNotEmpty(categories)) {
            list = new ArrayList<>(size(categories));
            for (SyndCategory syndCategory : categories) {
                list.add(syndCategory.getName());
            }
        }
        return list;
    }

    static List<PostEnclosure> convertEnclosureList(Collection<? extends SyndEnclosure> enclosures) {
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

    static String computeHash(MessageDigest md, Long queueId, Serializable objectSrc) {
        return printHexBinary(md.digest(serialize(String.format("%s:%s", queueId, objectSrc))));
    }
}
