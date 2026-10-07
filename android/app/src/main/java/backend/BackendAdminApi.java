package backend;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.UUID;

/** The same administrator REST contracts used by the browser console. All queues are bounded. */
public final class BackendAdminApi {
    public static final int PAGE_SIZE = 30;
    private final BackendAdminSession session;
    public BackendAdminApi(BackendAdminSession session) { this.session = session; }
    public JSONArray cases(String status, int page) {
        if (!Arrays.asList("AWAITING_REVIEW", "RESOLVED", "QUEUED", "ANALYSING").contains(status)) throw new IllegalArgumentException("Case status required.");
        return array(session.request("GET", "/api/admin/moderation-cases?status=" + status + paging(page), null));
    }
    public JSONObject detail(String id) { return object("GET", casePath(id), null); }
    public JSONObject translations(JSONArray ids) {
        if (ids.length() > PAGE_SIZE) throw new IllegalArgumentException("Oversized translation batch.");
        return object("POST", "/api/admin/translations", BackendAdminSession.body("language", "zh-CN", "caseIds", ids));
    }
    public JSONObject claim(String id) { return object("POST", casePath(id) + "/assignment", BackendAdminSession.body()); }
    public JSONObject release(String id) {
        // DELETE returns the canonical detail, but fetching it also handles older 204 responses.
        session.request("DELETE", casePath(id) + "/assignment", null);
        return detail(id);
    }
    public JSONObject decide(String id, String action, String note) {
        if (!Arrays.asList("NONE", "HIDE", "DELETE", "BAN").contains(action)) throw new IllegalArgumentException("Invalid action.");
        limit(note, 1000);
        return object("POST", casePath(id) + "/decision", BackendAdminSession.body("action", action, "note", note));
    }
    public JSONArray appeals(String status, int page) {
        if (!Arrays.asList("PENDING", "UPHELD", "OVERTURNED").contains(status)) throw new IllegalArgumentException("Appeal status required.");
        return array(session.request("GET", "/api/admin/appeals?status=" + status + paging(page), null));
    }
    public JSONObject decideAppeal(String id, String decision, String response) {
        if (!Arrays.asList("UPHOLD", "OVERTURN").contains(decision)) throw new IllegalArgumentException("Invalid appeal decision.");
        limit(response, 2000);
        return object("POST", "/api/admin/appeals/" + uuid(id) + "/decision",
                BackendAdminSession.body("decision", decision, "response", response));
    }
    public byte[] image(String path) { return session.image(path); }
    private JSONObject object(String method, String path, JSONObject body) { return BackendAdminSession.parse(session.request(method, path, body)); }
    private static String paging(int page) {
        if (page < 0 || page > 1_000_000) throw new IllegalArgumentException("Invalid page.");
        return "&size=" + PAGE_SIZE + "&page=" + page + "&sort=createdAt,asc";
    }
    private static String casePath(String id) { return "/api/admin/moderation-cases/" + uuid(id); }
    private static String uuid(String id) { return UUID.fromString(id).toString(); }
    private static void limit(String text, int max) {
        if (text != null && text.length() > max) throw new IllegalArgumentException("Text exceeds " + max + " characters.");
    }
    private static JSONArray array(String raw) {
        try {
            JSONArray result = new JSONArray(raw);
            if (result.length() > PAGE_SIZE) throw new IllegalArgumentException("Oversized queue response.");
            return result;
        } catch (Exception error) { throw new BackendException(BackendText.malformedResponse(), error); }
    }
}
