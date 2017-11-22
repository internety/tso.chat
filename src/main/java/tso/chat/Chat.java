package tso.chat;

import javafx.beans.property.SimpleObjectProperty;

import java.util.List;
import java.util.Map;

public interface Chat {
    String connect();
    String connect(SimpleObjectProperty<Stage> stage);
    Map<String, Status> getFriendsAndStatusFromServer();
    List<ChatMessage> bindChat(String chatName);
    ChatMessage getNewMessage();
    void sendMessage(SentMessage message);
    void restart();

}
