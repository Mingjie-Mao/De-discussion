package backend;

import java.util.List;
import java.util.UUID;

/** An immutable local content chain ready to be mirrored and reported. */
public record BackendReportTarget(
        PostSnapshot post,
        List<CommentSnapshot> comments,
        TargetType targetType,
        UUID localTargetId) {

    public BackendReportTarget {
        comments = List.copyOf(comments);
    }

    public enum TargetType {
        POST,
        COMMENT
    }

    public record PostSnapshot(
            UUID localId,
            UUID authorId,
            String forumKey,
            String title,
            String body) {
    }

    /** Ordered from the top-level comment through to the reported comment. */
    public record CommentSnapshot(UUID localId, UUID authorId, String body) {
    }
}
