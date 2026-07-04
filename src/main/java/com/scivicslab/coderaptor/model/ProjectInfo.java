package com.scivicslab.coderaptor.model;

/**
 * Metadata about a single code project detected under works-dir.
 *
 * @param name          directory name (e.g. "POJO-actor")
 * @param language      primary programming language (e.g. "Java", "Python", "Go"); empty if unknown
 * @param buildSystem   build tool (e.g. "Maven", "Gradle", "Go Modules", "Cargo"); empty if unknown
 * @param compiledDocs  relative path (from project root) to compiled API docs directory,
 *                      e.g. "target/site/apidocs" or "target/doc"; empty string if absent
 * @param groupId       Maven groupId, or empty string
 * @param artifactId    Maven artifactId, or empty string
 * @param version       Maven version string, or empty string (kept for search; not shown in UI)
 * @param isMaven       true if pom.xml exists at the project root
 */
public record ProjectInfo(
        String name,
        String language,
        String buildSystem,
        String compiledDocs,
        String groupId,
        String artifactId,
        String version,
        boolean isMaven
) {}
