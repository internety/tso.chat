package tso.chat;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by reax on 03.11.17.
 */
public class ChatMessage implements Message {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("kk:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd:MM:yyyy");
    private final String channel;
    private final String senderGuild;
    private final String senderName;
    private final String senderId;
    private final String text;
    private final LocalDateTime dateTime;

    public ChatMessage(String channel, String senderGuild, String senderName, String senderId, LocalDateTime dateTime, String text) {
        this.channel=channel;
        this.senderGuild=senderGuild;
        this.senderName=senderName;
        this.senderId=senderId;
        this.dateTime = dateTime;
        this.text=text;
    }

    public String getChannel() {
        return channel;
    }

    public String getSenderGuild() {
        return senderGuild;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getTime() {
        return dateTime.toLocalTime().format(TIME_FORMATTER);
    }

    public String getDate() { return dateTime.toLocalDate().format(DATE_FORMATTER); }

    public String getText() {
        return text;
    }
}
