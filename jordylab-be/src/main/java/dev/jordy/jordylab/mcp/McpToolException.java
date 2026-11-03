package dev.jordy.jordylab.mcp;

/**
 * A tool failure whose message is safe to show the model (FR-018). Tools throw it for expected outcomes such as "not
 * found"; any other exception is replaced by a generic message at the advice boundary.
 *
 * <p>The MCP annotation layer shows the exception's message and then its root cause's message. With no cause the safe text
 * would appear twice, so the exception carries an empty cause and the text appears once.
 */
public class McpToolException extends RuntimeException {

    public McpToolException(String safeMessage) {
        super(safeMessage, new IllegalStateException(""));
    }
}
