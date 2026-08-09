package backend;

import java.util.UUID;

/** One server-backed moderation item rendered in the Android admin queue. */
public record BackendReviewCase(
        UUID id,
        UUID remoteTargetId,
        UUID localTargetId,
        String targetType,
        String status,
        int reportCount,
        String engine,
        String recommendedDecision,
        double confidence,
        String rationale,
        String title,
        String body,
        String createdAt,
        /** Empty until a human has decided; the outcome in force once they have. */
        String finalAction,
        String decidedAt) {

    /** Whether a human has already ruled on this case, as opposed to it awaiting review. */
    public boolean isDecided() {
        return !finalAction.isEmpty();
    }
}
