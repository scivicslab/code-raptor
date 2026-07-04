package com.scivicslab.coderaptor.model;

/**
 * A single hit from a Javadoc search.
 *
 * @param title   document title (e.g. "ActorRef (POJO-actor 3.0.1 API)")
 * @param url     absolute file path to the Javadoc HTML file
 * @param snippet first ~300 chars of the page body text
 */
public record JavadocHit(String title, String url, String snippet) {}
