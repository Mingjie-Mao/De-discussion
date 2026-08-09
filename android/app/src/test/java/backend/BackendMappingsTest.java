package backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class BackendMappingsTest {

    private final BackendConfigTest.MemoryStore store = new BackendConfigTest.MemoryStore();
    private final BackendConfig config = new BackendConfig(store);
    private final BackendMappings mappings = new BackendMappings(store, config);

    private static BackendReportTarget targetFor(UUID postId, UUID commentId) {
        return new BackendReportTarget(
                new BackendReportTarget.PostSnapshot(postId, UUID.randomUUID(), "anu", "Title", "Body"),
                List.of(new BackendReportTarget.CommentSnapshot(commentId, UUID.randomUUID(), "Body")),
                BackendReportTarget.TargetType.COMMENT,
                commentId);
    }

    @Test
    public void mapsLocalContentToBackendContentInBothDirections() {
        UUID localPost = UUID.randomUUID();
        UUID remotePost = UUID.randomUUID();
        UUID localComment = UUID.randomUUID();
        UUID remoteComment = UUID.randomUUID();

        mappings.putPost(localPost, remotePost);
        mappings.putComment(localComment, remoteComment);

        assertEquals(remotePost, mappings.remotePost(localPost));
        assertEquals(remoteComment, mappings.remoteComment(localComment));
        // The reverse direction is what lets the admin queue match a case back to
        // the comment sitting in the local feed.
        assertEquals(localPost, mappings.localForRemote(remotePost));
        assertEquals(localComment, mappings.localForRemote(remoteComment));
    }

    @Test
    public void unknownContentMapsToNothing() {
        assertNull(mappings.remotePost(UUID.randomUUID()));
        assertNull(mappings.remoteComment(UUID.randomUUID()));
        assertNull(mappings.localForRemote(UUID.randomUUID()));
    }

    /**
     * The recovery path for a mapping that has gone stale — the backend database
     * was recreated, or a HIDE decision soft-deleted the mirror. Clearing has to
     * remove the reverse entry too, or the queue would keep resolving a case to
     * a mapping that no longer exists in the forward direction.
     */
    @Test
    public void clearingATargetForgetsBothDirections() {
        UUID localPost = UUID.randomUUID();
        UUID remotePost = UUID.randomUUID();
        UUID localComment = UUID.randomUUID();
        UUID remoteComment = UUID.randomUUID();

        mappings.putPost(localPost, remotePost);
        mappings.putComment(localComment, remoteComment);
        mappings.clear(targetFor(localPost, localComment));

        assertNull(mappings.remotePost(localPost));
        assertNull(mappings.remoteComment(localComment));
        assertNull(mappings.localForRemote(remotePost));
        assertNull(mappings.localForRemote(remoteComment));
    }

    /**
     * Mappings are only meaningful against the database that issued them.
     * Pointing the app at a different backend must not resurrect ids from the
     * previous one, which would report content that never existed there.
     */
    @Test
    public void mappingsDoNotLeakAcrossBackends() {
        UUID localPost = UUID.randomUUID();
        mappings.putPost(localPost, UUID.randomUUID());

        config.setBaseUrl("http://192.168.1.50:8080");

        assertNull(mappings.remotePost(localPost));
    }

    @Test
    public void aCorruptStoredValueIsDiscardedRatherThanThrown() {
        UUID localPost = UUID.randomUUID();
        mappings.putPost(localPost, UUID.randomUUID());

        store.values.replaceAll((key, value) -> key.contains("post_") ? "not-a-uuid" : value);

        assertNull(mappings.remotePost(localPost));
    }
}
