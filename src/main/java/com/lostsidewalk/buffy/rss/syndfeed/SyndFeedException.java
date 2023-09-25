package com.lostsidewalk.buffy.rss.syndfeed;

import com.lostsidewalk.buffy.subscription.SubscriptionMetrics.QueryExceptionType;
import com.rometools.rome.io.ParsingFeedException;

import javax.net.ssl.SSLHandshakeException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.lostsidewalk.buffy.subscription.SubscriptionMetrics.QueryExceptionType.*;

/**
 * Custom exception class for handling errors related to syndicated feed fetching and processing.
 */
public class SyndFeedException extends Exception {

    /**
     * The URL of the feed that caused the exception.
     */
    public final String feedUrl;

    /**
     * The HTTP status code associated with the exception.
     */
    public final Integer httpStatusCode;

    /**
     * The HTTP status message associated with the exception.
     */
    public final String httpStatusMessage;

    /**
     * The URL to which the feed was redirected if applicable, otherwise null.
     */
    public final String redirectUrl;

    /**
     * The HTTP status code of the redirect if applicable, otherwise null.
     */
    public final Integer redirectHttpStatusCode;

    /**
     * The HTTP status message of the redirect if applicable, otherwise null.
     */
    public final String redirectHttpStatusMessage;

    /**
     * The type of exception, indicating the specific error encountered during feed processing.
     */
    public final QueryExceptionType exceptionType;

    /**
     * Constructs a `SyndFeedException` with detailed information about the exception.
     *
     * @param feedUrl The URL of the feed.
     * @param httpStatusCode The HTTP status code.
     * @param httpStatusMessage The HTTP status message.
     * @param redirectUrl The URL to which the feed was redirected (if applicable), otherwise null.
     * @param redirectHttpStatusCode The HTTP status code of the redirect (if applicable), otherwise null.
     * @param redirectHttpStatusMessage The HTTP status message of the redirect (if applicable), otherwise null.
     * @param exceptionType The type of exception indicating the specific error encountered during feed processing.
     */
    SyndFeedException(String feedUrl, Integer httpStatusCode, String httpStatusMessage,
                      String redirectUrl, Integer redirectHttpStatusCode, String redirectHttpStatusMessage,
                      QueryExceptionType exceptionType) {
        super(exceptionType.name());
        this.feedUrl = feedUrl;
        this.httpStatusCode = httpStatusCode;
        this.httpStatusMessage = httpStatusMessage;
        this.redirectUrl = redirectUrl;
        this.redirectHttpStatusCode = redirectHttpStatusCode;
        this.redirectHttpStatusMessage = redirectHttpStatusMessage;
        this.exceptionType = exceptionType;
    }

    /**
     * Constructs a `SyndFeedException` with information about the exception and the underlying exception.
     *
     * @param feedUrl The URL of the feed.
     * @param httpStatusCode The HTTP status code.
     * @param httpStatusMessage The HTTP status message.
     * @param redirectUrl The URL to which the feed was redirected (if applicable), otherwise null.
     * @param redirectHttpStatusCode The HTTP status code of the redirect (if applicable), otherwise null.
     * @param redirectHttpStatusMessage The HTTP status message of the redirect (if applicable), otherwise null.
     * @param exception The underlying exception that caused this `SyndFeedException`.
     */
    SyndFeedException(String feedUrl, Integer httpStatusCode, String httpStatusMessage,
                      String redirectUrl, Integer redirectHttpStatusCode, String redirectHttpStatusMessage,
                      Exception exception) {
        super(exception);
        this.feedUrl = feedUrl;
        this.httpStatusCode = httpStatusCode;
        this.httpStatusMessage = httpStatusMessage;
        this.redirectUrl = redirectUrl;
        this.redirectHttpStatusCode = redirectHttpStatusCode;
        this.redirectHttpStatusMessage = redirectHttpStatusMessage;
        // Determine the exception type based on the underlying exception.
        if (exception instanceof FileNotFoundException) {
            exceptionType = FILE_NOT_FOUND_EXCEPTION;
        } else if (exception instanceof SSLHandshakeException) {
            exceptionType = SSL_HANDSHAKE_EXCEPTION;
        } else if (exception instanceof UnknownHostException) {
            exceptionType = UNKNOWN_HOST_EXCEPTION;
        } else if (exception instanceof SocketTimeoutException) {
            exceptionType = SOCKET_TIMEOUT_EXCEPTION;
        } else if (exception instanceof ConnectException) {
            exceptionType = CONNECT_EXCEPTION;
        } else if (exception instanceof SocketException) {
            exceptionType = SOCKET_EXCEPTION;
        } else if (exception instanceof IllegalArgumentException) {
            exceptionType = ILLEGAL_ARGUMENT_EXCEPTION;
        } else if (exception instanceof ParsingFeedException) {
            exceptionType = PARSING_FEED_EXCEPTION;
        } else if (exception instanceof IOException) {
            exceptionType = IO_EXCEPTION;
        } else {
            exceptionType = OTHER;
        }
    }
}
