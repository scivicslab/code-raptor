package com.scivicslab.coderaptor.resource;

import com.scivicslab.coderaptor.activity.ServedLog;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Answers what this instance is working on ({@code ActivitySummary_260905_oo01}).
 *
 * <p>No model is asked. What this instance serves is Javadoc and source, and a Javadoc page is
 * already named in words a reader knows — the class and its package. The last thing asked for is
 * the answer, and the ones before it are the parts.</p>
 *
 * <p>Because nothing is inferred, every request is answered from the list as it stands. There is
 * nothing to hold.</p>
 */
@Path("/api/activity")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class ActivityResource {

    @Inject
    ServedLog servedLog;

    /**
     * Returns what this instance is working on.
     *
     * @return {@code {summary, asOf, parts}}
     */
    @GET
    public Map<String, Object> activity() {
        List<ServedLog.Served> recent = servedLog.recent();

        List<Map<String, String>> parts = new ArrayList<>();
        for (ServedLog.Served s : recent) {
            Map<String, String> part = new LinkedHashMap<>();
            part.put("name", s.key());
            part.put("summary", s.label());
            parts.add(part);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", recent.isEmpty()
                ? "索引はあるが、まだ誰も引いていない。"
                : recent.get(0).label());
        out.put("asOf", recent.isEmpty() ? Instant.now().toString()
                                         : Instant.ofEpochMilli(recent.get(0).at()).toString());
        out.put("parts", parts);
        return out;
    }
}
