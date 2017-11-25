package tso.chat;

import javafx.beans.property.SimpleObjectProperty;
import tso.chat.exceptions.BadCredentialsException;
import tso.chat.exceptions.UplayDownException;

import java.util.List;
import java.util.Map;

public class ChatImpl implements Chat {
    private final Connection connection;

    public ChatImpl(String email, String password, Region region) {
        connection = new Connection(email, password, region);
    }

    /**
     *
     * @return the name of the player which is resolved at the authorization step
     * @throws BadCredentialsException if the user entered incorrect email or password
     * @throws UplayDownException if the uplay server is down at the login step
     */
    @Override
    public String connect() throws BadCredentialsException, UplayDownException {
        connection.login();
        connection.checkIn();
        String name = connection.receiveAuthHash();
        connection.bindAll();
        return name;
    }

    @Override
    public String connect(SimpleObjectProperty<Stage> stage)
            throws BadCredentialsException, UplayDownException {
        stage.set(Stage.LOGIN);
        connection.login();
        stage.set(Stage.CHECK_IN);
        connection.checkIn();
        stage.set(Stage.AUTH);
        String name = connection.receiveAuthHash();
        stage.set(Stage.BIND);
        connection.bindAll();
        return name;
    }

    @Override
    public Map<String, Status> getFriendsAndStatusFromServer() {
        return connection.getFriendsAndStatusFromServer();
    }

    @Override
    public List<ChatMessage> bindChat(String chatName) {
        return connection.bindChat(chatName);
    }

    @Override
    public ChatMessage getNewMessage() {
        return connection.chatLoop();
    }



    @Override
    public void sendMessage(SentMessage message) {
        connection.sendMessage(message);
    }

    @Override
    public void restart() {
        connection.restart();
    }
}
