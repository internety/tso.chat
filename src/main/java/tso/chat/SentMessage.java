package tso.chat;

/**
 * Created by reax on 14.11.17.
 */
public class SentMessage {
    private final String channel;
    private final String to;
    private final String text;

    public String getText() {
        return text;
    }

    public String getChannel() {
        return channel;
    }

    public String getTo() {
        return to;
    }

    public SentMessage(String channel, String to, String text) {
        this.channel = channel;
        this.to = to;
        this.text = text;
    }
}
