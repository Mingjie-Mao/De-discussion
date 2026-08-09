package backend;

/** What the server will use for newly filed reports right now. */
public record BackendModerationStatus(
        String configuredEngine,
        String activeEngine,
        String fallbackEngine,
        boolean configuredEngineAvailable,
        boolean llmActive) {
}
