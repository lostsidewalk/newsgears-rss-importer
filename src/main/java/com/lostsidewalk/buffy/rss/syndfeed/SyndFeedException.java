package com.lostsidewalk.buffy.rss.syndfeed;

import com.lostsidewalk.buffy.query.QueryMetrics.QueryExceptionType;
import com.rometools.rome.io.ParsingFeedException;

import javax.net.ssl.SSLHandshakeException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static com.lostsidewalk.buffy.query.QueryMetrics.QueryExceptionType.*;

public class SyndFeedException extends Exception {

    public final String feedUrl;

    public final Integer httpStatusCode;

    public final String httpStatusMessage;

    public final String redirectUrl;

    public final Integer redirectHttpStatusCode;

    public final String redirectHttpStatusMessage;

    public final QueryExceptionType exceptionType;

    SyndFeedException(String feedUrl, Integer httpStatusCode, String httpStatusMessage,
                           String redirectUrl, Integer redirectHttpStatusCode, String redirectHttpStatusMessage,
                           QueryExceptionType exceptionType)
    {
        super(exceptionType.name());
        this.feedUrl = feedUrl;
        this.httpStatusCode = httpStatusCode;
        this.httpStatusMessage = httpStatusMessage;
        this.redirectUrl = redirectUrl;
        this.redirectHttpStatusCode = redirectHttpStatusCode;
        this.redirectHttpStatusMessage = redirectHttpStatusMessage;
        this.exceptionType = exceptionType;
    }

    SyndFeedException(String feedUrl, Integer httpStatusCode, String httpStatusMessage,
                           String redirectUrl, Integer redirectHttpStatusCode, String redirectHttpStatusMessage,
                           Exception exception)
    {
        super(exception);
        this.feedUrl = feedUrl;
        this.httpStatusCode = httpStatusCode;
        this.httpStatusMessage = httpStatusMessage;
        this.redirectUrl = redirectUrl;
        this.redirectHttpStatusCode = redirectHttpStatusCode;
        this.redirectHttpStatusMessage = redirectHttpStatusMessage;
        if (exception instanceof FileNotFoundException) {
            exceptionType = FILE_NOT_FOUND_EXCEPTION;
        } else if (exception instanceof SSLHandshakeException) {
            exceptionType = SSL_HANDSHAKE_EXCEPTION;
        } else if (exception instanceof UnknownHostException) {
            exceptionType = UNKNOWN_HOST_EXCEPTION;
        } else if (exception instanceof SocketTimeoutException) {
            exceptionType = SOCKET_TIMEOUT_EXCEPTION;
        }else if (exception instanceof ConnectException) {
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
