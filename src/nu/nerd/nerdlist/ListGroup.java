package nu.nerd.nerdlist;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.bukkit.entity.Player;

/**
 * A NerdList group.
 */
public class ListGroup implements Comparable<ListGroup> {

    private final String name;
    private final String permission;
    private final int priority;

    /**
     * Creates a group with the given name, permission, and priority.
     *
     * @param name the group's name
     * @param permission the group's permission
     * @param priority the priority with which this group should be tested
     */
    public ListGroup(String name, String permission, int priority) {
        this.name = name;
        this.permission = permission;
        this.priority = priority;
    }

    /**
     * Gets the group's name
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Determines whether the given player is a member of this group.
     *
     * @param player the player
     * @return whether the player is a member of the group
     */
    public boolean isMember(Player player) {
        return player.hasPermission(permission);
    }

    @Override
    public int compareTo(ListGroup o) {
        int cmp = Integer.compare(priority, o.priority);
        return cmp == 0 ? name.compareTo(o.name) : -cmp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        ListGroup that = (ListGroup) o;

        return new EqualsBuilder()
                .append(priority, that.priority)
                .append(name, that.name)
                .append(permission, that.permission)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(name)
                .append(permission)
                .append(priority)
                .toHashCode();
    }

    @Override
    public String toString() {
        return name;
    }

}
