package tso.chat;

/**
 * A Channel represents a chat channel on a chat server.
 * <p>
 * Globals 1 to 5, help and trade channels are accessible to anyone.
 * <p>
 * There exist 2 guild chats for each guild: guild chat is open for any member of the guild and guild officers chat
 * is accessible only to said guild's head and officers. Guild chats have unique number for each guild within a region
 * (or perhaps within a realm?): gc_123 and gco_123.
 * <p>
 * Reporting channel is probably only for administrators and moderators.
 * <p>
 * Moderators channel seems to be open for anyone, but that's most likely a bug. It looks like this channel
 * is not used now.
 * <p>
 * Adventure channels will be added later.
 * <p>
 * There is no "private messages" channel.
 */
public enum Channel {
    GLOBAL_1("global-1"),
    GLOBAL_2("global-2"),
    GLOBAL_3("global-3"),
    GLOBAL_4("global-4"),
    GLOBAL_5("global-5"),
    HELP("help"),
    TRADE("trade"),
    GUILD("gc_"),
    GUILD_OFFICERS("gco_"),
    REPORTING("reporting"),
    MODERATORS("moderators");

    private String value;

    private Channel(String value) {
        this.value=value;
    }

    @Override
    public String toString() {
        return value;
    }

}
