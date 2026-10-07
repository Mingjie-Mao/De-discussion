package backend;

import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class BackendAdminApiTest {
    static final String ID = BackendAdminSessionTest.ID;
    record Call(String method, String path, JSONObject body) {}
    static class Fixture extends BackendAdminSessionTest.Fake {
        final List<Call> requests = new ArrayList<>();
        String response = "[]";
        @Override public String call(String o, String m, String p, String t, JSONObject body) {
            if (p.startsWith("/api/admin/")) { requests.add(new Call(m, p, body)); return response; }
            return super.call(o, m, p, t, body);
        }
    }
    private BackendAdminApi api(Fixture fixture) {
        BackendAdminSession session = new BackendAdminSession(BackendAdminSessionTest.config(), fixture);
        session.login("admin", "password"); return new BackendAdminApi(session);
    }
    @Test public void queuesRequestBoundedPagesAndAllRealStatuses() {
        Fixture fixture = new Fixture(); BackendAdminApi api = api(fixture);
        api.cases("AWAITING_REVIEW", 2); api.cases("QUEUED", 0); api.cases("ANALYSING", 0); api.appeals("PENDING", 1);
        assertTrue(fixture.requests.get(0).path.contains("size=30&page=2"));
        assertTrue(fixture.requests.get(3).path.contains("/api/admin/appeals?status=PENDING&size=30&page=1"));
        assertThrows(IllegalArgumentException.class, () -> api.cases("UNKNOWN", 0));
        assertThrows(IllegalArgumentException.class, () -> api.cases("RESOLVED", -1));
    }
    @Test public void oversizedQueueResponseIsRejected() {
        Fixture fixture = new Fixture(); BackendAdminApi api = api(fixture);
        fixture.response = "[" + "{},".repeat(30) + "{}]";
        assertThrows(BackendException.class, () -> api.cases("RESOLVED", 0));
    }
    @Test public void detailPreservesReportedSnapshotCurrentEvidenceAndAudit() {
        Fixture fixture = new Fixture(); BackendAdminApi api = api(fixture);
        fixture.response = BackendAdminSession.body("content", BackendAdminSession.body("title", JSONObject.NULL, "body", "reported text"),
                "currentContent", BackendAdminSession.body("body", "edited text"), "contentChanged", true,
                "auditTrail", new org.json.JSONArray().put(BackendAdminSession.body("action", "CASE_ANALYSED"))).toString();
        JSONObject detail = api.detail(ID);
        assertEquals("reported text", detail.optJSONObject("content").optString("body"));
        assertTrue(detail.optJSONObject("content").isNull("title"));
        assertEquals("edited text", detail.optJSONObject("currentContent").optString("body"));
        assertEquals("CASE_ANALYSED", detail.optJSONArray("auditTrail").optJSONObject(0).optString("action"));
    }
    @Test public void claimReleaseAndAllDecisionsUseCanonicalCaseApi() {
        Fixture fixture = new Fixture(); BackendAdminApi api = api(fixture); fixture.response = "{}";
        api.claim(ID); api.release(ID);
        assertEquals("POST", fixture.requests.get(0).method); assertTrue(fixture.requests.get(0).path.endsWith("/assignment"));
        assertEquals("DELETE", fixture.requests.get(1).method); assertEquals("GET", fixture.requests.get(2).method);
        for (String action : new String[]{"NONE", "HIDE", "DELETE", "BAN"}) {
            api.decide(ID, action, "Administrator note");
            Call call = fixture.requests.get(fixture.requests.size() - 1);
            assertEquals(action, call.body.optString("action")); assertEquals("Administrator note", call.body.optString("note"));
        }
        assertThrows(IllegalArgumentException.class, () -> api.decide(ID, "ALLOW", ""));
        assertThrows(IllegalArgumentException.class, () -> api.decide(ID, "HIDE", "x".repeat(1001)));
    }
    @Test public void appealDecisionsSendResponseAndLeaveReversalToServer() {
        Fixture fixture = new Fixture(); BackendAdminApi api = api(fixture); fixture.response = "{}";
        api.decideAppeal(ID, "UPHOLD", "Reason to uphold"); api.decideAppeal(ID, "OVERTURN", "Reason to restore");
        Call last = fixture.requests.get(1);
        assertEquals("POST", last.method); assertEquals("/api/admin/appeals/" + ID + "/decision", last.path);
        assertEquals("OVERTURN", last.body.optString("decision")); assertEquals("Reason to restore", last.body.optString("response"));
        assertThrows(IllegalArgumentException.class, () -> api.decideAppeal(ID, "NONE", ""));
        assertThrows(IllegalArgumentException.class, () -> api.decideAppeal(ID, "UPHOLD", "x".repeat(2001)));
    }
    @Test public void conflictingReviewerDoesNotRefreshOrLoseLogin() {
        Fixture fixture = new Fixture() {
            @Override public String call(String o, String m, String p, String t, JSONObject b) {
                if (p.endsWith("/decision")) throw new BackendException(409, "Assigned to another reviewer");
                return super.call(o, m, p, t, b);
            }
        };
        BackendAdminSession session = new BackendAdminSession(BackendAdminSessionTest.config(), fixture);
        session.login("admin", "password");
        assertEquals(409, assertThrows(BackendException.class, () -> new BackendAdminApi(session).decide(ID, "HIDE", "")).status());
        assertTrue(session.hasSession()); assertEquals(0, fixture.refreshes.get());
    }
}
