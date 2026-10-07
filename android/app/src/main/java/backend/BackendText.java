package backend;

import android.content.Context;

import com.example.myapplication.R;

/**
 * The wording this package shows to a person.
 *
 * <p>Everything here reaches the screen — the review queue prints these on its
 * status line, and reporting prints them in a toast — so they have to follow the
 * app's language like the rest of the copy. Held statically rather than threaded
 * through every call because the alternative is a {@code Context} parameter on
 * classes whose whole point is that they are plain and testable off-device.
 *
 * <p>Messages that come from the server are not translated here: those arrive as
 * problem-detail text and are shown as the server wrote them.
 */
final class BackendText {

    private static volatile Context context;

    private BackendText() {
    }

    static void init(Context applicationContext) {
        context = applicationContext;
    }

    /** Falls back to the English literal when no context has been attached — unit tests. */
    static String get(int resId, String fallback) {
        Context current = context;
        return current == null ? fallback : current.getString(resId);
    }

    static String adminLoginRequired() {
        return get(R.string.admin_login_required, "Administrator session ended. Please sign in again.");
    }

    static String adminRoleRequired() {
        return get(R.string.admin_role_required, "This account is not an active server administrator.");
    }

    static String memberLoginRequired() {
        return get(R.string.member_login_required, "Your session ended. Please sign in again.");
    }
    static String memberRoleRequired() {
        return get(R.string.member_role_required, "Use Member for a member account and Admin for an administrator account.");
    }

    static String unreachable(String baseUrl) {
        Context current = context;
        return current == null
                ? "Could not reach the moderation backend at " + baseUrl + "."
                : current.getString(R.string.backend_error_unreachable, baseUrl);
    }

    static String contentUnavailable() {
        return get(R.string.backend_content_unavailable, "This content is no longer available.");
    }

    static String integrationFailed() {
        return get(R.string.backend_error_generic, "Moderation integration failed.");
    }

    static String malformedResponse() {
        return get(R.string.backend_error_malformed, "The moderation service returned an unexpected response.");
    }
}
