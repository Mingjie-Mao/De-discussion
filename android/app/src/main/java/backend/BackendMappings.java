package backend;

import java.util.UUID;

/** Persistent local-to-backend ids, namespaced by backend URL. */
final class BackendMappings {
    private final KeyValueStore store;
    private final BackendConfig config;

    BackendMappings(KeyValueStore store, BackendConfig config) {
        this.store = store;
        this.config = config;
    }

    UUID remotePost(UUID localId) {
        return read("post_" + localId);
    }

    void putPost(UUID localId, UUID remoteId) {
        write("post_" + localId, localId, remoteId);
    }

    UUID remoteComment(UUID localId) {
        return read("comment_" + localId);
    }

    void putComment(UUID localId, UUID remoteId) {
        write("comment_" + localId, localId, remoteId);
    }

    UUID localForRemote(UUID remoteId) {
        return read("local_" + remoteId);
    }

    void clear(BackendReportTarget target) {
        remove("post_" + target.post().localId(), remotePost(target.post().localId()));
        for (BackendReportTarget.CommentSnapshot comment : target.comments()) {
            remove("comment_" + comment.localId(), remoteComment(comment.localId()));
        }
    }

    private void write(String forwardKey, UUID localId, UUID remoteId) {
        store.put(key(forwardKey), remoteId.toString());
        store.put(key("local_" + remoteId), localId.toString());
    }

    private void remove(String forwardKey, UUID remoteId) {
        store.remove(key(forwardKey));
        if (remoteId != null) {
            store.remove(key("local_" + remoteId));
        }
    }

    private UUID read(String suffix) {
        String value = store.get(key(suffix), null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            store.remove(key(suffix));
            return null;
        }
    }

    private String key(String suffix) {
        return "mapping_" + Integer.toHexString(config.baseUrl().hashCode()) + "_" + suffix;
    }
}
