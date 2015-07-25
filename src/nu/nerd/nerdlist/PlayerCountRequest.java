package nu.nerd.nerdlist;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.json.simple.JSONObject;

/**
 * A PlayerCount request to be sent over BungeeCord's plugin messaging channel.
 * @see <a href="http://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/#playercount">
 *     the PlayerCount channel</a>
 */
public class PlayerCountRequest implements BungeeRequest {

    private String server;

    /**
     * Constructs a PlayerCountRequest for the given server.
     *
     * @param server the server
     */
    public PlayerCountRequest(String server, String content) {
        this.server = server;
    }

    @Override
    public byte[] toByteArray() {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerCount");
        out.writeUTF(server);
        return out.toByteArray();
    }

}
