package backend;

import org.json.JSONObject;

/** A request the backend answered with something other than success. */
public class BackendException extends RuntimeException {

    /** Zero when the request never reached the server at all. */
    private final int status;

    public BackendException(int status, String message) {
        super(message);
        this.status = status;
    }

    public BackendException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int status() {
        return status;
    }

    public boolean isUnauthorised() {
        return status == 401 || status == 403;
    }

    public boolean isConflict() {
        return status == 409;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isUnreachable() {
        return status == 0;
    }

    /**
     * The server explains itself in an RFC 7807 problem detail, which is far
     * more useful than the status alone — "You have already reported this
     * content." rather than 409. Falls back to the raw body when the response is
     * not a problem detail, and to the status when there is no body worth
     * showing.
     */
    static BackendException fromResponse(int status, String body) {
        if (body != null && !body.isEmpty()) {
            try {
                JSONObject problem = new JSONObject(body);
                String detail = problem.optString("detail", null);
                if (detail != null && !detail.isEmpty()) {
                    return new BackendException(status, detail);
                }
            } catch (Exception ignored) {
                // Not a problem detail. The raw body is the next best thing.
            }
            return new BackendException(status, body);
        }
        return new BackendException(status, "The backend answered " + status + ".");
    }
}
