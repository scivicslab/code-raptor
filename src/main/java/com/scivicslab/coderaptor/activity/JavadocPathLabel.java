package com.scivicslab.coderaptor.activity;

/**
 * Turns a compiled-documentation path into what it is about
 * ({@code ActivitySummary_260905_oo01}).
 *
 * <p>A Javadoc path is already made of words a reader knows —
 * {@code com/scivicslab/pojoactor/core/ActorRef.html} names a class and the package it is in. It
 * only has to be written the way that class is normally written, which is why nothing here needs a
 * model.</p>
 */
public final class JavadocPathLabel {

    private JavadocPathLabel() {}

    /**
     * What one served path is about.
     *
     * @param path the path under the project's compiled-documentation root
     * @return the class or page name, or {@code null} for a path that names nothing a reader would
     *         recognise — a stylesheet, a script, a frame, an index
     */
    public static String of(String path) {
        if (path == null || path.isBlank()) return null;
        String p = path.replace('\\', '/');
        if (!p.endsWith(".html")) return null;

        String file = p.substring(p.lastIndexOf('/') + 1, p.length() - ".html".length());
        // Pages the generator makes, which say what the tool is showing rather than what is being
        // read. Someone landing on the index has not chosen anything yet.
        switch (file) {
            case "index", "overview-summary", "overview-tree", "allclasses-index", "allpackages-index",
                 "help-doc", "deprecated-list", "constant-values", "serialized-form",
                 "search", "index-all":
                return null;
            default:
                break;
        }
        if (file.startsWith("index-")) return null;   // index-1.html … the split A-Z index

        String dir = p.contains("/") ? p.substring(0, p.lastIndexOf('/')) : "";
        if ("package-summary".equals(file) || "package-tree".equals(file) || "package-use".equals(file)) {
            return dir.isEmpty() ? null : "package " + dir.replace('/', '.');
        }
        return dir.isEmpty() ? file : dir.replace('/', '.') + "." + file;
    }
}
