package com.scivicslab.coderaptor.index;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;
import com.scivicslab.coderaptor.model.JavadocHit;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.jboss.logging.Logger;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Builds a Lucene full-text index over Javadoc HTML files found in
 * {@code <project>/target/site/apidocs/} for every code project.
 *
 * <p>Index location: {@code <works-dir>/.code-raptor/javadoc-index/}
 */
@ApplicationScoped
public class JavadocIndexer {

    private static final Logger LOG = Logger.getLogger(JavadocIndexer.class);
    private static final int MAX_RESULTS = 20;
    private static final int SNIPPET_LENGTH = 300;

    @Inject
    CodeRaptorConfig config;

    // -------------------------------------------------------------------------
    // Indexing
    // -------------------------------------------------------------------------

    /**
     * (Re)builds the Lucene index from all Javadoc HTML files found under
     * {@code <project>/target/site/apidocs/} for each supplied project.
     *
     * @param projects list of code project directories
     */
    public void buildIndex(List<Path> projects) {
        Path indexDir = config.getDataDir().resolve("javadoc-index");
        try {
            Files.createDirectories(indexDir);
        } catch (IOException e) {
            LOG.errorf(e, "Cannot create index directory: %s", indexDir);
            return;
        }

        LOG.info("Building Javadoc Lucene index...");
        int totalFiles = 0;

        try (FSDirectory dir = FSDirectory.open(indexDir);
             StandardAnalyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(dir,
                     new IndexWriterConfig(analyzer)
                             .setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {

            for (Path project : projects) {
                Path apidocs = project.resolve("target/site/apidocs");
                if (!Files.isDirectory(apidocs)) {
                    continue;
                }
                int count = indexApidocsDir(writer, apidocs);
                if (count > 0) {
                    LOG.infof("  Indexed %d Javadoc page(s) from %s", count, project.getFileName());
                    totalFiles += count;
                }
            }

            writer.commit();

        } catch (IOException e) {
            LOG.errorf(e, "Failed to build Javadoc index");
            return;
        }

        LOG.infof("Javadoc index built: %d page(s) indexed.", totalFiles);
    }

    /**
     * Walks the apidocs directory and indexes each HTML file.
     *
     * @return number of files indexed
     */
    private int indexApidocsDir(IndexWriter writer, Path apidocs) throws IOException {
        int count = 0;
        try (Stream<Path> walk = Files.walk(apidocs)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".html"))::iterator) {
                try {
                    indexHtmlFile(writer, file);
                    count++;
                } catch (IOException e) {
                    LOG.debugf("Skipping %s: %s", file, e.getMessage());
                }
            }
        }
        return count;
    }

    private void indexHtmlFile(IndexWriter writer, Path htmlFile) throws IOException {
        org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(htmlFile.toFile(), "UTF-8");

        String title = jsoupDoc.title();
        String bodyText = jsoupDoc.body() != null ? jsoupDoc.body().text() : "";
        String url = htmlFile.toAbsolutePath().toString();

        if (title.isBlank() && bodyText.isBlank()) {
            return; // Skip empty files
        }

        Document luceneDoc = new Document();
        luceneDoc.add(new StoredField("url", url));
        luceneDoc.add(new Field("title", title, TextField.TYPE_STORED));
        luceneDoc.add(new Field("content", bodyText, TextField.TYPE_STORED));

        writer.addDocument(luceneDoc);
    }

    // -------------------------------------------------------------------------
    // Searching
    // -------------------------------------------------------------------------

    /**
     * Searches the Javadoc index for the given query string.
     *
     * @param query search terms (class name, method name, description text, etc.)
     * @return up to {@value MAX_RESULTS} hits, ordered by relevance
     */
    public List<JavadocHit> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        Path indexDir = config.getDataDir().resolve("javadoc-index");
        if (!Files.exists(indexDir)) {
            LOG.warn("Javadoc index does not exist yet. Run POST /api/reindex first.");
            return Collections.emptyList();
        }

        try (FSDirectory dir = FSDirectory.open(indexDir);
             DirectoryReader reader = DirectoryReader.open(dir)) {

            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();

            // Boost title matches over content matches
            String[] fields = {"title", "content"};
            java.util.Map<String, Float> boosts = java.util.Map.of("title", 3.0f, "content", 1.0f);
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);
            parser.setDefaultOperator(org.apache.lucene.queryparser.classic.QueryParser.Operator.AND);

            Query q;
            try {
                q = parser.parse(MultiFieldQueryParser.escape(query));
            } catch (ParseException e) {
                LOG.warnf("Invalid query '%s': %s", query, e.getMessage());
                return Collections.emptyList();
            }

            TopDocs topDocs = searcher.search(q, MAX_RESULTS);
            List<JavadocHit> hits = new ArrayList<>();

            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(sd.doc);
                String title = doc.get("title");
                String url = doc.get("url");
                String content = doc.get("content");
                String snippet = content != null && content.length() > SNIPPET_LENGTH
                        ? content.substring(0, SNIPPET_LENGTH) + "..."
                        : content;
                hits.add(new JavadocHit(title, url, snippet));
            }

            return hits;

        } catch (IOException e) {
            LOG.errorf(e, "Failed to search Javadoc index");
            return Collections.emptyList();
        }
    }
}
