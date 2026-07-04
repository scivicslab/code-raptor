package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;
import com.scivicslab.coderaptor.index.ProjectIndexer;
import com.scivicslab.coderaptor.index.ProjectInfoReader;
import com.scivicslab.coderaptor.index.ProjectScanner;
import com.scivicslab.coderaptor.model.PagedResponse;
import com.scivicslab.coderaptor.model.ProjectInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.Comparator;
import java.util.List;

/**
 * REST endpoint that returns a paginated, searchable list of detected code projects.
 *
 * <p>GET /api/projects?page=0&amp;size=20&amp;q=actor
 *
 * <ul>
 *   <li>page  — 0-based page index (default 0)</li>
 *   <li>size  — items per page (default 20, clamped 1–200)</li>
 *   <li>q     — optional Lucene query; when absent, returns all projects sorted by name</li>
 * </ul>
 *
 * <p>When {@code q} is present the result is ordered by Lucene relevance score.
 * When {@code q} is absent the result is ordered alphabetically by project name.
 */
@Path("/api/projects")
@ApplicationScoped
public class ProjectsResource {

    @Inject CodeRaptorConfig config;
    @Inject ProjectScanner   scanner;
    @Inject ProjectInfoReader infoReader;
    @Inject ProjectIndexer   projectIndexer;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PagedResponse<ProjectInfo> list(
            @QueryParam("page") @DefaultValue("0")  int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("q")                        String q) {

        int clampedSize = Math.max(1, Math.min(size, 200));

        // ── Full-text search via Lucene ────────────────────────────────────
        if (q != null && !q.isBlank()) {
            return projectIndexer.search(q.trim(), page, clampedSize);
        }

        // ── Browse all (no query): in-memory sort + slice ─────────────────
        // Scanning is fast (~100 dirs); this avoids a Lucene MatchAllDocsQuery
        // and always reflects the current filesystem state.
        List<ProjectInfo> all = scanner.scanCodeProjects(config.getWorksDir()).stream()
                .map(infoReader::read)
                .sorted(Comparator.comparing(ProjectInfo::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int total       = all.size();
        int totalPages  = total == 0 ? 1 : (int) Math.ceil((double) total / clampedSize);
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        int from        = clampedPage * clampedSize;
        int to          = Math.min(from + clampedSize, total);

        return new PagedResponse<>(total, clampedPage, clampedSize, totalPages,
                all.subList(from, to));
    }
}
