package backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackendExceptionTest {

    /**
     * The backend answers failures with an RFC 7807 problem detail. Its
     * {@code detail} is written to be read by a person — "You have already
     * reported this content." — and is the difference between a useful toast and
     * one that says 409.
     */
    @Test
    public void prefersTheProblemDetailOverTheStatus() {
        BackendException error = BackendException.fromResponse(
                409, "{\"title\":\"Conflicting request\",\"detail\":\"You have already reported this content.\"}");

        assertEquals("You have already reported this content.", error.getMessage());
        assertEquals(409, error.status());
        assertTrue(error.isConflict());
    }

    @Test
    public void fallsBackToTheRawBodyWhenItIsNotAProblemDetail() {
        BackendException error = BackendException.fromResponse(502, "upstream is down");

        assertEquals("upstream is down", error.getMessage());
        assertEquals(502, error.status());
    }

    @Test
    public void fallsBackToTheStatusWhenThereIsNoBody() {
        BackendException error = BackendException.fromResponse(500, "");

        assertTrue(error.getMessage().contains("500"));
        assertFalse(error.isUnreachable());
    }

    @Test
    public void aProblemDetailWithoutADetailFieldStillReportsSomething() {
        BackendException error = BackendException.fromResponse(400, "{\"title\":\"Bad request\"}");

        assertFalse(error.getMessage().isEmpty());
        assertEquals(400, error.status());
    }

    /**
     * A request that never arrived carries status 0. The queue and the report
     * flow tell "the server said no" apart from "there is no server" on this.
     */
    @Test
    public void aRequestThatNeverArrivedIsUnreachableRatherThanRejected() {
        BackendException error = new BackendException("Could not reach it.", new RuntimeException("connect"));

        assertTrue(error.isUnreachable());
        assertFalse(error.isConflict());
        assertFalse(error.isNotFound());
        assertFalse(error.isUnauthorised());
    }

    @Test
    public void gatewayAndTransportFailuresKeepTheOriginalWriteRequest() {
        for (int status : new int[]{0, 500, 502, 503, 504})
            assertTrue(new BackendException(status, "Failed").hasAmbiguousWriteOutcome());
        for (int status : new int[]{400, 401, 403, 404, 409, 429})
            assertFalse(new BackendException(status, "Rejected").hasAmbiguousWriteOutcome());
    }

    @Test
    public void recognisesTheStatusesTheIntegrationBranchesOn() {
        assertTrue(BackendException.fromResponse(401, "").isUnauthorised());
        assertTrue(BackendException.fromResponse(403, "").isUnauthorised());
        assertTrue(BackendException.fromResponse(404, "").isNotFound());
        assertTrue(BackendException.fromResponse(409, "").isConflict());
    }
}
