package com.scivicslab.coderaptor.index;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;
import com.scivicslab.coderaptor.model.PagedResponse;
import com.scivicslab.coderaptor.model.ProjectInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintains a Lucene full-text index of project metadata (name, groupId, artifactId).
 *
 * <p>Index lives at {@code worksDir/.code-raptor/projects-index/}.
 * Rebuilt on every reindex; searched on every {@code GET /api/projects?q=...}.
 *
 * <p>When no query is supplied, callers should use in-memory sorting rather than
 * this searcher, to avoid the overhead of a MatchAllDocsQuery.
 */
@ApplicationScoped
public class ProjectIndexer {

    private static final Logger LOG = Logger.getLogger(ProjectIndexer.class);

    /** Cap on the number of Lucene hits retrieved per search (covers all pagination pages). */
    private static final int MAX_HITS = 10_000;

    private static final String[] SEARCH_FIELDS =
            {"name", "language", "buildSystem", "groupId", "artifactId", "version"};

    @Inject
    CodeRaptorConfig config;

    // ── Build ─────────────────────────────────────────────────────────────────

    /**
     * Rebuilds the project metadata index from the given list.
     * Replaces any existing index (OpenMode.CREATE).
     */
    public void buildIndex(List<ProjectInfo> projects) {
        Path indexDir = config.getDataDir().resolve("projects-index");
        try {
            Files.createDirectories(indexDir);
        } catch (IOException e) {
            LOG.errorf(e, "Cannot create projects-index directory: %s", indexDir);
            return;
        }

        try (Directory dir     = FSDirectory.open(indexDir);
             StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(dir,
                     new IndexWriterConfig(analyzer)
                             .setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {

            for (ProjectInfo p : projects) {
                Document doc = new Document();
                // Indexed + stored fields (used for search ranking and retrieval)
                doc.add(new TextField("name",        p.name(),        Field.Store.YES));
                doc.add(new TextField("language",    p.language(),    Field.Store.YES));
                doc.add(new TextField("buildSystem", p.buildSystem(), Field.Store.YES));
                doc.add(new TextField("groupId",     p.groupId(),     Field.Store.YES));
                doc.add(new TextField("artifactId",  p.artifactId(),  Field.Store.YES));
                doc.add(new TextField("version",     p.version(),     Field.Store.YES));
                // Stored-only fields (metadata flags, not indexed)
                doc.add(new StoredField("compiledDocs", p.compiledDocs()));
                doc.add(new StoredField("isMaven",       p.isMaven() ? "1" : "0"));
                writer.addDocument(doc);
            }

            LOG.infof("Project index built: %d projects", projects.size());

        } catch (IOException e) {
            LOG.errorf(e, "Failed to build project index");
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Searches the project index with a Lucene query.
     *
     * <p>Supports full Lucene query syntax (e.g. {@code name:pojo*}).
     * On parse failure the query is automatically escaped and retried as a
     * plain text search.
     *
     * @param queryStr user-supplied query (not blank)
     * @param page     0-based page index
     * @param size     page size (1–200)
     * @return paginated result; empty response if index does not exist
     */
    public PagedResponse<ProjectInfo> search(String queryStr, int page, int size) {
        Path indexDir = config.getDataDir().resolve("projects-index");
        if (!Files.exists(indexDir)) {
            LOG.warn("projects-index not found — run reindex first");
            return empty(size);
        }

        try (Directory dir      = FSDirectory.open(indexDir);
             DirectoryReader reader = DirectoryReader.open(dir)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();

            Query query = parseQuery(queryStr, analyzer);
            TopDocs topDocs = searcher.search(query, MAX_HITS);

            int total      = (int) topDocs.totalHits.value;
            int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / size);
            int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
            int from       = clampedPage * size;
            int to         = Math.min(from + size, total);

            List<ProjectInfo> items = new ArrayList<>(to - from);
            var storedFields = searcher.storedFields();
            for (int i = from; i < to; i++) {
                Document doc = storedFields.document(topDocs.scoreDocs[i].doc);
                items.add(new ProjectInfo(
                        doc.get("name"),
                        doc.get("language"),
                        doc.get("buildSystem"),
                        doc.get("compiledDocs"),
                        doc.get("groupId"),
                        doc.get("artifactId"),
                        doc.get("version"),
                        "1".equals(doc.get("isMaven"))
                ));
            }

            return new PagedResponse<>(total, clampedPage, size, totalPages, items);

        } catch (IOException e) {
            LOG.errorf(e, "Project search I/O error for query: %s", queryStr);
            return empty(size);
        }
    }

    /**
     * Parses the query string. Allows full Lucene syntax; on {@link ParseException}
     * falls back to an escaped plain-text query.
     */
    private Query parseQuery(String queryStr, StandardAnalyzer analyzer) throws IOException {
        MultiFieldQueryParser parser = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer);
        parser.setDefaultOperator(QueryParser.Operator.OR);
        try {
            return parser.parse(queryStr);
        } catch (ParseException e) {
            // Escape special characters and retry as a safe plain-text query
            try {
                return parser.parse(QueryParser.escape(queryStr));
            } catch (ParseException e2) {
                LOG.warnf("Unparseable query even after escaping: %s", queryStr);
                // Last resort: match nothing
                return new org.apache.lucene.search.MatchNoDocsQuery();
            }
        }
    }

    private static PagedResponse<ProjectInfo> empty(int size) {
        return new PagedResponse<>(0, 0, size, 0, List.of());
    }
}
