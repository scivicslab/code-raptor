package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.service.RebuildService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Starts a rebuild for every project, or for one project's part of each index; its progress is
 * read like a build's.
 *
 * <pre>
 *   POST /api/rebuild?target=javadoc                      → {"buildId":"b7"}
 *   POST /api/rebuild?target=symbol&amp;project=pluggable-cli → {"buildId":"b8"}
 *   GET  /api/build/b7                 → {"running":true, "output":"(3/80) pluggable-cli docs ✓ 9s\n…", …}
 * </pre>
 */
@Path("/api/rebuild")
@ApplicationScoped
public class RebuildResource {

    @Inject
    RebuildService rebuildService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response start(@QueryParam("target") String target, @QueryParam("project") String project) {
        try {
            String id = rebuildService.start(RebuildService.Target.parse(target),
                    project == null || project.isBlank() ? null : project);
            return Response.ok(Map.of("buildId", id)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
