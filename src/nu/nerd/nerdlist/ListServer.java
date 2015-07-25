package nu.nerd.nerdlist;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * NerdList's representation of another server that is running the plugin.
 */
public class ListServer {

    private String name;
    private int visibility;
    private List<String> aliases;

    /**
     * Constructs a new ListServer from the given name, visibility, and
     * aliases.
     *
     * @param name the server's name
     * @param visibility the server's visibility
     * @param aliases the server's aliases
     */
    public ListServer(String name, int visibility, List<String> aliases) {
        this.name = name;
        this.visibility = visibility;
        this.aliases = aliases;
    }

    /**
     * Gets the server's name
     *
     * @return the server's name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the server's visibility
     *
     * @return the server's visibility
     */
    public int getVisibility() {
        return visibility;
    }

    /**
     * Gets the server's list of aliases.
     *
     * @return the server's aliases
     */
    public List<String> getAliases() {
        return aliases;
    }

    /**
     * Gets whether this server is visible to the given player
     *
     * @param player the player
     * @return whether this server is visible
     */
    public boolean isVisible(Player player) {
        return visibility == 2 || visibility == 1 && player.hasPermission("nerdlist.admin");
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

}
