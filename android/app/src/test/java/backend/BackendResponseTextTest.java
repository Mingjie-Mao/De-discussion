package backend;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Guards the queue against rendering "null" to an administrator.
 *
 * <p>Half of a moderation case is nullable by design: a comment has no title,
 * and a case that has not been analysed yet has no engine, decision or
 * rationale. {@code JSONObject.optString(name, fallback)} does not cover that —
 * it substitutes the fallback only for an absent key — so these are the shapes
 * the parser actually has to survive.
 */
public class BackendResponseTextTest {

    @Test
    public void aJsonNullReadsAsAbsentRatherThanAsTheWordNull() throws JSONException {
        JSONObject content = new JSONObject();
        content.put("title", JSONObject.NULL);
        content.put("body", "I agree, especially if the drawer only handles community switching.");

        assertEquals("Reported content", BackendModerationGateway.text(content, "title", "Reported content"));
        assertEquals(
                "I agree, especially if the drawer only handles community switching.",
                BackendModerationGateway.text(content, "body", ""));
    }

    @Test
    public void aMissingKeyFallsBack() throws JSONException {
        assertEquals("Reported content", BackendModerationGateway.text(new JSONObject(), "title", "Reported content"));
    }

    @Test
    public void aBlankValueFallsBack() throws JSONException {
        JSONObject moderationCase = new JSONObject();
        moderationCase.put("rationale", "   ");

        assertEquals("", BackendModerationGateway.text(moderationCase, "rationale", ""));
    }

    @Test
    public void arealValueSurvivesUntouched() throws JSONException {
        JSONObject moderationCase = new JSONObject();
        moderationCase.put("engine", "gemini-3.5-flash-lite/v2");

        assertEquals(
                "gemini-3.5-flash-lite/v2", BackendModerationGateway.text(moderationCase, "engine", ""));
    }

    /** An unanalysed case: everything the verdict block reads is null at once. */
    @Test
    public void anUnanalysedCaseRendersFallbacksThroughout() throws JSONException {
        JSONObject moderationCase = new JSONObject();
        moderationCase.put("status", "QUEUED");
        moderationCase.put("engine", JSONObject.NULL);
        moderationCase.put("recommendedDecision", JSONObject.NULL);
        moderationCase.put("rationale", JSONObject.NULL);

        assertEquals("QUEUED", BackendModerationGateway.text(moderationCase, "status", ""));
        assertEquals("", BackendModerationGateway.text(moderationCase, "engine", ""));
        assertEquals("", BackendModerationGateway.text(moderationCase, "recommendedDecision", ""));
        assertEquals("", BackendModerationGateway.text(moderationCase, "rationale", ""));
    }
}
