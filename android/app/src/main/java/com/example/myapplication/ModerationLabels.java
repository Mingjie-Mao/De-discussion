package com.example.myapplication;

import android.content.Context;

/**
 * Turns the moderation service's enum names into words in the app's language.
 *
 * <p>The API answers with {@code HIDE}, {@code ALLOW} and so on. Those are wire
 * values, not copy, and putting them straight on screen left the review queue
 * half in English while the rest of the app was in Chinese.
 *
 * <p>An unrecognised value is returned as-is rather than replaced with a
 * placeholder: if the server grows a new outcome, showing its raw name is more
 * use to an administrator than showing nothing.
 */
final class ModerationLabels {

    private ModerationLabels() {
    }

    /** The outcome an administrator settled on. */
    static String action(Context context, String action) {
        if (action == null || action.isEmpty()) {
            return "";
        }
        return switch (action) {
            case "NONE" -> context.getString(R.string.moderation_action_none);
            case "HIDE" -> context.getString(R.string.moderation_action_hide);
            case "DELETE" -> context.getString(R.string.moderation_action_delete);
            case "BAN" -> context.getString(R.string.moderation_action_ban);
            default -> action;
        };
    }

    /** What the engine recommended. */
    static String decision(Context context, String decision) {
        if (decision == null || decision.isEmpty()) {
            return "";
        }
        return switch (decision) {
            case "ALLOW" -> context.getString(R.string.moderation_decision_allow);
            case "REMOVE" -> context.getString(R.string.moderation_decision_remove);
            case "ESCALATE" -> context.getString(R.string.moderation_decision_escalate);
            default -> decision;
        };
    }

    /** Where a case has reached in the queue. */
    static String status(Context context, String status) {
        if (status == null || status.isEmpty()) {
            return "";
        }
        return switch (status) {
            case "QUEUED" -> context.getString(R.string.moderation_status_queued);
            case "ANALYSING" -> context.getString(R.string.moderation_status_analysing);
            case "AWAITING_REVIEW" -> context.getString(R.string.moderation_status_awaiting);
            case "RESOLVED" -> context.getString(R.string.moderation_status_resolved);
            case "PENDING" -> context.getString(R.string.admin_appeal_pending);
            case "UPHELD" -> context.getString(R.string.admin_appeal_upheld);
            case "OVERTURNED" -> context.getString(R.string.admin_appeal_overturned);
            default -> status;
        };
    }
}
