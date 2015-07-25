package nu.nerd.nerdlist;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The NerdList plugin.
 */
public class NerdList extends JavaPlugin implements Listener {

    private int visibility;
    private Collection<String> aliases;
    private String outputIntro;
    private String outputLabel;
    private List<ChatColor> outputListColors;
    private String outputListDelimiter;
    private String outputCount;
    private List<ListGroup> displayGroups;
    private List<ListGroup> testGroups;
    private Collection<String> hiddenPlayers;

    private String serverName;
    private FileConfiguration playerConfig;
    private ListHandler handler;

    private Set<ListServer> servers;
    private Map<String, ListServer> serverAliases;

    public boolean debug;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        handler = new ListHandler(this);
        servers = new HashSet<>();
        serverAliases = new HashMap<>();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", handler);
        reloadConfig();
        handler.sendHandshake();
    }

    @Override
    public void onDisable() {
        handler.sendRemoveServer();
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("list")) {
            if (args.length == 0) {
                sendPlayerList(sender);
            } else if (args.length == 1) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Cross-server list commands can only be run by players.");
                    return true;
                }
                Player player = (Player) sender;
                String server = args[0].toLowerCase();
                if (server.equals("all")) {
                    sendPlayerList(player);
                    for (ListServer s : servers) {
                        handler.sendPreListRequest(s.getName(), player);
                    }
                } else if (server.equalsIgnoreCase(serverName)) {
                    sendPlayerList(player);
                } else {
                    ListServer s = serverAliases.get(server);
                    if (s == null || !s.isVisible(player)) {
                        player.sendMessage(ChatColor.RED + "There are no players on " + server);
                    } else {
                        handler.sendPreListRequest(s.getName(), player);
                    }
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /list [<server|all]");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("list-hide")) {
            if (args.length == 0) {
                if (sender instanceof Player) {
                    hidePlayer(sender.getName());
                    sender.sendMessage(ChatColor.GREEN + "You are now hidden from the player list.");
                } else {
                    sender.sendMessage(ChatColor.RED + "You must include a player name to run this from console.");
                }
            } else if (args.length == 1) {
                hidePlayer(args[0]);
                sender.sendMessage(ChatColor.GREEN + args[0] + " is now hidden from the player list.");
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /list-show [player]");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("list-show")) {
            if (args.length == 0) {
                if (sender instanceof Player) {
                    if (showPlayer(sender.getName())) {
                        sender.sendMessage(ChatColor.GREEN + "You are no longer hidden from the player list.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "You are not hidden from the player list.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "You must include a player name to run this from console.");
                }
            } else if (args.length == 1) {
                if (showPlayer(args[0])) {
                    sender.sendMessage(ChatColor.GREEN + args[0] + " is no longer hidden from the player list.");
                } else {
                    sender.sendMessage(ChatColor.RED + args[0] + " is not hidden from the player list.");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Usage: /list-show [player]");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("list-reload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "NerdList configuration reloaded.");
            return true;
        }
        return false;
    }

    @Override
    public void reloadConfig() {
        saveDefaultConfig();
        super.reloadConfig();
        FileConfiguration config = getConfig();
        String visibilityString = config.getString("visibility", "ALL").toUpperCase();
        switch (visibilityString) {
            case "ALL":
                visibility = 2;
                break;
            case "ADMIN":
                visibility = 1;
                break;
            case "NONE":
                visibility = 0;
                break;
            default:
                getLogger().warning("Invalid visibility '" + visibilityString + "'; defaulting to ALL. Please check " +
                        "config.yml");
                visibility = 2;
                break;
        }

        aliases = config.getStringList("aliases");

        outputIntro = config.getString("output.intro", "Online players:");
        outputLabel = config.getString("output.label", "§6%s: ");
        outputListColors = new ArrayList<>();
        for (String color : config.getStringList("output.list.colors")) {
            try {
                outputListColors.add(ChatColor.valueOf(color.toUpperCase()));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid list color '" + color + "'. Please check config.yml");
            }
        }
        outputListDelimiter = config.getString("output.list.delimiter", " ");
        outputCount = config.getString("outputCount", "§7Total:§f %d players");
        debug = config.getBoolean("debug");

        displayGroups = new LinkedList<>();
        for (Map group : config.getMapList("groups")) {
            try {
                String name = (String) group.get("name");
                String permission = (String) group.get("permission");
                int priority = (int) group.get("priority");
                displayGroups.add(new ListGroup(name, permission, priority));
            } catch (Exception e) {
                getLogger().warning("An error was found in your group definitions. Please check config.yml");
                e.printStackTrace();
            }
        }
        testGroups = new ArrayList<>(displayGroups);
        Collections.sort(testGroups);

        hiddenPlayers = new HashSet<>();
        playerConfig = new YamlConfiguration();
        File playerConfigFile = new File(getDataFolder(), "players.yml");
        if (playerConfigFile.exists()) {
            try {
                playerConfig.load(playerConfigFile);
                for (String player : playerConfig.getStringList("hidden")) {
                    hiddenPlayers.add(player.toLowerCase());
                }
            } catch (Exception e) {
                getLogger().warning("An error occurred while reading your player configuration file. Please check "
                        + "players.yml");
            }
        }

        getLogger().info("Reloaded configuration.");
    }

    /**
     * Gets a list of players online, separated by groups.
     *
     * @return the online players
     */
    public Map<ListGroup, Collection<String>> getPlayerList() {
        Map<ListGroup, Collection<String>> groups = new LinkedHashMap<>();
        for (ListGroup group : displayGroups) {
            groups.put(group, new TreeSet<>(String.CASE_INSENSITIVE_ORDER));
        }
        for (Player player : getServer().getOnlinePlayers()) {
            // TODO cache group membership
            if (!isPlayerHidden(player.getName())) {
                for (ListGroup group : testGroups) {
                    if (group.isMember(player)) {
                        groups.get(group).add(player.getName());
                        break;
                    }
                }
            }
        }
        return groups;
    }

    /**
     * Formats a player list as a list of messages.
     *
     * @param list the player list
     * @param server the server this list is from
     * @return a list of message strings
     */
    public List<String> toMessageList(Map<?, Collection<String>> list, String server) {
        List<String> messages = new LinkedList<>();
        int count = 0;
        messages.add(String.format(outputIntro, server));
        for (Map.Entry<?, Collection<String>> group : list.entrySet()) {
            Collection<String> players = group.getValue();
            count += players.size();
            if (!players.isEmpty()) {
                StringBuilder line = new StringBuilder();
                line.append(String.format(outputLabel, group.getKey().toString()));
                int index = 0;
                for (String player : players) {
                    line.append(outputListColors.get(index % outputListColors.size()));
                    line.append(player);
                    if (index + 1 < players.size()) {
                        line.append(outputListDelimiter);
                    }
                    index++;
                }
                messages.add(line.toString());
            }
        }
        messages.add(String.format(outputCount, count));
        return messages;
    }

    /**
     * Sends the list to the given player as a message.
     *
     * @param player the player
     * @param list the player list
     * @param server the server this list is from
     */
    public synchronized void sendMessageList(CommandSender player, Map<?, Collection<String>> list, String server) {
        List<String> messages = toMessageList(list, server);
        for (String message : messages) {
            player.sendMessage(message);
        }
    }

    /**
     * Sends this server's player list to the given player.
     *
     * @param player the player
     */
    public void sendPlayerList(CommandSender player) {
        sendMessageList(player, getPlayerList(), serverName == null ? "this server" : serverName);
    }

    /**
     * Hides the given player from the list.
     *
     * @param player the player to hide
     */
    public void hidePlayer(String player) {
        hiddenPlayers.add(player.toLowerCase());
        List<String> players = new ArrayList<>(hiddenPlayers);
        playerConfig.set("hidden", players);
        try {
            playerConfig.save(new File(getDataFolder(), "players.yml"));
        } catch (IOException e) {
            getLogger().warning("An error occurred while saving your player configuration file.");
        }
    }

    /**
     * Unhides the given player in the list.
     *
     * @param player the player to show
     * @return whether the player was hidden
     */
    public boolean showPlayer(String player) {
        boolean removed = hiddenPlayers.remove(player.toLowerCase());
        List<String> players = new ArrayList<>(hiddenPlayers);
        playerConfig.set("hidden", players);
        try {
            playerConfig.save(new File(getDataFolder(), "players.yml"));
        } catch (IOException e) {
            getLogger().warning("An error occurred while saving your player configuration file.");
        }
        return removed;
    }

    /**
     * Gets this server's list visibility.
     *
     * @return the visibility
     */
    public int getVisibility() {
        return visibility;
    }

    /**
     * Gets a list of this server's aliases.
     * @return the aliases
     */
    public List<String> getAliases() {
        return new ArrayList<>(aliases);
    }

    /**
     * Determines whether the player should be hidden from the list.
     *
     * @param player the player
     * @return whether the player is hidden
     */
    public boolean isPlayerHidden(String player) {
        return hiddenPlayers.contains(player.toLowerCase());
    }

    /**
     * Gets the name of the server.
     *
     * @return the server name
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Sets the name of the server.
     *
     * @param serverName the server name
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Gets a set of all registered servers.
     *
     * @return the servers
     */
    public Set<ListServer> getServers() {
        return servers;
    }

    /**
     * Gets the map of aliases for all registered servers.
     *
     * @return the alias map
     */
    public Map<String, ListServer> getServerAliases() {
        return serverAliases;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (serverName == null) {
            handler.flushPlayerQueue();
        }
    }

}
