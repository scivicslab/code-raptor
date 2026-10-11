package com.scivicslab.coderaptor.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A search the LLM asked for by writing a tag in its reply:
 * {@code <symbol_search type="definition">requestFromWorker</symbol_search>} or
 * {@code <javadoc_search>ActorRef tell</javadoc_search>}.
 *
 * @param tool  {@code symbol_search} or {@code javadoc_search}
 * @param type  for a symbol search, {@code definition}, {@code references}, {@code grep} or {@code files};
 *              empty for a Javadoc search
 * @param query the words to search for
 */
public record SearchRequest(String tool, String type, String query) {

    public static final String SYMBOL = "symbol_search";
    public static final String JAVADOC = "javadoc_search";

    private static final Set<String> SYMBOL_TYPES = Set.of("definition", "references", "grep", "files");

    private static final Pattern TAG = Pattern.compile(
            "<(symbol_search|javadoc_search)((?:\\s+\\w+\\s*=\\s*\"[^\"]*\")*)\\s*>(.*?)</\\1\\s*>",
            Pattern.DOTALL);
    private static final Pattern TYPE_ATTR = Pattern.compile("type\\s*=\\s*\"([^\"]*)\"");

    /**
     * The searches a reply asks for, in the order written. A symbol search without a known
     * {@code type} is a definition search; a tag with no words is ignored.
     */
    public static List<SearchRequest> parse(String reply) {
        List<SearchRequest> found = new ArrayList<>();
        if (reply == null) return found;
        Matcher m = TAG.matcher(reply);
        while (m.find()) {
            String query = m.group(3).strip();
            if (query.isEmpty()) continue;
            if (JAVADOC.equals(m.group(1))) {
                found.add(new SearchRequest(JAVADOC, "", query));
            } else {
                Matcher t = TYPE_ATTR.matcher(m.group(2));
                String type = t.find() ? t.group(1).strip() : "";
                found.add(new SearchRequest(SYMBOL, SYMBOL_TYPES.contains(type) ? type : "definition", query));
            }
        }
        return found;
    }

    /** The reply with the search tags taken out, for showing as text. */
    public static String withoutTags(String reply) {
        return reply == null ? "" : TAG.matcher(reply).replaceAll("").strip();
    }
}
