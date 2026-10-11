package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;
import com.scivicslab.coderaptor.model.SymbolHit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * REST endpoint for symbol search via GNU Global.
 *
 * <p>GET /api/symbol?q=addIIActor&amp;type=references
 *
 * <p>Supported types:
 * <ul>
 *   <li>definition — {@code global -x <symbol>}</li>
 *   <li>references — {@code global -rx <symbol>} (default)</li>
 *   <li>files      — {@code global -P <pattern>}</li>
 *   <li>grep       — {@code global -g <pattern>}</li>
 * </ul>
 */
@Path("/api/symbol")
@ApplicationScoped
public class SymbolResource {

    private static final Logger LOG = Logger.getLogger(SymbolResource.class);

    @Inject
    CodeRaptorConfig config;

    @Inject
    com.scivicslab.coderaptor.activity.ServedLog servedLog;

    /**
     * Symbols matching {@code q}, or 503 with the reason when GNU Global cannot be run. An empty
     * list would read as "nothing matches" while nothing was searched.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(
            @QueryParam("q") String query,
            @QueryParam("type") String type) {

        if (query == null || query.isBlank()) {
            return Response.ok(Collections.emptyList()).build();
        }

        try {
            return Response.ok(hits(query, type)).build();
        } catch (SymbolSearchUnavailable e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(java.util.Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * The symbols matching {@code query}, for this endpoint and the MCP tool alike.
     *
     * @throws SymbolSearchUnavailable when GNU Global cannot be run, so a caller does not report
     *         "no results" for a search that never happened
     */
    public List<SymbolHit> hits(String query, String type) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        servedLog.note("symbol?q=" + query + "&type=" + type,
                       "Searching the source for \"" + query + "\"");
        return runGlobal(buildGlobalArgs(query, type));
    }

    /** The search command could not be started at all, so nothing was searched. */
    public static final class SymbolSearchUnavailable extends RuntimeException {
        SymbolSearchUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Builds the argument array for the global command based on search type.
     */
    private String[] buildGlobalArgs(String query, String type) {
        if (type == null || type.isBlank()) {
            type = "references";
        }
        return switch (type.toLowerCase()) {
            case "definition"  -> new String[]{"global", "-x",   query};
            case "files"       -> new String[]{"global", "-Px",  query};
            case "grep"        -> new String[]{"global", "-gx",  query};
            default            -> new String[]{"global", "-rx",  query}; // references
        };
    }

    /**
     * Runs the global command and parses each output line into a SymbolHit.
     */
    List<SymbolHit> runGlobal(String[] args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(config.getWorksDir().toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0 && !stderr.isBlank()) {
                LOG.warnf("global %s exited %d: %s", args[1], exitCode, stderr.trim());
            }

            List<SymbolHit> hits = new ArrayList<>();
            for (String line : stdout.split("\n")) {
                SymbolHit hit = SymbolHit.parse(line);
                if (hit != null) {
                    hits.add(hit);
                }
            }
            return hits;

        } catch (IOException e) {
            LOG.errorf(e, "Failed to run global");
            throw new SymbolSearchUnavailable("Symbol search needs GNU Global, and its '" + args[0]
                    + "' command could not be started (is GNU Global installed?): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            LOG.errorf(e, "Failed to run global");
            Thread.currentThread().interrupt();
            return Collections.emptyList();
        }
    }
}
