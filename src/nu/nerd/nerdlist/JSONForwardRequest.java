package nu.nerd.nerdlist;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.json.simple.JSONObject;

/**
 * A Forward request containing a JSON string to be sent over BungeeCord's
 * plugin messaging channel.
 * @see <a href="http://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/#forward">
 *     the Forward channel</a>
 */
public class JSONForwardRequest implements BungeeRequest {

    private final String server;
    private final String channel;
    private final JSONObject content;

    /**
     * Constructs a JSONForwardRequest from the given server, channel, and
     * content.
     *
     * @param server the destination server
     * @param channel the NerdList channel
     * @param content the JSON content
     */
    public JSONForwardRequest(String server, String channel, JSONObject content) {
        this.server = server;
        this.channel = channel;
        this.content = content;
    }

    /**
     * Gets the server to which the request will be sent.
     *
     * @return the server
     */
    public String getServer() {
        return server;
    }

    /**
     * Gets the channel over which the request will be sent.
     *
     * @return the channel
     */
    public String getChannel() {
        return channel;
    }

    /**
     * Gets the request's JSON content.
     *
     * @return the JSON content
     */
    public JSONObject getContent() {
        return content;
    }

    @Override
    public byte[] toByteArray() {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF(server);
        out.writeUTF(channel);
        out.writeUTF(content.toJSONString());

        return out.toByteArray();
    }

}
