package com.lostsidewalk.buffy.rss;

import com.lostsidewalk.buffy.discovery.FeedDiscoveryImageInfo;
import com.lostsidewalk.buffy.discovery.FeedDiscoveryInfo;
import com.lostsidewalk.buffy.discovery.FeedDiscoveryInfo.FeedDiscoveryException;
import com.lostsidewalk.buffy.post.ContentObject;
import com.rometools.rome.feed.synd.SyndCategory;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndImage;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static com.lostsidewalk.buffy.discovery.FeedDiscoveryInfo.FeedDiscoveryExceptionType.*;
import static com.lostsidewalk.buffy.discovery.FeedDiscoveryInfo.FeedDiscoveryExceptionType.HTTP_SERVER_ERROR;
import static com.lostsidewalk.buffy.rss.RssImportUtils.importArticleResponse;
import static java.lang.Math.min;
import static java.net.HttpURLConnection.*;
import static java.net.InetAddress.getByName;
import static java.net.URI.create;
import static java.util.stream.Collectors.toSet;
import static javax.xml.bind.DatatypeConverter.printHexBinary;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang3.SerializationUtils.serialize;
import static org.apache.commons.lang3.StringUtils.*;

/**
 * This class provides methods for discovering and parsing RSS feeds from URLs. It handles various HTTP status codes,
 * redirects, and feed parsing errors.
 */
@Slf4j
@Component
public class RssDiscovery {

    private static final String FEED_URL_FIELD_NAME = "feed_url";

    private static final String STATUS_MESSAGE_FIELD_NAME = "http_status_message";

    private static final String REDIRECT_URL_FIELD_NAME = "redirect_feed_url";

    private static final String REDIRECT_STATUS_MESSAGE_FIELD_NAME = "redirect_http_status_message";

    private static final String FEED_TYPE_FIELD_NAME = "feed_type";

    private static final String AUTHOR_FIELD_NAME = "author";

    private static final String COPYRIGHT_FIELD_NAME = "copyright";

    private static final String DOCS_FIELD_NAME = "docs";

    private static final String ENCODING_FIELD_NAME = "encoding";

    private static final String GENERATOR_FIELD_NAME = "generator";

    private static final String LANGUAGE_FIELD_NAME = "language";

    private static final String LINK_FIELD_NAME = "link";

    private static final String MANAGING_EDITOR_FIELD_NAME = "managing_editor";

    private static final String WEB_MASTER_FIELD_NAME = "web_master";

    private static final String URI_FIELD_NAME = "uri";

    private static final String CATEGORIES_FIELD_NAME = "categories";

    /**
     * Default constructor; initializes the object.
     */
    RssDiscovery() {
    }
    /**
     * Discover an RSS feed from the given URL and user agent.
     *
     * @param url       The URL of the feed to discover.
     * @param userAgent The user agent string to use for the HTTP request.
     * @return A `FeedDiscoveryInfo` object containing information about the discovered feed.
     * @throws FeedDiscoveryException If an error occurs during feed discovery.
     */
    @SuppressWarnings("unused")
    public static FeedDiscoveryInfo discoverUrl(String url, String userAgent) throws FeedDiscoveryException {
        return discoverUrl(url, null, null, userAgent);
    }

    /**
     * Discover an RSS feed from the given URL with optional authentication credentials and user agent.
     *
     * @param url       The URL of the feed to discover.
     * @param username  The username for HTTP authentication, or null for no authentication.
     * @param password  The password for HTTP authentication, or null for no authentication.
     * @param userAgent The user agent string to use for the HTTP request.
     * @return A `FeedDiscoveryInfo` object containing information about the discovered feed.
     * @throws FeedDiscoveryException If an error occurs during feed discovery.
     */
    public static FeedDiscoveryInfo discoverUrl(String url, String username, String password, String userAgent) throws FeedDiscoveryException {
        return discoverUrl(url, username, password, userAgent, true, 0);
    }

    /**
     * Discover an RSS feed from the given URL with optional authentication credentials, user agent, and redirect settings.
     *
     * @param url                    The URL of the feed to discover.
     * @param username               The username for HTTP authentication, or null for no authentication.
     * @param password               The password for HTTP authentication, or null for no authentication.
     * @param userAgent              The user agent string to use for the HTTP request.
     * @param followUnsecureRedirects Whether to follow unsecure (HTTP to HTTPS) redirects.
     * @param depth                  The depth of redirection (used to prevent infinite loops).
     * @return A `FeedDiscoveryInfo` object containing information about the discovered feed.
     * @throws FeedDiscoveryException If an error occurs during feed discovery.
     */
    static FeedDiscoveryInfo discoverUrl(String url, String username, String password, String userAgent, boolean followUnsecureRedirects, int depth) throws FeedDiscoveryException {
        log.debug("Performing feed discovery for URL={}", url);
        Integer statusCode = null;
        String statusMessage = null;
        String redirectUrl = null;
        Integer redirectStatusCode = null;
        String redirectStatusMessage = null;
        try {
            // setup the initial connection
            HttpURLConnection feedConnection = openFeedConnection(url);
            // add authentication, if any
            boolean hasAuthenticationHeaders = addAuthenticator(feedConnection, username, password);
            // add the UA header
            feedConnection.setRequestProperty("User-Agent", userAgent);
            // add the AE header
            feedConnection.setRequestProperty("Accept-Encoding", "gzip");
            // add the cache control header
            addCacheControlHeader(feedConnection);
            // get the (initial) status response
            statusCode = getStatusCode(feedConnection);
            // get the (initial) status message
            statusMessage = getStatusMessage(feedConnection);
            // if this is a redirect...
            if (isRedirect(statusCode)) {
                // get the redirect location URL
                redirectUrl = feedConnection.getHeaderField("Location");
                // (check to broken redirect setups, e.g. http://www.virtualr.net/feed)
                if (depth > 2) {
                    throw new FeedDiscoveryException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, null, OTHER); // (caught in an idiot loop of some sort)
                }
                // check for unsecure redirect
                boolean isUnsecure = "http".equalsIgnoreCase(feedConnection.getURL().getProtocol());
                // determine if we're being redirected within the same domain by comparing the resolved canonical name of the original URL to resolved canonical name of the redirect URL
                boolean isSameDomain = getByName(feedConnection.getURL().getHost()).getCanonicalHostName().equals(getByName(new URL(redirectUrl).getHost()).getCanonicalHostName());
                // if this is an unsecure redirect to a new domain *and we have auth*, bail
                // also, if this is an unsecure redirect (no auth), but we have been instructed *not to trust such redirects to other domains*, bail
                if ((isUnsecure && !isSameDomain) && (hasAuthenticationHeaders || !followUnsecureRedirects)) {
                    throw new FeedDiscoveryException(url, statusCode, statusMessage, redirectUrl, null, null, UNSECURE_REDIRECT); // (http URL got redirected to other domain)
                }
                // open the redirect connection
                feedConnection = openFeedConnection(redirectUrl);
                // add authentication to the redirect, if any
                addAuthenticator(feedConnection, username, password);
                // add the UA header to the redirect
                feedConnection.setRequestProperty("User-Agent", userAgent);
                // add the AE header to the redirect
                feedConnection.setRequestProperty("Accept-Encoding", "gzip");
                // get the redirect status response
                redirectStatusCode = getStatusCode(feedConnection);
                // get the redirect status message
                redirectStatusMessage = getStatusMessage(feedConnection);
                // if *this* is also a redirect...
                if (isRedirect(redirectStatusCode)) {
                    // TOO_MANY_REDIRECTS
                    throw new FeedDiscoveryException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage, TOO_MANY_REDIRECTS); // (redirect got redirected)
                }
                // if the redirect ends in CLIENT ERROR (response status 4xx)
                if (isClientError(redirectStatusCode)) {
                    // DISCOVERY_CLIENT_ERROR
                    throw new FeedDiscoveryException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage, HTTP_CLIENT_ERROR); // (client error status on redirect)
                    // DISCOVERY_SERVER_ERROR
                } else if (isServerError(redirectStatusCode)) {
                    throw new FeedDiscoveryException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage, HTTP_SERVER_ERROR); // (server error status on redirect)
                }
            } else if (isClientError(statusCode)) { // otherwise, if this is a client error (4xx)
                // CLIENT_ERROR
                throw new FeedDiscoveryException(url, statusCode, statusMessage, null, null, null, HTTP_CLIENT_ERROR);
            } else if (isServerError(statusCode)) { // otherwise, if this is a server error (5xx)
                // SERVER_ERROR
                throw new FeedDiscoveryException(url, statusCode, statusMessage, null, null, null, HTTP_SERVER_ERROR);
            }  // otherwise (this is a success response)

            boolean isUrlUpgradable = false;
            try {
                // non-redirected HTTP call which resulted in success
                if (isSuccess(statusCode) && "http".equalsIgnoreCase(feedConnection.getURL().getProtocol())) {
                    // attempt HTTPS
                    isUrlUpgradable = isUrlUpgradable(url, username, password, userAgent, depth);
                    // redirected HTTP call which resulted in success
                } else {
                    boolean isRedirect = redirectStatusCode != null;
                    if (isRedirect) {
                        boolean isRedirectSuccess = isSuccess(redirectStatusCode);
                        boolean isRedirectHttp = "http".equalsIgnoreCase(create(redirectUrl).toURL().getProtocol());
                        if (isRedirectSuccess && isRedirectHttp) {
                            // attempt HTTPS
                            isUrlUpgradable = isUrlUpgradable(redirectUrl, username, password, userAgent, depth);
                        }
                    }
                }
            } catch (MalformedURLException ignored) {
                // not upgradable
            }

            try (InputStream is = feedConnection.getInputStream()) {
                InputStream toRead;
                if (containsIgnoreCase(feedConnection.getContentEncoding(), "gzip")) {
                    toRead = new GZIPInputStream(is);
                } else {
                    toRead = is;
                }
                byte[] allBytes = toRead.readAllBytes();
                toRead.close();
                ByteArrayInputStream bais = new ByteArrayInputStream(allBytes);
                XmlReader xmlReader = new XmlReader(bais);
                SyndFeedInput input = new SyndFeedInput();
                input.setAllowDoctypes(true);
                SyndFeed feed = input.build(xmlReader);

                return FeedDiscoveryInfo.from(
                        trimToLength(FEED_URL_FIELD_NAME, url, 1024),
                        statusCode, // http status code
                        trimToLength(STATUS_MESSAGE_FIELD_NAME, statusMessage, 512), // http status message
                        trimToLength(REDIRECT_URL_FIELD_NAME, redirectUrl, 1024), // redirect url
                        redirectStatusCode, // redirect status code
                        trimToLength(REDIRECT_STATUS_MESSAGE_FIELD_NAME, redirectStatusMessage, 512), // redirect status message
                        convertToContentObject(feed.getTitleEx()),
                        convertToContentObject(feed.getDescriptionEx()),
                        trimToLength(FEED_TYPE_FIELD_NAME, feed.getFeedType(), 64),
                        trimToLength(AUTHOR_FIELD_NAME, feed.getAuthor(), 256),
                        trimToLength(COPYRIGHT_FIELD_NAME, feed.getCopyright(), 1024),
                        trimToLength(DOCS_FIELD_NAME, feed.getDocs(), 1024),
                        trimToLength(ENCODING_FIELD_NAME, feed.getEncoding(), 64),
                        trimToLength(GENERATOR_FIELD_NAME, feed.getGenerator(), 512),
                        buildFeedImage(feed.getImage()),
                        buildFeedImage(feed.getIcon()),
                        trimToLength(LANGUAGE_FIELD_NAME, feed.getLanguage(), 16),
                        trimToLength(LINK_FIELD_NAME, feed.getLink(), 1024),
                        trimToLength(MANAGING_EDITOR_FIELD_NAME, feed.getManagingEditor(), 256),
                        feed.getPublishedDate(),
                        feed.getStyleSheet(),
                        isNotEmpty(feed.getSupportedFeedTypes()) ? new ArrayList<>(feed.getSupportedFeedTypes()) : new ArrayList<>(0),
                        trimToLength(WEB_MASTER_FIELD_NAME, feed.getWebMaster(), 256),
                        trimToLength(URI_FIELD_NAME, feed.getUri(), 1024),
                        new ArrayList<>(firstFiveCategories(feed.getCategories())
                                .map(SyndCategory::getName)
                                .map(name -> trimToLength(CATEGORIES_FIELD_NAME, name, 256))
                                .collect(toSet())),
                        new ArrayList<>(importArticleResponse(null, null, url, null, feed, username, new Date())),
                        // is upgradable
                        isUrlUpgradable
                );
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") FeedException | IOException | IllegalArgumentException e) {
            throw new FeedDiscoveryException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage, e);
        }
    }

    @SuppressWarnings("OverlyBroadThrowsClause") // MalformedURLException extends IOException
    private static HttpURLConnection openFeedConnection(String url) throws IOException {
        URL feedUrl = new URL(url);
        return (HttpURLConnection) feedUrl.openConnection();
    }

    private static boolean addAuthenticator(HttpURLConnection feedConnection, String username, String password) {
        if (username != null && password != null) {
            feedConnection.setAuthenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
            return true;
        }

        return false;
    }

    private static void addCacheControlHeader(HttpURLConnection feedConnection) {
        feedConnection.setRequestProperty("Cache-Control", "no-cache");
    }

    private static int getStatusCode(HttpURLConnection feedConnection) throws IOException {
        feedConnection.setInstanceFollowRedirects(true);
        return feedConnection.getResponseCode();
    }

    private static String getStatusMessage(HttpURLConnection feedConnection) throws IOException {
        return feedConnection.getResponseMessage();
    }

    /**
     * Checks if the given HTTP status code represents a successful response (HTTP 200 OK).
     *
     * @param statusCode The HTTP status code to check.
     * @return True if the status code indicates success, otherwise false.
     */
    public static boolean isSuccess(int statusCode) {
        return statusCode == HTTP_OK;
    }

    /**
     * Checks if the given HTTP status code represents a redirect response (HTTP 3xx).
     *
     * @param statusCode The HTTP status code to check.
     * @return True if the status code indicates a redirect, otherwise false.
     */
    public static boolean isRedirect(int statusCode) {
        return (isTemporaryRedirect(statusCode) || isPermanentRedirect(statusCode)
                || statusCode == HTTP_SEE_OTHER);
    }

    /**
     * Checks if the given HTTP status code represents a temporary redirect (HTTP 302 Found).
     *
     * @param statusCode The HTTP status code to check.
     * @return True if the status code indicates a temporary redirect, otherwise false.
     */
    public static boolean isTemporaryRedirect(int statusCode) {
        return statusCode == HTTP_MOVED_TEMP;
    }

    /**
     * Checks if the given HTTP status code represents a permanent redirect (HTTP 301 Moved Permanently).
     *
     * @param statusCode The HTTP status code to check.
     * @return True if the status code indicates a permanent redirect, otherwise false.
     */
    public static boolean isPermanentRedirect(int statusCode) {
        return statusCode == HTTP_MOVED_PERM;
    }

    /**
     * Checks if the given HTTP status code represents a client error response (HTTP 4xx).
     *
     * @param statusCode The HTTP status code to check.
     * @return True if the status code indicates a client error, otherwise false.
     */
    public static boolean isClientError(int statusCode) {
        return statusCode >= HTTP_BAD_REQUEST && statusCode < HTTP_INTERNAL_ERROR;
    }

    /**
     * Checks if the given HTTP status code represents a server error response (HTTP 5xx).
     *
     * @param statusCode The HTTP status code to check.
     * @return True if the status code indicates a server error, otherwise false.
     */
    public static boolean isServerError(int statusCode) {
        return statusCode >= HTTP_INTERNAL_ERROR;
    }

    private static boolean isUrlUpgradable(String url, String username, String password, String userAgent, int depth) {
        if (url.startsWith("http") && !url.startsWith("https")) {
            String newUrl = replaceOnce(url, "http", "https");
            try {
                FeedDiscoveryInfo newFeedDiscoveryInfo = discoverUrl(newUrl, username, password, userAgent, false, depth + 1);
                if (
                    // either initial discovery or redirected discovery produced an HTTP 200
                        (isSuccess(newFeedDiscoveryInfo.getHttpStatusCode()) || isSuccess(newFeedDiscoveryInfo.getRedirectHttpStatusCode()))
                                // and the feed parsed without an exception
                                && newFeedDiscoveryInfo.getErrorType() == null)
                {
                    return true;
                }
            } catch (FeedDiscoveryException ignored) {
                // not upgradable due to e
            }
        }

        return false;
    }

    private static String trimToLength(String fieldName, String str, int len) {
        if (str == null) {
            return null;
        }
        String t = trim(str);
        if (t.length() > len) {
            log.error("Field length overrun, fieldName={}, len={}", fieldName, len);
        }
        return t.substring(0, min(len, t.length()));
    }

    private static FeedDiscoveryImageInfo buildFeedImage(SyndImage img) {
        if (img == null) {
            return null;
        }
        String url = img.getUrl();
        if (isNotBlank(url)) {
            MessageDigest md = null;
            try {
                md = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ignored) {
                // ignored
            }
            String transportIdent = computeThumbnailHash(md, url);

            return FeedDiscoveryImageInfo.from(
                    img.getTitle(),
                    img.getDescription(),
                    img.getHeight(),
                    img.getWidth(),
                    img.getLink(),
                    transportIdent,
                    img.getUrl());
        }

        return null;
    }

    private static ContentObject convertToContentObject(SyndContent syndContent) {
        if (syndContent == null) {
            return null;
        }
        return ContentObject.from(randomAlphanumeric(8), syndContent.getType(), syndContent.getValue());
    }

    private static String computeThumbnailHash(MessageDigest md, String feedImgUrl) {
        return isNotBlank(feedImgUrl) ? printHexBinary(md.digest(serialize(feedImgUrl))) : null;
    }

    private static Stream<SyndCategory> firstFiveCategories(List<SyndCategory> categories) {
        return categories == null ? Stream.of() : categories.subList(0, min(categories.size(), 5)).stream();
    }
}
