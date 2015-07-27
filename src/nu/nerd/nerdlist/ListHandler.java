package nu.nerd.nerdlist;

import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Handles incoming and outgoing plugin messages.
 */
public class ListHandler implements PluginMessageListener {

    private NerdList plugin;
    private JSONParser parser;

    /**
     * Backlog of requests to be sent once a player joins the server.
     */
    private Queue<BungeeRequest> playerQueue;

    /**
     * Backlog of requests to be sent once we figure out Bungee's name for the
     * server.
     */
    private Queue<JSONForwardRequest> nameQueue;

    /**
     * Queues of requests that are waiting on a player count from a particular
     * server to be sent.
     */
    private Map<String, Queue<Player>> playerCountQueues;


    /**
     * Creates a new ListHandler.
     *
     * @param plugin the NerdList plugin
     */
    public ListHandler(NerdList plugin) {
        this.plugin = plugin;
        playerQueue = new LinkedList<>();
        nameQueue = new LinkedList<>();
        playerCountQueues = new HashMap<>();
        parser = new JSONParser();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("BungeeCord")) {
            if (plugin.debug) {
                plugin.getLogger().info("Received raw plugin message: " + new String(message));
                plugin.getLogger().info("Raw bytes: " + Arrays.toString(message));
            }
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            switch (subchannel) {
                case "GetServer": {
                    String server = in.readUTF();
                    plugin.setServerName(server);
                    flushNameQueue();
                    break;
                }
                case "PlayerCount": {
                    String server = in.readUTF();
                    int count = in.readInt();
                    flushPlayerCountQueue(server, count);
                    break;
                }
                case "NerdListHandshake": {
                    JSONObject content = readJSON(in);
                    String name = (String) content.get("server");
                    int visibility = ((Long) content.get("visibility")).intValue();
                    List<String> aliases = (List<String>) content.get("aliases");
                    ListServer server = new ListServer(name, visibility, aliases);
                    Map<String, ListServer> aliasMap = plugin.getServerAliases();
                    aliasMap.put(name.toLowerCase(), server);
                    for (String alias : aliases) {
                        aliasMap.put(alias.toLowerCase(), server);
                    }
                    plugin.getServers().remove(server);
                    plugin.getServers().add(server);
                    sendHandshakeResponse(name);
                    break;
                }
                case "NerdListHandshakeResponse": {
                    JSONObject content = readJSON(in);
                    String name = (String) content.get("server");
                    int visibility = ((Long) content.get("visibility")).intValue();
                    List<String> aliases = (List<String>) content.get("aliases");
                    ListServer server = new ListServer(name, visibility, aliases);
                    Map<String, ListServer> aliasMap = plugin.getServerAliases();
                    aliasMap.put(name.toLowerCase(), server);
                    for (String alias : aliases) {
                        aliasMap.put(alias.toLowerCase(), server);
                    }
                    plugin.getServers().remove(server);
                    plugin.getServers().add(server);
                    break;
                }
                case "NerdListRemoveServer": {
                    Map<String, ListServer> aliases = plugin.getServerAliases();
                    ListServer server = aliases.get(in.readUTF());
                    for (String alias : server.getAliases()) {
                        aliases.remove(alias);
                    }
                }
                case "NerdListRequest": {
                    JSONObject content = readJSON(in);
                    sendListResponse((String) content.get("server"), (String) content.get("player"),
                            plugin.getVisibility() > 1 ||
                                    plugin.getVisibility() > 0 && (boolean) content.get("admin"));
                    break;
                }
                case "NerdListResponse": {
                    JSONObject content = readJSON(in);
                    Player recipient = plugin.getServer().getPlayer((String) content.get("player"));
                    if (recipient != null) {
                        if ((boolean) content.get("permission")) {
                            plugin.sendMessageList(recipient,
                                    groupListToMap((List<Map<String, Object>>) content.get("groups")),
                                    (String) content.get("server"));
                        } else {
                            recipient.sendMessage(ChatColor.RED + "There are no players on " + content.get("server"));
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * Notifies other servers that this server is using NerdList
     */
    public void sendHandshake() {
        JSONObject object = new JSONObject();
        object.put("aliases", plugin.getAliases());
        object.put("visibility", plugin.getVisibility());
        sendBungeeMessage(new JSONForwardRequest("ALL", "NerdListHandshake", object), true, true);
    }

    /**
     * Respond to a handshake request with this server's info.
     */
    public void sendHandshakeResponse(String server) {
        JSONObject object = new JSONObject();
        object.put("aliases", plugin.getAliases());
        object.put("visibility", plugin.getVisibility());
        sendBungeeMessage(new JSONForwardRequest(server, "NerdListHandshakeResponse", object), true, true);
    }

    /**
     * Notifies other servers that this server should be removed.
     */
    public void sendRemoveServer() {
        // This will only fire if there are players online when the plugin is
        // disabled... Is there a better way of handling this?

        // For now, let's just not send this message until we figure out a
        // better method of cross-server communication. This won't break
        // anything too badly.
        // sendBungeeMessage(new JSONForwardRequest("ALL", "NerdListRemoveServer", new JSONObject()), true, false);
    }

    /**
     * Sends a PlayerCount request for the given server, in anticipation of
     * sending a list request to that server on behalf of the given player upon
     * response.
     *
     * @param server the server
     * @param player the player
     */
    public void sendPreListRequest(String server, Player player) {
        Queue<Player> players = playerCountQueues.get(server);
        if (players == null) {
            players = new LinkedList<>();
            playerCountQueues.put(server, players);
            sendBungeeMessage(new PlayerCountRequest(server, player.getName()), false, false);
        }
        players.add(player);
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
        sendBungeeMessage(new JSONForwardRequest(server, "NerdListRequest", object), true, false);
    }

    /**
     * Sends this server's player list to the specified player on the given
     * server.
     *
     * @param server the server to which to send the list
     * @param player the player to which to send the list
     * @param permission whether the player has permission to view the list
     */
    public void sendListResponse(String server, String player, boolean permission) {
        JSONObject object = new JSONObject();
        object.put("player", player);
        object.put("permission", permission);
        if (permission) {
            object.put("groups", groupMapToList(plugin.getPlayerList()));
        }
        sendBungeeMessage(new JSONForwardRequest(server, "NerdListResponse", object), true, false);
    }

    /**
     * Requests the server name registered with BungeeCord.
     */
    public void requestServerName() {
        sendBungeeMessage(new GetServerRequest(), false, true);
    }

    /**
     * Sends an arbitrary plugin message to BungeeCord.
     *
     * @param request the message content
     * @param appendName whether the server's name should be included in this
     *                   request
     * @param queue whether this request should be queued if it cannot
     *              currently be sent
     */
    private void sendBungeeMessage(BungeeRequest request, boolean appendName, boolean queue) {
        if (appendName) {
            if (request instanceof JSONForwardRequest) {
                String server = plugin.getServerName();
                if (server == null) {
                    nameQueue.add((JSONForwardRequest) request);
                    requestServerName();
                    return;
                }
                ((JSONForwardRequest) request).getContent().put("server", server);
            }
        }
        Player player = Iterables.getFirst(plugin.getServer().getOnlinePlayers(), null);
        if (player == null) {
            if (queue) {
                playerQueue.add(request);
                return;
            }
        }

        byte[] message = request.toByteArray();
        if (plugin.debug) {
            plugin.getLogger().info("Sending plugin message: " + new String(message));
            plugin.getLogger().info("Raw bytes: " + Arrays.toString(message));
        }
        player.sendPluginMessage(plugin, "BungeeCord", message);
    }

    private JSONObject readJSON(ByteArrayDataInput in) {
        String str = in.readUTF();
        try {
            return (JSONObject) parser.parse(str);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Collection<String>> groupListToMap(List<Map<String, Object>> groups) {
        Map<String, Collection<String>> groupMap = new LinkedHashMap<>();
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
                object.put("players", new ArrayList<>(group.getValue()));
                groupList.add(object);
            }
        }
        return groupList;
    }

    /**
     * Attempts to resend all requests that are waiting on a player to join.
     */
    public void flushPlayerQueue() {
        if (!playerQueue.isEmpty()) {
            // Messages can't be sent immediately after a player joins, so
            // we'll wait 1 tick.
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    while(!playerQueue.isEmpty()) {
                        BungeeRequest request = playerQueue.poll();
                        sendBungeeMessage(request, false, true);
                    }
                }
            }, 10);
        }
    }

    /**
     * Attempts to resend all requests that are waiting for the server's name
     * to be defined.
     */
    public void flushNameQueue() {
        String server = plugin.getServerName();
        if (server != null) {
            while (!nameQueue.isEmpty()) {
                JSONForwardRequest request = nameQueue.poll();
                request.getContent().put("server", server);
                sendBungeeMessage(request, true, true);
            }
        }
    }

    /**
     * Handles all list requests toward a particular server.
     *
     * @param server the server
     * @param count the server's player count
     */
    public void flushPlayerCountQueue(String server, int count) {
        Queue<Player> players = playerCountQueues.get(server);
        if (players != null) {
            while (!players.isEmpty()) {
                Player player = players.poll();
                if (player.isOnline()) {
                    if (count > 0) {
                        sendListRequest(server, player);
                    } else {
                        player.sendMessage(ChatColor.RED + "There are no players on " + server);
                    }
                }
            }
        }
    }

}
