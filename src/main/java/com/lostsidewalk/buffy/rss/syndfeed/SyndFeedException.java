package com.lostsidewalk.buffy.rss.syndfeed;

public class SyndFeedException extends Exception {
    SyndFeedException(String mesg) {
        super("Unable to fetch feed due to: " + mesg);
    }
}
