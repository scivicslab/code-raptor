package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.service.IndexService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST endpoint to trigger a full index rebuild.
 *
 * <p>POST /api/reindex
 */
@Path("/api/reindex")
@ApplicationScoped
public class ReindexResource {

    @Inject
    IndexService indexService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public String reindex() {
        indexService.reindex();
        return "{\"status\": \"ok\", \"message\": \"Reindex complete.\"}";
    }
}
