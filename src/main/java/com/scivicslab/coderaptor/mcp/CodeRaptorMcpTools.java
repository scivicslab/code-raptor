package com.scivicslab.coderaptor.mcp;

import com.scivicslab.coderaptor.index.JavadocIndexer;
import com.scivicslab.coderaptor.model.JavadocHit;
import com.scivicslab.coderaptor.model.SymbolHit;
import com.scivicslab.coderaptor.resource.SymbolResource;
import com.scivicslab.coderaptor.service.IndexService;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * MCP tool definitions for code-raptor.
 *
 * <p>Tools are automatically discovered by the Quarkus MCP Server extension
 * and exposed via the MCP endpoint (default: /mcp).
 */
public class CodeRaptorMcpTools {

    private static final Logger LOG = Logger.getLogger(CodeRaptorMcpTools.class);

    @Inject
    SymbolResource symbolResource;

    @Inject
    JavadocIndexer javadocIndexer;

    @Inject
    IndexService indexService;

    // -------------------------------------------------------------------------
    // find_symbol
    // -------------------------------------------------------------------------

    @Tool(description = "Search for symbol definitions and references using GNU Global (gtags). "
            + "Returns a list of source locations where the symbol appears. "
            + "Works across all code projects under the works directory.")
    String findSymbol(
            @ToolArg(description = "Symbol name or pattern to search for (e.g. 'addIIActor', 'ActorRef')") String symbol,
            @ToolArg(description = "Search type: 'references' (default, where symbol is used), "
                    + "'definition' (where symbol is defined), "
                    + "'grep' (full-text pattern search), "
                    + "'files' (file name search)") String type) {
        try {
            if (type == null || type.isBlank()) {
                type = "references";
            }
            List<SymbolHit> hits = symbolResource.search(symbol, type);

            if (hits.isEmpty()) {
                return "No results found for symbol: " + symbol + " (type=" + type + ")";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(hits.size()).append(" result(s) for '")
              .append(symbol).append("' (").append(type).append("):\n\n");
            for (SymbolHit h : hits) {
                sb.append(h.file()).append(":").append(h.line()).append("\n");
                sb.append("  ").append(h.content().trim()).append("\n");
            }
            return sb.toString();

        } catch (Exception e) {
            LOG.errorf(e, "findSymbol error");
            return "Error: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // search_javadoc
    // -------------------------------------------------------------------------

    @Tool(description = "Search Javadoc across all code projects. "
            + "Uses full-text Lucene search on class names, method names, and descriptions. "
            + "Returns matching pages with title and file path.")
    String searchJavadoc(
            @ToolArg(description = "Search query (class name, method name, or description text, e.g. 'ActorRef tell')") String query) {
        try {
            List<JavadocHit> hits = javadocIndexer.search(query);

            if (hits.isEmpty()) {
                return "No Javadoc found matching: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(hits.size()).append(" Javadoc result(s) for '")
              .append(query).append("':\n\n");
            for (JavadocHit h : hits) {
                sb.append(h.title()).append("\n");
                sb.append("  ").append(h.url()).append("\n");
                if (h.snippet() != null && !h.snippet().isBlank()) {
                    sb.append("  ").append(h.snippet().trim(), 0,
                            Math.min(200, h.snippet().trim().length())).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString().trim();

        } catch (Exception e) {
            LOG.errorf(e, "searchJavadoc error");
            return "Error: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // reindex
    // -------------------------------------------------------------------------

    @Tool(description = "Rebuild search indexes for all code projects under the works directory. "
            + "Run this after adding new projects or after significant source code changes.")
    String reindex() {
        try {
            indexService.reindex();
            return "Reindex complete.";
        } catch (Exception e) {
            LOG.errorf(e, "reindex error");
            return "Error during reindex: " + e.getMessage();
        }
    }
}
