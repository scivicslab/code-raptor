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

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<SymbolHit> search(
            @QueryParam("q") String query,
            @QueryParam("type") String type) {

        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        servedLog.note("symbol?q=" + query + "&type=" + type,
                       "Searching the source for \"" + query + "\"");
        String[] args = buildGlobalArgs(query, type);
        return runGlobal(args);
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

        } catch (IOException | InterruptedException e) {
            LOG.errorf(e, "Failed to run global");
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Collections.emptyList();
        }
    }
}
