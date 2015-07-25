package nu.nerd.nerdlist;

/**
 * A request to be sent on BungeeCord's plugin messaging channel.
 */
public interface BungeeRequest {

    /**
     * Gets the request as a byte array.
     *
     * @return the byte array
     */
    public byte[] toByteArray();

}