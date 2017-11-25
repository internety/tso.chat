package tso.chat;

import javafx.beans.property.SimpleObjectProperty;
import tso.chat.exceptions.BadCredentialsException;
import tso.chat.exceptions.UplayDownException;

import java.util.List;
import java.util.Map;

public interface Chat {
    String connect() throws BadCredentialsException, UplayDownException;
    String connect(SimpleObjectProperty<Stage> stage) throws BadCredentialsException, UplayDownException;
    Map<String, Status> getFriendsAndStatusFromServer();
    List<ChatMessage> bindChat(String chatName);
    ChatMessage getNewMessage();
    void sendMessage(SentMessage message);
    void restart();

}
