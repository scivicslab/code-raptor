package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.service.BuildService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * REST API for running project builds asynchronously.
 *
 * <pre>
 *   POST /api/build?project=X&amp;task=build&amp;buildSystem=Maven
 *        → {"buildId":"b1"}
 *
 *   GET  /api/build/b1
 *        → {"id":"b1","project":"X","task":"build","running":false,
 *            "exitCode":0,"output":"..."}
 * </pre>
 */
@Path("/api/build")
@ApplicationScoped
public class BuildResource {

    @Inject
    BuildService buildService;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response start(
            @QueryParam("project")     String project,
            @QueryParam("task")        String task,
            @QueryParam("buildSystem") String buildSystem) {
        try {
            String id = buildService.start(project, task, buildSystem);
            return Response.ok(Map.of("buildId", id)).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    /** POST /api/build/b1/stop — stops the job; its status then reports {@code "stopped": true}. */
    @POST
    @Path("{id}/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stop(@PathParam("id") String id) {
        return buildService.stop(id)
                ? Response.ok(Map.of("stopping", true)).build()
                : Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Build job not found: " + id))
                        .build();
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(@PathParam("id") String id) {
        return buildService.get(id)
                .map(status -> Response.ok(status).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Build job not found: " + id))
                        .build());
    }
}
