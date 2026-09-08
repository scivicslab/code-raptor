package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;
import com.scivicslab.coderaptor.index.ProjectInfoReader;
import com.scivicslab.coderaptor.model.ProjectInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;

/**
 * Serves compiled documentation (Javadoc, rustdoc, Sphinx, …) for a project.
 *
 * <p>GET /docs/{project}             → 302 redirect to /docs/{project}/index.html
 * <p>GET /docs/{project}/{path…}     → serves the file from compiledDocs root
 *
 * <p>Security: both the project directory and the resolved file are checked to
 * be under their respective allowed roots before serving.
 */
@Path("/docs")
@ApplicationScoped
public class DocsResource {

    @Inject CodeRaptorConfig config;
    @Inject ProjectInfoReader infoReader;
    @Inject com.scivicslab.coderaptor.activity.ServedLog servedLog;

    /** Redirect bare project name to index.html. */
    @GET
    @Path("/{project}")
    public Response redirectToIndex(@PathParam("project") String project) {
        return Response.seeOther(URI.create("/docs/" + project + "/index.html")).build();
    }

    /** Serve a file from the project's compiled-docs directory. */
    @GET
    @Path("/{project}/{path: .+}")
    public Response serveDoc(
            @PathParam("project") String project,
            @PathParam("path")    String path) {

        java.nio.file.Path worksDir = config.getWorksDir();

        // ── Security: project must be a direct child of worksDir ─────────
        java.nio.file.Path projectDir = worksDir.resolve(project).normalize();
        if (!projectDir.startsWith(worksDir.normalize())) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        if (!Files.isDirectory(projectDir)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Project not found: " + project).build();
        }

        // ── Locate compiledDocs root for this project ─────────────────────
        ProjectInfo info = infoReader.read(projectDir);
        if (info.compiledDocs().isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No compiled docs for project: " + project).build();
        }

        java.nio.file.Path docsRoot = projectDir.resolve(info.compiledDocs()).normalize();

        // ── Security: resolved file must be inside docsRoot ───────────────
        java.nio.file.Path target = docsRoot.resolve(path).normalize();
        if (!target.startsWith(docsRoot)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        if (!Files.isRegularFile(target)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            byte[] bytes = Files.readAllBytes(target);
            // Only pages that name something a reader recognises. One Javadoc page pulls in a
            // stylesheet, a script and a dozen icons, and none of those say what is being read.
            String label = com.scivicslab.coderaptor.activity.JavadocPathLabel.of(path);
            if (label != null) servedLog.note(project + "/" + path, label + " (" + project + ")");
            return Response.ok(bytes)
                    .type(mimeType(target.getFileName().toString()))
                    .build();
        } catch (IOException e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private static String mimeType(String filename) {
        if (filename.endsWith(".html") || filename.endsWith(".htm"))
            return "text/html; charset=UTF-8";
        if (filename.endsWith(".css"))   return "text/css";
        if (filename.endsWith(".js"))    return "application/javascript";
        if (filename.endsWith(".json"))  return "application/json";
        if (filename.endsWith(".png"))   return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".gif"))   return "image/gif";
        if (filename.endsWith(".svg"))   return "image/svg+xml";
        if (filename.endsWith(".ico"))   return "image/x-icon";
        if (filename.endsWith(".woff"))  return "font/woff";
        if (filename.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
