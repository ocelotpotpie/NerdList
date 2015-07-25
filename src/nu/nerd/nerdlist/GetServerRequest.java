package nu.nerd.nerdlist;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

/**
 * A GetServer request to be sent over BungeeCord's plugin messaging channel.
 * @see <a href="http://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/#getserver">
 *     the GetServer channel</a>
 */
public class GetServerRequest implements BungeeRequest {

    @Override
    public byte[] toByteArray() {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        return out.toByteArray();
    }

}
