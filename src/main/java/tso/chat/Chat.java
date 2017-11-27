package tso.chat;

import javafx.beans.property.SimpleObjectProperty;
import tso.chat.exceptions.BadCredentialsException;
import tso.chat.exceptions.UplayDownException;

import java.util.List;
import java.util.Map;

public interface Chat {
    /**
     *
     * @return the name of the player which is resolved at the authorization step
     * @throws BadCredentialsException if the user entered incorrect email or password
     * @throws UplayDownException if the uplay server is down at the login step
     */
    String connect() throws BadCredentialsException, UplayDownException;
    String connect(SimpleObjectProperty<Stage> stage) throws BadCredentialsException, UplayDownException;
    Map<String, Status> getFriendsAndStatusFromServer();
    List<ChatMessage> bindChat(String chatName);
    ChatMessage getNewMessage();
    void sendMessage(SentMessage message);
    void restart();

}
