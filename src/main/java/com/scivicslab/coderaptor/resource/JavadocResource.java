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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<JavadocHit> search(@QueryParam("q") String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        return indexer.search(query);
    }
}
