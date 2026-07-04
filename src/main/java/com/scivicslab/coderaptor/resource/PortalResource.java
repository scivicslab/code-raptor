package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.config.CodeRaptorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Serves the code-raptor portal UI.
 *
 * <p>GET /        — project list + javadoc search
 * <p>GET /search  — symbol search results (OpenGrok-style)
 */
@Path("/")
@ApplicationScoped
public class PortalResource {

    // ── Shared CSS (Catppuccin dark theme) ────────────────────────────────────
    private static final String COMMON_CSS = """
              :root {
                --bg-primary:    #1e1e2e;
                --bg-secondary:  #313244;
                --bg-tertiary:   #45475a;
                --text-primary:  #cdd6f4;
                --text-secondary:#a6adc8;
                --accent:        #a6e3a1;
                --accent2:       #89b4fa;
                --accent3:       #cba6f7;
                --border:        #585b70;
                --red:           #f38ba8;
                --yellow:        #f9e2af;
              }
              * { box-sizing: border-box; margin: 0; padding: 0; }
              body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                     background: var(--bg-primary); color: var(--text-primary); min-height: 100vh; }

              /* ── Header ── */
              header {
                background: var(--bg-secondary);
                border-bottom: 1px solid var(--border);
                padding: 0.65rem 2rem;
                display: flex; align-items: center; gap: 1.5rem; flex-wrap: wrap;
              }
              .header-brand { display: flex; align-items: baseline; gap: 0.75rem; }
              .header-brand h1 { font-size: 1.1rem; font-weight: 700; }
              .header-brand h1 a { color: inherit; text-decoration: none; }
              .header-brand h1 a:hover { color: var(--accent2); }
              .header-brand p  { font-size: 0.78rem; color: var(--text-secondary); }

              .search-forms { display: flex; gap: 1rem; flex-wrap: wrap; margin-left: auto; align-items: center; }
              .search-group { display: flex; gap: 0.4rem; align-items: center; }
              .search-group label {
                font-size: 0.75rem; color: var(--text-secondary);
                white-space: nowrap; font-weight: 600;
                text-transform: uppercase; letter-spacing: 0.05em;
              }
              .search-group input[type=text] {
                padding: 0.32rem 0.7rem; border-radius: 4px;
                border: 1px solid var(--border);
                background: var(--bg-primary); color: var(--text-primary);
                font-size: 0.875rem; width: 200px; outline: none;
              }
              .search-group input[type=text]:focus { border-color: var(--accent2); }
              .search-group input[type=text]::placeholder { color: var(--text-secondary); }
              .search-group select {
                padding: 0.32rem 0.5rem; border-radius: 4px;
                border: 1px solid var(--border);
                background: var(--bg-tertiary); color: var(--text-primary);
                font-size: 0.8rem; cursor: pointer; outline: none;
              }
              .btn {
                padding: 0.32rem 0.8rem; border-radius: 4px;
                border: 1px solid var(--border);
                background: var(--bg-tertiary); color: var(--text-primary);
                font-size: 0.8rem; font-weight: 600; cursor: pointer;
                white-space: nowrap;
              }
              .btn:hover { border-color: var(--accent); color: var(--accent); }
              .btn:disabled { opacity: 0.5; cursor: not-allowed; }
              .btn-primary { background: var(--accent); color: #1e1e2e; border-color: var(--accent); }
              .btn-primary:hover { background: #94d5a0; border-color: #94d5a0; color: #1e1e2e; }

              .separator { width: 1px; height: 24px; background: var(--border); margin: 0 0.2rem; align-self: center; flex-shrink: 0; }

              /* ── Main layout ── */
              main { max-width: 1100px; margin: 1.5rem auto; padding: 0 1.5rem; }

              .section-label {
                font-size: 0.75rem; font-weight: 600; color: var(--text-secondary);
                text-transform: uppercase; letter-spacing: 0.06em;
                margin-bottom: 0.6rem;
                border-bottom: 1px solid var(--border); padding-bottom: 0.35rem;
                display: flex; align-items: center; gap: 0.5rem;
              }
              .count-badge {
                font-size: 0.72rem; font-weight: 600;
                background: var(--bg-tertiary); color: var(--accent);
                border: 1px solid var(--border); border-radius: 3px;
                padding: 0.05rem 0.4rem; font-family: monospace;
              }
              .status-row {
                padding: 10px 14px; font-size: 0.83rem; color: var(--text-secondary);
                background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 8px;
                margin-bottom: 1.5rem;
              }
              .status-row.error { color: var(--red); }

              /* ── Build action buttons ── */
              .build-btn {
                font-size: 0.72rem; padding: 0.1rem 0.5rem;
                border-radius: 3px; border: 1px solid var(--border);
                background: var(--bg-tertiary); color: var(--text-secondary);
                cursor: pointer; font-family: 'SFMono-Regular', Consolas, monospace;
                min-width: 3rem; text-align: center; flex-shrink: 0;
              }
              .build-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
              .build-btn:disabled { opacity: 0.5; cursor: not-allowed; }
              .build-btn-running { border-color: var(--yellow) !important; color: var(--yellow) !important;
                                   animation: blink 1s step-start infinite; }
              .build-btn-ok  { border-color: var(--accent) !important; color: var(--accent) !important; }
              .build-btn-err { border-color: var(--red)    !important; color: var(--red)    !important; }
              @keyframes blink { 50% { opacity: 0.45; } }
            """;

    // ── Main portal page (/) ───────────────────────────────────────────────────
    private static final String PORTAL_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>code-raptor</title>
              <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🦅</text></svg>">
              <style>
            """ + COMMON_CSS + """
                /* ── Project list ── */
                .project-list { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 8px 8px 0 0; overflow: hidden; }
                .project-row { display: flex; align-items: center; gap: 14px; padding: 9px 16px; border-bottom: 1px solid var(--border); }
                .project-row:last-child { border-bottom: none; }
                .project-name { font-size: 0.92rem; font-weight: 700; min-width: 200px; }
                .project-name a { color: var(--text-primary); text-decoration: none; }
                .project-name a:hover { color: var(--accent2); text-decoration: underline; }
                .project-labels { flex: 1; display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; }
                .tag { font-size: 0.72rem; padding: 0.1rem 0.45rem; border-radius: 3px; border: 1px solid var(--border); font-family: 'SFMono-Regular', Consolas, monospace; }
                .tag-lang    { background: var(--bg-tertiary); color: var(--accent3); }
                .tag-build   { background: var(--bg-tertiary); color: var(--accent2); }
                .tag-docs    { background: var(--bg-tertiary); color: var(--accent); }
                .tag-unknown { background: var(--bg-tertiary); color: var(--text-secondary); }

                /* ── Search result cards (Javadoc) ── */
                .result-list { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 8px; overflow: hidden; margin-bottom: 1.5rem; }
                .result-row { padding: 8px 14px; border-bottom: 1px solid var(--border); font-size: 0.85rem; }
                .result-row:last-child { border-bottom: none; }
                .hit-title { font-weight: 700; color: var(--text-primary); }
                .hit-url { font-family: 'SFMono-Regular', Consolas, monospace; color: var(--accent2); font-size: 0.78rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
                .hit-snippet { color: var(--text-secondary); font-size: 0.8rem; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

                /* ── Pager ── */
                .pager { display: flex; align-items: center; gap: 0.5rem; padding: 8px 14px; background: var(--bg-secondary); border: 1px solid var(--border); border-top: none; border-radius: 0 0 8px 8px; font-size: 0.8rem; color: var(--text-secondary); margin-bottom: 1.5rem; }
                .pager-info { flex: 1; }
                .pager .btn { padding: 0.2rem 0.6rem; font-size: 0.78rem; }
                .pager .btn:disabled { opacity: 0.35; cursor: not-allowed; }
                .pager-pages { display: flex; gap: 0.25rem; flex-wrap: wrap; }
                .pager-page { padding: 0.2rem 0.55rem; border-radius: 3px; font-size: 0.78rem; border: 1px solid var(--border); background: var(--bg-tertiary); color: var(--text-primary); cursor: pointer; }
                .pager-page.active { background: var(--accent); color: #1e1e2e; border-color: var(--accent); font-weight: 700; }
                .pager-page:hover:not(.active) { border-color: var(--accent); color: var(--accent); }
                .pager-ellipsis { padding: 0.2rem 0.3rem; font-size: 0.78rem; color: var(--text-secondary); }

                .filter-input { padding: 0.18rem 0.55rem; border-radius: 4px; border: 1px solid var(--border); background: var(--bg-primary); color: var(--text-primary); font-size: 0.78rem; width: 150px; outline: none; font-weight: 400; text-transform: none; letter-spacing: 0; }
                .filter-input:focus { border-color: var(--accent2); }
                .filter-input::placeholder { color: var(--text-secondary); }
                select.page-size { padding: 0.18rem 0.4rem; border-radius: 3px; border: 1px solid var(--border); background: var(--bg-tertiary); color: var(--text-primary); font-size: 0.78rem; cursor: pointer; outline: none; }

                /* ── Build actions column ── */
                .project-actions { display: flex; gap: 0.3rem; align-items: center; flex-shrink: 0; }

                /* ── Build log panel (fixed bottom) ── */
                #build-log-panel {
                  position: fixed; bottom: 0; left: 0; right: 0;
                  height: 40vh; min-height: 180px;
                  display: none; flex-direction: column;
                  background: #08080f;
                  border-top: 2px solid var(--accent2);
                  z-index: 1000; box-shadow: 0 -4px 20px rgba(0,0,0,.6);
                }
                .log-header {
                  display: flex; align-items: center; gap: 1rem;
                  padding: 0.35rem 1rem;
                  background: var(--bg-secondary); border-bottom: 1px solid var(--border);
                  flex-shrink: 0;
                }
                .log-title  { font-family: monospace; font-size: 0.85rem; font-weight: 700; flex: 1; }
                .log-status { font-size: 0.78rem; color: var(--text-secondary); font-family: monospace; }
                .log-rerun  {
                  background: none; border: 1px solid var(--border); color: var(--text-secondary);
                  border-radius: 3px; cursor: pointer; padding: 0.1rem 0.55rem; font-size: 0.78rem;
                }
                .log-rerun:hover  { border-color: var(--accent2); color: var(--accent2); }
                .log-close  {
                  background: none; border: 1px solid var(--border); color: var(--text-secondary);
                  border-radius: 3px; cursor: pointer; padding: 0.1rem 0.55rem; font-size: 0.78rem;
                }
                .log-close:hover  { border-color: var(--red); color: var(--red); }
                #build-log-output {
                  flex: 1; overflow-y: auto; padding: 0.6rem 1rem;
                  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
                  font-size: 0.78rem; line-height: 1.45; color: #c8d3f5;
                  white-space: pre-wrap; word-break: break-word;
                  background: #08080f;
                }
              </style>
            </head>
            <body>

            <header>
              <div class="header-brand">
                <h1>🦅 code-raptor</h1>
                <p id="project-count-header">Cross-project symbol &amp; Javadoc search</p>
              </div>

              <div class="search-forms">
                <!-- Symbol search → navigates to /search -->
                <div class="search-group">
                  <label>Symbol</label>
                  <input type="text" id="symbol-input" placeholder="e.g. addIIActor"
                         onkeydown="if(event.key==='Enter') goSearch()">
                  <select id="symbol-type">
                    <option value="references">references</option>
                    <option value="definition">definition</option>
                    <option value="grep">grep</option>
                    <option value="files">files</option>
                  </select>
                  <button class="btn btn-primary" onclick="goSearch()">Search</button>
                </div>

                <div class="separator"></div>

                <!-- Javadoc search (inline) -->
                <div class="search-group">
                  <label>Javadoc</label>
                  <input type="text" id="javadoc-input" placeholder="e.g. ActorRef tell"
                         onkeydown="if(event.key==='Enter') javadocSearch()">
                  <button class="btn btn-primary" onclick="javadocSearch()">Search</button>
                </div>

                <div class="separator"></div>

                <button class="btn" id="reindex-btn" onclick="doReindex(this)" title="Rebuild indexes">Reindex</button>
              </div>
            </header>

            <main>
              <div id="javadoc-section" style="display:none">
                <div class="section-label">
                  Javadoc results
                  <span id="javadoc-count" class="count-badge"></span>
                </div>
                <div id="javadoc-results"></div>
              </div>

              <!-- Project list -->
              <div id="projects-section">
                <div class="section-label">
                  Projects
                  <span id="projects-count" class="count-badge"></span>
                  <span style="margin-left:auto;display:flex;align-items:center;gap:0.5rem;">
                    <input type="text" id="project-filter" class="filter-input"
                           placeholder="filter…" oninput="filterProjects(this.value)">
                    <span style="font-size:0.75rem;text-transform:none;letter-spacing:0;color:var(--text-secondary);">per page:</span>
                    <select class="page-size" id="page-size-select" onchange="setPageSize(+this.value)">
                      <option value="20" selected>20</option>
                      <option value="50">50</option>
                      <option value="100">100</option>
                    </select>
                  </span>
                </div>
                <div id="projects-list"><div class="status-row">Loading…</div></div>
                <div id="projects-pager" class="pager" style="display:none">
                  <span class="pager-info" id="pager-info"></span>
                  <div class="pager-pages" id="pager-pages"></div>
                  <button class="btn" id="pager-prev" onclick="changePage(currentPage-1)">&#8249; Prev</button>
                  <button class="btn" id="pager-next" onclick="changePage(currentPage+1)">Next &#8250;</button>
                </div>
              </div>
            </main>

            <!-- Build log panel -->
            <div id="build-log-panel">
              <div class="log-header">
                <span class="log-title" id="log-title">Build output</span>
                <span class="log-status" id="log-status"></span>
                <button class="log-rerun" id="log-rerun-btn" onclick="rerunBuild()" style="display:none">&#9654; Re-run</button>
                <button class="log-close" onclick="closeLog()">&#10005; close</button>
              </div>
              <pre id="build-log-output"></pre>
            </div>

            <script>
              function esc(s) { return String(s??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
              function shortPath(p) { const m = p.match(/\\/works\\/(.+)/); return m ? m[1] : p; }
              function enc(s) { return encodeURIComponent(s); }
              function show(id) { document.getElementById(id).style.display = ''; }
              function hide(id) { document.getElementById(id).style.display = 'none'; }
              function setInner(id, html) { document.getElementById(id).innerHTML = html; }

              // ── Symbol search → navigate to /search page ──────────────────
              function goSearch() {
                const q    = document.getElementById('symbol-input').value.trim();
                const type = document.getElementById('symbol-type').value;
                if (!q) return;
                window.location.href = '/search?q=' + enc(q) + '&type=' + enc(type);
              }

              // ── Project list ───────────────────────────────────────────────
              let currentPage = 0, pageSize = 20, currentFilter = '', filterTimer = null;

              async function loadProjects(page, q) {
                page = (page === undefined) ? currentPage : page;
                q    = (q    === undefined) ? currentFilter : q;
                try {
                  const url = '/api/projects?page=' + page + '&size=' + pageSize + (q ? '&q=' + enc(q) : '');
                  const data = await (await fetch(url)).json();
                  currentPage = data.page; currentFilter = q;
                  document.getElementById('projects-count').textContent = data.total;
                  document.getElementById('project-count-header').textContent = data.total + ' project(s) indexed';
                  if (data.total === 0) {
                    setInner('projects-list', '<div class="status-row">' + (q ? 'No projects match &ldquo;' + esc(q) + '&rdquo;.' : 'No projects found.') + '</div>');
                    hide('projects-pager'); return;
                  }
                  const rows = data.items.map(p => {
                    let tags = '';
                    if (p.language) tags += '<span class="tag tag-lang">' + esc(p.language) + '</span>';
                    tags += p.buildSystem ? '<span class="tag tag-build">' + esc(p.buildSystem) + '</span>'
                                          : '<span class="tag tag-unknown">no build</span>';
                    if (p.compiledDocs) {
                      const label = p.compiledDocs.includes('apidocs') ? 'Javadoc'
                                  : p.compiledDocs.includes('target/doc') ? 'rustdoc'
                                  : (p.compiledDocs === 'doc/html' || p.compiledDocs === 'docs/html' || p.compiledDocs === 'html') ? 'doxygen'
                                  : 'docs';
                      tags += '<a class="tag tag-docs" style="text-decoration:none" href="/docs/' + enc(p.name) + '" target="_blank">' + label + '</a>';
                    }
                    const sub = (p.isMaven && p.groupId && p.artifactId)
                      ? '<span style="display:block;font-size:0.72rem;color:var(--text-secondary);font-family:monospace;font-weight:400;margin-top:1px;">' + esc(p.groupId) + ':' + esc(p.artifactId) + '</span>' : '';
                    const ideUrl = 'http://' + location.hostname + ':8080/?folder=' + enc('%%WORKS_DIR%%' + p.name);
                    const hasBuild = !!p.buildSystem;
                    // Maven/Gradle/Cargo ship a doc generator; for C/C++ build systems the
                    // Docs task runs Doxygen, auto-generating a Doxyfile when one is absent.
                    const hasDocs  = ['Maven', 'Gradle', 'Cargo'].includes(p.buildSystem)
                                  || ['Autotools', 'CMake', 'Make'].includes(p.buildSystem);
                    let actions = '';
                    if (hasBuild)
                      actions += '<button class="build-btn" data-project="' + esc(p.name) + '" data-task="build" data-bs="' + esc(p.buildSystem) + '" onclick="doBuild(this)">Build</button>';
                    if (hasDocs)
                      actions += '<button class="build-btn" data-project="' + esc(p.name) + '" data-task="docs" data-bs="' + esc(p.buildSystem) + '" onclick="doBuild(this)">Docs</button>';
                    return '<div class="project-row"><div class="project-name"><a href="' + ideUrl + '" target="code-server-ide">' + esc(p.name) + '</a>' + sub + '</div><div class="project-labels">' + tags + '</div>' + (actions ? '<div class="project-actions">' + actions + '</div>' : '') + '</div>';
                  }).join('');
                  setInner('projects-list', '<div class="project-list">' + rows + '</div>');
                  renderPager(data);
                } catch(e) {
                  setInner('projects-list', '<div class="status-row error">Failed: ' + esc(e.message) + '</div>');
                  hide('projects-pager');
                }
              }

              function renderPager(data) {
                const { total, page, size, totalPages } = data;
                if (totalPages <= 1) { hide('projects-pager'); return; }
                show('projects-pager');
                const from = page * size + 1, to = Math.min((page+1)*size, total);
                document.getElementById('pager-info').textContent = from + '\\u2013' + to + ' of ' + total;
                const slots = buildPageSlots(page, totalPages);
                document.getElementById('pager-pages').innerHTML = slots.map(s =>
                  s === '...' ? '<span class="pager-ellipsis">\\u2026</span>'
                              : '<button class="pager-page' + (s===page?' active':'') + '" onclick="changePage(' + s + ')">' + (s+1) + '</button>'
                ).join('');
                document.getElementById('pager-prev').disabled = (page === 0);
                document.getElementById('pager-next').disabled = (page >= totalPages-1);
              }

              function buildPageSlots(c, t) {
                if (t <= 7) return Array.from({length:t},(_,i)=>i);
                if (c <= 3) return [0,1,2,3,4,'...',t-1];
                if (c >= t-4) return [0,'...',t-5,t-4,t-3,t-2,t-1];
                return [0,'...',c-1,c,c+1,'...',t-1];
              }

              function changePage(n) { loadProjects(n, currentFilter); }
              function setPageSize(n) { pageSize = n; loadProjects(0, currentFilter); }
              function filterProjects(q) { clearTimeout(filterTimer); filterTimer = setTimeout(() => loadProjects(0, q), 300); }

              // ── Javadoc search ─────────────────────────────────────────────
              async function javadocSearch() {
                const q = document.getElementById('javadoc-input').value.trim();
                if (!q) return;
                show('javadoc-section');
                setInner('javadoc-results', '<div class="status-row">Searching…</div>');
                try {
                  const hits = await (await fetch('/api/javadoc?q=' + enc(q))).json();
                  document.getElementById('javadoc-count').textContent = hits.length;
                  if (hits.length === 0) { setInner('javadoc-results', '<div class="status-row">No results found.</div>'); return; }
                  setInner('javadoc-results', '<div class="result-list">' + hits.map(h =>
                    '<div class="result-row"><div class="hit-title">' + esc(h.title) + '</div>' +
                    '<div class="hit-url">' + esc(shortPath(h.url)) + '</div>' +
                    (h.snippet ? '<div class="hit-snippet">' + esc(h.snippet.slice(0,200)) + '</div>' : '') +
                    '</div>'
                  ).join('') + '</div>');
                } catch(e) {
                  setInner('javadoc-results', '<div class="status-row error">Error: ' + esc(e.message) + '</div>');
                }
              }

              // ── Reindex ────────────────────────────────────────────────────
              async function doReindex(btn) {
                btn.disabled = true; btn.textContent = 'Reindexing…';
                try {
                  await fetch('/api/reindex', {method:'POST'});
                  btn.textContent = 'Done ✓';
                  setTimeout(() => { btn.textContent = 'Reindex'; btn.disabled = false; loadProjects(0, currentFilter); }, 2000);
                } catch(e) {
                  btn.textContent = 'Error';
                  setTimeout(() => { btn.textContent = 'Reindex'; btn.disabled = false; }, 3000);
                }
              }

              // ── Build panel ──────────────────────────────────────────────────
              let logBuildId  = null;
              let lastBuildBtn = null;

              function doBuild(btn) {
                const project = btn.dataset.project;
                const task    = btn.dataset.task;
                const bs      = btn.dataset.bs;

                // Always start a fresh build (whether first time or re-clicking ✓/✗)
                btn.dataset.done = '';
                btn.disabled = true;
                btn.textContent = '\\u2026';
                btn.classList.remove('build-btn-ok', 'build-btn-err');
                btn.classList.add('build-btn-running');
                lastBuildBtn = btn;
                document.getElementById('log-rerun-btn').style.display = 'none';

                fetch('/api/build?project=' + enc(project) + '&task=' + enc(task) + '&buildSystem=' + enc(bs),
                      {method: 'POST'})
                  .then(r => r.json())
                  .then(({buildId, error}) => {
                    if (error) throw new Error(error);
                    btn.dataset.buildId = buildId;
                    openLog(project, task, buildId, null);
                    schedulePoll(btn, buildId);
                  })
                  .catch(e => {
                    btn.disabled = false;
                    btn.textContent = btn.dataset.origLabel || task;
                    btn.classList.remove('build-btn-running');
                    openLog(project, task, '?', {running: false, exitCode: -1,
                      output: 'Failed to start build: ' + e.message});
                  });
              }

              function schedulePoll(btn, buildId) {
                setTimeout(() => {
                  fetch('/api/build/' + buildId)
                    .then(r => r.json())
                    .then(s => {
                      if (logBuildId === buildId) {
                        const out = document.getElementById('build-log-output');
                        out.textContent = s.output || '(no output yet)';
                        out.scrollTop = out.scrollHeight;
                      }
                      if (s.running) {
                        schedulePoll(btn, buildId);
                      } else {
                        finishBuild(btn, s);
                      }
                    })
                    .catch(() => schedulePoll(btn, buildId));
                }, 1500);
              }

              function finishBuild(btn, s) {
                btn.disabled = false;
                btn.classList.remove('build-btn-running');
                // Derive label from data-task so re-runs never accumulate ✓/✗ prefixes
                const label = btn.dataset.task.charAt(0).toUpperCase() + btn.dataset.task.slice(1);
                if (s.exitCode === 0) {
                  btn.textContent = '\\u2713 ' + label;
                  btn.classList.add('build-btn-ok');
                } else {
                  btn.textContent = '\\u2717 ' + label;
                  btn.classList.add('build-btn-err');
                }
                btn.dataset.done = '1';
                if (logBuildId === s.id) {
                  document.getElementById('log-status').textContent =
                    s.exitCode === 0 ? '\\u2713 done (exit 0)' : '\\u2717 failed (exit ' + s.exitCode + ')';
                  const out = document.getElementById('build-log-output');
                  out.textContent = s.output || '(no output)';
                  out.scrollTop = out.scrollHeight;
                  document.getElementById('log-rerun-btn').style.display = '';
                }
                // After successful docs build: reindex then refresh project list
                // so the Javadoc tag appears immediately without a manual page reload.
                if (s.exitCode === 0 && btn.dataset.task === 'docs') {
                  fetch('/api/reindex', {method: 'POST'})
                    .then(() => loadProjects(currentPage, currentFilter));
                }
              }

              function openLog(project, task, buildId, status) {
                logBuildId = buildId;
                document.getElementById('log-title').textContent = project + ' \\u203a ' + task;
                const statusEl = document.getElementById('log-status');
                const rerunBtn = document.getElementById('log-rerun-btn');
                if (status) {
                  statusEl.textContent = status.running ? 'running\\u2026'
                    : status.exitCode === 0 ? '\\u2713 done (exit 0)'
                    : '\\u2717 failed (exit ' + status.exitCode + ')';
                  document.getElementById('build-log-output').textContent =
                    status.output || '(no output)';
                  rerunBtn.style.display = status.running ? 'none' : '';
                } else {
                  statusEl.textContent = 'starting\\u2026';
                  document.getElementById('build-log-output').textContent = '';
                  rerunBtn.style.display = 'none';
                }
                document.getElementById('build-log-panel').style.display = 'flex';
              }

              function closeLog() {
                document.getElementById('build-log-panel').style.display = 'none';
              }

              function rerunBuild() {
                if (!lastBuildBtn) return;
                lastBuildBtn.dataset.done = '';
                lastBuildBtn.textContent = lastBuildBtn.dataset.origLabel || lastBuildBtn.dataset.task;
                lastBuildBtn.classList.remove('build-btn-ok', 'build-btn-err');
                document.getElementById('log-rerun-btn').style.display = 'none';
                doBuild(lastBuildBtn);
              }

              loadProjects(0, '');
            </script>
            </body>
            </html>
            """;

    // ── Symbol search results page (/search) ──────────────────────────────────
    private static final String SEARCH_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Search — code-raptor</title>
              <link rel="icon" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>🦅</text></svg>">
              <style>
            """ + COMMON_CSS + """
                main { max-width: 1200px; }

                /* ── Result summary bar ── */
                .result-summary {
                  display: flex; align-items: baseline; gap: 0.75rem; flex-wrap: wrap;
                  padding: 0.6rem 0; margin-bottom: 1.5rem;
                  border-bottom: 1px solid var(--border);
                }
                .result-count { font-weight: 700; font-size: 1.05rem; }
                .result-query { color: var(--accent); font-family: 'SFMono-Regular', Consolas, monospace; font-size: 1rem; }
                .result-type-badge {
                  font-size: 0.72rem; padding: 0.12rem 0.5rem; border-radius: 3px;
                  background: var(--bg-tertiary); color: var(--accent2);
                  border: 1px solid var(--border); font-family: monospace;
                }
                .result-range { color: var(--text-secondary); font-size: 0.82rem; margin-left: auto; }

                /* ── File group (OpenGrok style) ── */
                .file-group { margin-bottom: 1.5rem; border-radius: 4px; overflow: hidden; }

                .file-header {
                  display: flex; align-items: center; gap: 1rem;
                  padding: 0.45rem 1rem;
                  background: var(--bg-secondary);
                  border-left: 3px solid var(--accent2);
                  border-bottom: 1px solid var(--border);
                  border-radius: 4px 4px 0 0;
                }
                .file-path {
                  font-family: 'SFMono-Regular', Consolas, monospace;
                  color: var(--accent2); font-size: 0.875rem; font-weight: 600;
                  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
                  text-decoration: none;
                }
                a.file-path:hover { text-decoration: underline; }
                .file-project {
                  color: var(--accent3); font-weight: 700;
                }
                .file-path-rest { color: var(--text-secondary); font-weight: 400; }
                .match-count {
                  color: var(--text-secondary); font-size: 0.72rem;
                  background: var(--bg-tertiary); border: 1px solid var(--border);
                  border-radius: 3px; padding: 0.05rem 0.4rem;
                  white-space: nowrap; font-family: monospace;
                }

                /* ── Code lines table ── */
                .result-lines {
                  border: 1px solid var(--border); border-top: none;
                  border-radius: 0 0 4px 4px;
                  background: var(--bg-primary);
                  overflow: hidden;
                }
                .result-line {
                  display: flex; align-items: stretch;
                  border-bottom: 1px solid rgba(88,91,112,0.25);
                }
                .result-line:last-child { border-bottom: none; }
                .result-line:hover { background: var(--bg-secondary); }

                .line-num {
                  min-width: 4.5rem; text-align: right;
                  padding: 0.25rem 0.75rem;
                  color: var(--text-secondary);
                  font-family: 'SFMono-Regular', Consolas, monospace; font-size: 0.8rem;
                  text-decoration: none; user-select: none;
                  border-right: 1px solid rgba(88,91,112,0.4);
                  background: rgba(49,50,68,0.5); flex-shrink: 0;
                  display: flex; align-items: center; justify-content: flex-end;
                }
                .line-num:hover { color: var(--accent2); text-decoration: underline; }

                .line-code {
                  padding: 0.25rem 1rem;
                  font-family: 'SFMono-Regular', Consolas, monospace; font-size: 0.82rem;
                  white-space: pre; overflow: hidden; text-overflow: ellipsis;
                  color: var(--text-primary); flex: 1; align-self: center;
                }

                mark.hl {
                  background: rgba(249,226,175,0.22); color: var(--yellow);
                  border-radius: 2px; padding: 0.05em 0.1em;
                  font-style: normal;
                }

                /* ── Pagination ── */
                .pagination {
                  display: flex; align-items: center; gap: 0.4rem;
                  justify-content: center; margin: 2.5rem 0 1.5rem; flex-wrap: wrap;
                }
                .page-btn {
                  padding: 0.3rem 0.75rem; border-radius: 4px;
                  border: 1px solid var(--border);
                  background: var(--bg-secondary); color: var(--text-primary);
                  font-size: 0.82rem; cursor: pointer; text-decoration: none;
                  display: inline-block;
                }
                .page-btn:hover:not([disabled]):not(.active) { border-color: var(--accent2); color: var(--accent2); }
                .page-btn.active { background: var(--accent2); color: var(--bg-primary); border-color: var(--accent2); font-weight: 700; cursor: default; }
                .page-btn[disabled] { opacity: 0.35; cursor: not-allowed; pointer-events: none; }
                .page-ellipsis { padding: 0.3rem 0.3rem; color: var(--text-secondary); font-size: 0.85rem; align-self: center; }

                .no-results { color: var(--text-secondary); padding: 2rem 0; text-align: center; font-size: 0.9rem; }
                .loading { color: var(--text-secondary); padding: 2rem 0; text-align: center; }
              </style>
            </head>
            <body>

            <header>
              <div class="header-brand">
                <h1><a href="/">🦅 code-raptor</a></h1>
              </div>

              <div class="search-forms">
                <div class="search-group">
                  <label>Symbol</label>
                  <input type="text" id="q" placeholder="e.g. addIIActor"
                         onkeydown="if(event.key==='Enter') doSearch()">
                  <select id="type">
                    <option value="references">references</option>
                    <option value="definition">definition</option>
                    <option value="grep">grep</option>
                    <option value="files">files</option>
                  </select>
                  <button class="btn btn-primary" onclick="doSearch()">Search</button>
                </div>
              </div>
            </header>

            <main>
              <div id="summary" class="loading">Loading…</div>
              <div id="results"></div>
              <div id="pagination"></div>
            </main>

            <script>
              const PAGE_SIZE = 50;
              let allHits = [], currentPage = 0, searchQuery = '', searchType = '';

              function esc(s) { return String(s??'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }
              function enc(s) { return encodeURIComponent(s); }

              async function init() {
                const p = new URLSearchParams(location.search);
                searchQuery = p.get('q') || '';
                searchType  = p.get('type') || 'references';
                currentPage = parseInt(p.get('page') || '0');

                if (!searchQuery) { window.location.href = '/'; return; }

                // Prefill search form
                document.getElementById('q').value = searchQuery;
                const sel = document.getElementById('type');
                for (let o of sel.options) if (o.value === searchType) { o.selected = true; break; }
                document.title = '"' + searchQuery + '" — code-raptor';

                document.getElementById('summary').innerHTML = '<span class="loading">Searching…</span>';

                try {
                  const res = await fetch('/api/symbol?q=' + enc(searchQuery) + '&type=' + enc(searchType));
                  if (!res.ok) throw new Error('HTTP ' + res.status);
                  allHits = await res.json();
                  renderPage(currentPage);
                } catch(e) {
                  document.getElementById('summary').innerHTML =
                    '<span style="color:var(--red)">Error: ' + esc(e.message) + '</span>';
                }
              }

              function doSearch() {
                const q = document.getElementById('q').value.trim();
                const t = document.getElementById('type').value;
                if (!q) return;
                window.location.href = '/search?q=' + enc(q) + '&type=' + enc(t);
              }

              function renderPage(page) {
                currentPage = page;
                const total      = allHits.length;
                const totalPages = Math.ceil(total / PAGE_SIZE) || 1;
                // Clamp page
                if (page >= totalPages) page = currentPage = totalPages - 1;

                const from     = page * PAGE_SIZE;
                const to       = Math.min(from + PAGE_SIZE, total);
                const pageHits = allHits.slice(from, to);

                // Update URL (no reload)
                const url = '/search?q=' + enc(searchQuery) + '&type=' + enc(searchType)
                          + (page > 0 ? '&page=' + page : '');
                history.replaceState(null, '', url);

                // Summary
                const typeLabel = searchType || 'references';
                document.getElementById('summary').innerHTML =
                  '<span class="result-count">' + total + '</span> result' + (total !== 1 ? 's' : '') +
                  ' for <span class="result-query">' + esc(searchQuery) + '</span>' +
                  ' &nbsp;<span class="result-type-badge">' + esc(typeLabel) + '</span>' +
                  (totalPages > 1
                    ? '<span class="result-range">page ' + (page+1) + ' / ' + totalPages +
                      ' &nbsp;(' + (from+1) + '–' + to + ')</span>'
                    : '');

                // ── Group by file ──────────────────────────────────────────
                const byFile = new Map();
                for (const h of pageHits) {
                  if (!byFile.has(h.file)) byFile.set(h.file, []);
                  byFile.get(h.file).push(h);
                }

                const WORKS   = '%%WORKS_DIR%%';
                const ideHost = location.hostname + ':8080';
                const ideBase = 'http://' + ideHost;

                function makeIdeUrl(h) {
                  const abs    = h.file.startsWith(WORKS) ? h.file : WORKS + h.file;
                  const fUri   = 'vscode-remote://' + ideHost + abs + ':' + h.line;
                  const pl     = JSON.stringify([['openFile', fUri], ['gotoLineMode', 'true']]);
                  return ideBase + '/?folder=' + enc(WORKS.slice(0,-1)) + '&payload=' + enc(pl);
                }

                function highlightTerm(escapedCode, term) {
                  if (!term || term.length < 2) return escapedCode;
                  // Escape regex special chars in the search term
                  const re = new RegExp(term.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&'), 'gi');
                  return escapedCode.replace(re, '<mark class="hl">$&</mark>');
                }

                // Split path into project / rest for visual emphasis
                function splitPath(file) {
                  const rel = file.startsWith('%%WORKS_DIR%%') ? file.slice('%%WORKS_DIR%%'.length) : file;
                  const slash = rel.indexOf('/');
                  if (slash < 0) return { project: rel, rest: '' };
                  return { project: rel.slice(0, slash), rest: rel.slice(slash) };
                }

                let html = '';
                if (pageHits.length === 0) {
                  html = '<div class="no-results">No results found for <em>' + esc(searchQuery) + '</em>.</div>';
                } else {
                  for (const [file, lines] of byFile) {
                    const { project, rest } = splitPath(file);
                    html += '<div class="file-group">';
                    // File header
                    const firstUrl = makeIdeUrl(lines[0]);
                    html += '<div class="file-header">';
                    html += '<a class="file-path" href="' + firstUrl + '" target="code-server-ide">'
                          + '<span class="file-project">' + esc(project) + '</span>'
                          + '<span class="file-path-rest">' + esc(rest) + '</span>'
                          + '</a>';
                    html += '<span class="match-count">' + lines.length + ' match' + (lines.length > 1 ? 'es' : '') + '</span>';
                    html += '</div>';
                    // Code lines
                    html += '<div class="result-lines">';
                    for (const h of lines) {
                      const ideUrl = makeIdeUrl(h);
                      const code   = highlightTerm(esc(h.content.trim()), searchQuery);
                      html += '<div class="result-line">';
                      html += '<a class="line-num" href="' + ideUrl + '" target="code-server-ide">' + h.line + '</a>';
                      html += '<span class="line-code">' + code + '</span>';
                      html += '</div>';
                    }
                    html += '</div></div>';
                  }
                }
                document.getElementById('results').innerHTML = html;

                // ── Pagination ─────────────────────────────────────────────
                if (totalPages <= 1) {
                  document.getElementById('pagination').innerHTML = '';
                } else {
                  const slots = buildPageSlots(page, totalPages);
                  let pg = '<div class="pagination">';
                  pg += '<button class="page-btn" onclick="renderPage(' + (page-1) + ')"'
                      + (page === 0 ? ' disabled' : '') + '>&#8249; Prev</button>';
                  for (const s of slots) {
                    if (s === '...') {
                      pg += '<span class="page-ellipsis">…</span>';
                    } else {
                      pg += '<button class="page-btn' + (s === page ? ' active' : '') + '" onclick="renderPage(' + s + ')">'
                          + (s + 1) + '</button>';
                    }
                  }
                  pg += '<button class="page-btn" onclick="renderPage(' + (page+1) + ')"'
                      + (page >= totalPages-1 ? ' disabled' : '') + '>Next &#8250;</button>';
                  pg += '</div>';
                  document.getElementById('pagination').innerHTML = pg;
                }

                window.scrollTo(0, 0);
              }

              function buildPageSlots(c, t) {
                if (t <= 9) return Array.from({length:t}, (_, i) => i);
                if (c <= 4) return [0,1,2,3,4,5,'...',t-1];
                if (c >= t-5) return [0,'...',t-6,t-5,t-4,t-3,t-2,t-1];
                return [0,'...',c-2,c-1,c,c+1,c+2,'...',t-1];
              }

              init();
            </script>
            </body>
            </html>
            """;

    @Inject
    CodeRaptorConfig config;

    /**
     * The configured works directory as a JS string prefix, with a trailing slash so it can be
     * prepended to a project-relative path. Injected into the page so the UI never hardcodes a
     * user-specific absolute path (the {@code %%WORKS_DIR%%} token in the HTML is replaced here).
     */
    private String worksDirPrefix() {
        return config.getWorksDir().toString() + "/";
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String portal() {
        return PORTAL_HTML.replace("%%WORKS_DIR%%", worksDirPrefix());
    }

    @GET
    @Path("search")
    @Produces(MediaType.TEXT_HTML)
    public String search() {
        return SEARCH_HTML.replace("%%WORKS_DIR%%", worksDirPrefix());
    }
}
