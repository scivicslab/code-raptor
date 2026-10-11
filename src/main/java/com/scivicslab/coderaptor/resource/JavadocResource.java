package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.index.JavadocIndexer;
import com.scivicslab.coderaptor.model.JavadocHit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Collections;
import java.util.List;

/**
 * REST endpoint for Javadoc full-text search.
 *
 * <p>GET /api/javadoc?q=ActorRef
 */
@Path("/api/javadoc")
@ApplicationScoped
public class JavadocResource {

    @Inject
    JavadocIndexer indexer;

    @Inject
    com.scivicslab.coderaptor.activity.ServedLog servedLog;

    @Inject
    com.scivicslab.coderaptor.config.CodeRaptorConfig config;

    @Inject
    com.scivicslab.coderaptor.index.ProjectInfoReader infoReader;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<JavadocHit> search(@QueryParam("q") String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        servedLog.note("javadoc?q=" + query, "Searching the Javadoc for \"" + query + "\"");
        // Each hit names a file on disk; the page links to it through /docs, which serves it.
        java.nio.file.Path works = config.getWorksDir();
        return indexer.search(query).stream()
                .map(h -> h.withDocsUrl(JavadocLink.docsUrl(works,
                        h.url() == null ? null : java.nio.file.Path.of(h.url()),
                        projectDir -> infoReader.read(projectDir).compiledDocs())))
                .toList();
    }
}
