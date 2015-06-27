package nu.nerd.nerdlist;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;


public class ListHandler implements PluginMessageListener {

    private NerdList plugin;
    private Queue<ListRequest> requests;
    private JSONParser parser;

    /**
     * Creates a new ListHandler.
     *
     * @param plugin the NerdList plugin
     */
    public ListHandler(NerdList plugin) {
        this.plugin = plugin;
        requests = new LinkedList<ListRequest>();
        parser = new JSONParser();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("BungeeCord")) {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            switch (subchannel) {
                case "GetServer":
                    String server = in.readUTF();
                    plugin.setServerName(server);
                    flushQueue();
                    break;
                case "NerdListRequest": {
                    JSONObject content = readJSON(in);
                    if (plugin.getVisibility() > 1 || plugin.getVisibility() > 0 && (boolean) content.get("admin")) {
                        sendListResponse((String) content.get("server"), (String) content.get("player"));
                    }
                    break;
                }
                case "NerdListResponse": {
                    JSONObject content = readJSON(in);
                    Player recipient = plugin.getServer().getPlayer((String) content.get("player"));
                    if (recipient != null) {
                        plugin.sendMessageList(recipient,
                                groupListToMap((List<Map<String, Object>>) content.get("groups")),
                                (String) content.get("server"));
                    }
                    break;
                }
            }
        }
    }

    /**
     * Sends a request to the given server for a player list.
     *
     * @param server the server to which to send the request
     * @param player the player requesting the list
     */
    public void sendListRequest(String server, Player player) {
        JSONObject object = new JSONObject();
        object.put("player", player.getName());
        object.put("admin", player.hasPermission("nerdlist.admin"));
        sendWithServerName(server, "NerdListRequest", object);
    }

    /**
     * Sends this server's player list to the specified player on the given server.
     *
     * @param server the server to which to send the list
     * @param player the player to which to send the list
     */
    public void sendListResponse(String server, String player) {
        JSONObject object = new JSONObject();
        object.put("player", player);
        object.put("groups", groupMapToList(plugin.getPlayerList()));

        sendWithServerName(server, "NerdListResponse", object);
    }

    /**
     * Requests the server name registered with BungeeCord.
     */
    public void requestServerName() {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        Player player = Iterables.getFirst(plugin.getServer().getOnlinePlayers(), null);
        if (player != null) {
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }

    /**
     * Sends a message to forward to another server, with a "return address".
     *
     * @param server the server to which to send the message
     * @param channel the channel over which to send the message
     * @param content the message content
     */
    private void sendWithServerName(String server, String channel, JSONObject content) {
        String localServer = plugin.getServerName();
        if (localServer == null) {
            ListRequest request = new ListRequest(server, channel, content);
            requests.add(request);
            requestServerName();
        } else {
            content.put("server", localServer);
            sendBungeeMessage(server, channel, content.toJSONString());
        }
    }

    /**
     * Sends an arbitrary message to forward to another server.
     *
     * @param server the server to which to send the message
     * @param channel the channel over which to send the message
     * @param message the message text
     */
    private void sendBungeeMessage(String server, String channel, String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward");
        out.writeUTF(server);
        out.writeUTF(channel);

        ByteArrayDataOutput msgOut = ByteStreams.newDataOutput();
        msgOut.writeUTF(message);

        byte[] bytes = msgOut.toByteArray();
        out.writeShort(bytes.length);
        out.write(bytes);

        Player player = Iterables.getFirst(plugin.getServer().getOnlinePlayers(), null);
        if (player == null) {
            throw new RuntimeException("There must be at least one player online to send a message.");
        }
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    private JSONObject readJSON(ByteArrayDataInput in) {
        short len = in.readShort();
        byte[] message = new byte[len];
        in.readFully(message);
        ByteArrayDataInput content = ByteStreams.newDataInput(message);
        String str = content.readUTF();
        try {
            return (JSONObject) parser.parse(str);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Collection<String>> groupListToMap(List<Map<String, Object>> groups) {
        Map<String, Collection<String>> groupMap = new LinkedHashMap<String, Collection<String>>();
        for (Map<String, Object> group : groups) {
            groupMap.put((String) group.get("name"), (Collection<String>) group.get("players"));
        }
        return groupMap;
    }

    private JSONArray groupMapToList(Map<?, Collection<String>> groups) {
        JSONArray groupList = new JSONArray();
        for (Map.Entry<?, Collection<String>> group : groups.entrySet()) {
            if (!group.getValue().isEmpty()) {
                JSONObject object = new JSONObject();
                object.put("name", group.getKey().toString());
                object.put("players", new ArrayList<String>(group.getValue()));
                groupList.add(object);
            }
        }
        return groupList;
    }

    private void flushQueue() {
        while (!requests.isEmpty()) {
            ListRequest request = requests.poll();
            sendWithServerName(request.getServer(), request.getChannel(), request.getContent());
        }
    }

}
