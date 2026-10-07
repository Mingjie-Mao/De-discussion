package backend;

import org.junit.Test;
import static org.junit.Assert.*;

public class BackendLogRouteTest {
    @Test public void logsOnlyTemplatesWithoutQueryOrIdentifiers() {
        assertEquals("/api/posts/{id}/comments", BackendClient.logRoute(
                "/api/posts/11111111-1111-1111-1111-111111111111/comments?cursor=secret&token=secret"));
        assertEquals("/api/posts/authors/{id}", BackendClient.logRoute(
                "/api/posts/authors/11111111-1111-1111-1111-111111111111?size=30"));
        assertEquals("other", BackendClient.logRoute("/api/users/private-email@example.com"));
        assertEquals("other", BackendClient.logRoute(null));
    }
}
