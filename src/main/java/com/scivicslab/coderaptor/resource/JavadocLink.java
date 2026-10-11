package com.scivicslab.coderaptor.resource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * Turns a Javadoc search hit — the HTML file on disk — into the {@code /docs/{project}/{path}} URL
 * {@link DocsResource} serves it at, so a hit can be opened from the search results.
 *
 * <p>The path after the project is relative to the project's compiled-docs root, because that is
 * the root {@link DocsResource} resolves against. A file outside that root, or in a project with no
 * compiled docs, is not served there, and gets no link.</p>
 */
final class JavadocLink {

    private JavadocLink() {}

    /**
     * @param worksDir       the directory the projects are in
     * @param htmlFile       the hit's file
     * @param compiledDocsOf a project directory's compiled-docs root, relative to it; empty for none
     * @return the URL, or {@code null} when the file is not served under {@code /docs}
     */
    static String docsUrl(Path worksDir, Path htmlFile, Function<Path, String> compiledDocsOf) {
        if (htmlFile == null) return null;
        Path works = worksDir.normalize();
        Path file = htmlFile.normalize();
        if (!file.startsWith(works) || works.relativize(file).getNameCount() < 2) return null;
        String project = works.relativize(file).getName(0).toString();
        Path projectDir = works.resolve(project);
        String compiledDocs = compiledDocsOf.apply(projectDir);
        if (compiledDocs == null || compiledDocs.isBlank()) return null;
        Path docsRoot = projectDir.resolve(compiledDocs).normalize();
        if (!file.startsWith(docsRoot) || file.equals(docsRoot)) return null;
        StringJoiner url = new StringJoiner("/", "/docs/" + encode(project) + "/", "");
        for (Path part : docsRoot.relativize(file)) url.add(encode(part.toString()));
        return url.toString();
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
