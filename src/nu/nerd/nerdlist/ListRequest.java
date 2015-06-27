package nu.nerd.nerdlist;

import org.json.simple.JSONObject;

/**
 * A stored request to be handled by BungeeCord.
 */
public class ListRequest {

    private final String server;
    private final String channel;
    private final JSONObject content;

    /**
     * Creates a new ListRequest from the given server, channel, and content.
     *
     * @param server the server
     * @param channel the channel
     * @param content the content
     */
    public ListRequest(String server, String channel, JSONObject content) {
        this.server = server;
        this.channel = channel;
        this.content = content;
    }

    /**
     * Gets the server this request should be sent to.
     *
     * @return the server
     */
    public String getServer() {
        return server;
    }

    /**
     * Gets the channel over which this request should be sent.
     *
     * @return the channel
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Gets the content of this request.
     *
     * @return the content
     */
    public JSONObject getContent() {
        return content;
    }

}
