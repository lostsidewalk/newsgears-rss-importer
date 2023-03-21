package com.lostsidewalk.buffy.rss.syndfeed;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import static com.lostsidewalk.buffy.query.QueryMetrics.QueryExceptionType.*;
import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;

@Service
@SuppressWarnings("unused")
public class SyndFeedService {

    @Data
    public static class SyndFeedResponse {

        final SyndFeed syndFeed;
        final int httpStatusCode;
        final String httpStatusMessage;
        final String redirectUrl;
        final Integer redirectHttpStatusCode;
        final String redirectHttpStatusMessage;

        private SyndFeedResponse(SyndFeed syndFeed, int httpStatusCode, String httpStatusMessage, String redirectUrl, Integer redirectHttpStatusCode, String redirectHttpStatusMessage) {
            this.syndFeed = syndFeed;
            this.httpStatusCode = httpStatusCode;
            this.httpStatusMessage = httpStatusMessage;
            this.redirectUrl = redirectUrl;
            this.redirectHttpStatusCode = redirectHttpStatusCode;
            this.redirectHttpStatusMessage = redirectHttpStatusMessage;
        }

        public static SyndFeedResponse from(SyndFeed syndFeed, int httpStatusCode, String httpStatusMessage, String redirectUrl, Integer redirectHttpStatusCode, String redirectHttpStatusMessage) {
            return new SyndFeedResponse(syndFeed, httpStatusCode, httpStatusMessage, redirectUrl, redirectHttpStatusCode, redirectHttpStatusMessage);
        }

        public static SyndFeedResponse from(SyndFeed syndFeed, int httpStatusCode, String httpStatusMessage) {
            return new SyndFeedResponse(syndFeed, httpStatusCode, httpStatusMessage, null, null, null);
        }
    }

    public SyndFeedResponse fetch(String url, String username, String password, String userAgent, boolean followUnsecureRedirects) throws SyndFeedException {
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
            addUserAgentHeader(feedConnection, userAgent);
            // add the cache control header
            addCacheControlHeader(feedConnection);
            // get the (initial) status response
            statusCode = getStatusCode(feedConnection);
            // get the (initial) status message
            statusMessage = getStatusMessage(feedConnection);
            // if this is a redirect...
            if (isPermanentRedirect(statusCode)) {
                throw new SyndFeedException(url, statusCode, statusMessage, null, redirectStatusCode, null, PERMANENTLY_REDIRECTED);
            }
            if (isTemporaryRedirect(statusCode)) {
                // get the redirect location URL
                redirectUrl = feedConnection.getHeaderField("Location");
                // check for unsecure redirect
                boolean isUnsecureRedirect = "http".equalsIgnoreCase(feedConnection.getURL().getProtocol());
                // if this is an unsecure redirect (no auth), but we have been instructed not to trust such redirects, bail
                if (isUnsecureRedirect && (hasAuthenticationHeaders || !followUnsecureRedirects)) {
                    throw new SyndFeedException(url, statusCode, statusMessage, redirectUrl, null, null, UNSECURE_REDIRECT); // (http URL got redirected)
                }
                // open the redirect connection
                feedConnection = openFeedConnection(redirectUrl);
                // add authentication to the redirect, if any
                addAuthenticator(feedConnection, username, password);
                // add the UA header to the redirect
                addUserAgentHeader(feedConnection, userAgent);
                // get the redirect status response
                redirectStatusCode = getStatusCode(feedConnection);
                // get the redirect status message
                redirectStatusMessage = getStatusMessage(feedConnection);
                // if *this* is also a redirect...
                if (isRedirect(redirectStatusCode)) {
                    // TOO_MANY_REDIRECTS
                    throw new SyndFeedException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage, TOO_MANY_REDIRECTS); // (redirect got redirected)
                }
                // if the redirect ends in CLIENT ERROR (response status 4xx)
                if (isClientError(redirectStatusCode)) {
                    // DISCOVERY_CLIENT_ERROR
                    throw new SyndFeedException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage, HTTP_CLIENT_ERROR); // (client error status on redirect)
                    // DISCOVERY_SERVER_ERROR
                } else if (isServerError(redirectStatusCode)) {
                    throw new SyndFeedException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage, HTTP_SERVER_ERROR); // (server error status on redirect)
                }
            } else if (isClientError(statusCode)) { // otherwise, if this is a client error (4xx)
                // CLIENT_ERROR
                throw new SyndFeedException(url, statusCode, statusMessage, null, null, null, HTTP_CLIENT_ERROR);
            } else if (isServerError(statusCode)) { // otherwise, if this is a server error (5xx)
                // SERVER_ERROR
                throw new SyndFeedException(url, statusCode, statusMessage, null, null, null, HTTP_SERVER_ERROR);
            }  // otherwise (this is a success response)

            try (InputStream is = feedConnection.getInputStream()) {
                InputStream toRead;
                if (containsIgnoreCase(feedConnection.getContentEncoding(), "gzip")) {
                    toRead = new GZIPInputStream(is);
                } else {
                    toRead = is;
                }
                byte[] allBytes = toRead.readAllBytes();
                ByteArrayInputStream bais = new ByteArrayInputStream(allBytes);
                XmlReader xmlReader = new XmlReader(bais);
                SyndFeedInput input = new SyndFeedInput();
                input.setAllowDoctypes(true);
                SyndFeed feed = input.build(xmlReader);
                return SyndFeedResponse.from(feed, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage);
            }
        } catch (Exception e) {
            throw new SyndFeedException(url, statusCode, statusMessage, redirectUrl, redirectStatusCode, redirectStatusMessage, e);
        }
    }

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

    private static void addUserAgentHeader(HttpURLConnection feedConnection, String userAgent) {
        feedConnection.setRequestProperty("User-Agent", userAgent);
    }

    private static void addCacheControlHeader(HttpURLConnection feedConnection) {
        feedConnection.setRequestProperty("Cache-Control", "no-cache");
    }

    private static int getStatusCode(HttpURLConnection feedConnection) throws IOException {
        feedConnection.setInstanceFollowRedirects(true);
        return feedConnection.getResponseCode();
    }

    public static boolean isSuccess(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_OK;
    }

    public static boolean isRedirect(int statusCode) {
        return (isTemporaryRedirect(statusCode) || isPermanentRedirect(statusCode)
                || statusCode == HttpURLConnection.HTTP_SEE_OTHER);
    }

    public static boolean isTemporaryRedirect(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_MOVED_TEMP;
    }

    public static boolean isPermanentRedirect(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_MOVED_PERM;
    }

    public static boolean isClientError(int statusCode) {
        return statusCode >= HttpURLConnection.HTTP_BAD_REQUEST && statusCode < HttpURLConnection.HTTP_INTERNAL_ERROR;
    }

    public static boolean isServerError(int statusCode) {
        return statusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR;
    }

    private static String getStatusMessage(HttpURLConnection feedConnection) throws IOException {
        return feedConnection.getResponseMessage();
    }
}
