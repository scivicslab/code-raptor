package com.scivicslab.coderaptor.model;

/**
 * A single hit from a Javadoc search.
 *
 * @param title   document title (e.g. "ActorRef (POJO-actor 3.0.1 API)")
 * @param url     absolute file path to the Javadoc HTML file
 * @param snippet first ~300 chars of the page body text
 * @param docsUrl the {@code /docs/{project}/{path}} URL that serves the page, or {@code null} when
 *                it is not served there
 */
public record JavadocHit(String title, String url, String snippet, String docsUrl) {

    /** @return this hit with {@code docsUrl} set */
    public JavadocHit withDocsUrl(String docsUrl) {
        return new JavadocHit(title, url, snippet, docsUrl);
    }
}
