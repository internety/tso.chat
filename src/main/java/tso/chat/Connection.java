package tso.chat;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import tso.chat.exceptions.BadCredentialsException;
import tso.chat.exceptions.UplayDownException;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.http.HttpStatus.SC_OK;

/**
 * This class provides chat connection and message exchange services.
 * Most of the connection methods are not part of the API. Protected methods perform different connection steps and
 * public methods call them in a predefined order.If you want to implement your own connection procedure then subclass.
 *
 * <p>
 * Each user needs their own instance of Connection because they need a separate set of cookies
 *
 */

public class Connection {

    // one client per class to handle separate cookies
    protected CloseableHttpClient httpclient = HttpClients.createDefault();

    // Sites are hardcoded for now, because getting them from the server involves action-message format
    // Two ways to deal with this are: hardcode all servers or add amf interaction

    private String gameSiteHttps = "https://www.thesettlersonline.ru";
    private String gameSite = "www.thesettlersonline.ru";
    private String mainPage = "/ru/главная-страница";
    private String loginPath = "/ru//api/user/login?name=%s&password=%s&rememberUser=on";
    private String bindPathHttp = "http://w03chat01.thesettlersonline.ru/http-bind/";
    private String bindPath = "w03chat01.thesettlersonline.ru/http-bind/";


    private Session session;
    private ArrayBlockingQueue<SentMessage> messages = new ArrayBlockingQueue<>(10);
    private XMLHelper xmlHelper = new XMLHelper();
    private volatile HttpPost hPost;

    /**
     * @param email the email used to log in to Uplay
     * @param password the password of the Uplay account
     */
    public Connection(String email, String password) {
        this.session = new Session(email, password);
    }

    /**
     * This is a convenience method to deal with all connection steps. It calls overridable methods in a predefined
     * order:
     * <code>
     *     login();
     *     checkIn();
     *     receiveAuthHash();
     *     bindAll();
     * </code>
     *
     * @return the name of the player which is resolved at the authorization step
     * @throws BadCredentialsException if the user entered incorrect email or password
     * @throws UplayDownException if the uplay server is down at the login step
     */
    public String connect() {
        login();
        checkIn();
        String name = receiveAuthHash();
        bindAll();
        return name;
    }

    /**
     * This is a convenience method to deal with all connection steps. It allows you to track progress via the
     * <i>stage</i> parameter.
     * This method calls overridable methods in a predefined order:
     * <code>
     *     login();
     *     checkIn();
     *     receiveAuthHash();
     *     bindAll();
     * </code>
     *
     * @param stage the object through which the stage of connection procedure can be communicated
     * @return the name of the player which is resolved at the authorization step
     * @throws BadCredentialsException if the user entered incorrect email or password
     * @throws UplayDownException if the uplay server is down at the login step
     */
    public String connect(SimpleObjectProperty<Stage> stage) {
        if (stage==null) {
            stage = new SimpleObjectProperty<>();
        }
        stage.set(Stage.LOGIN);
        login();
        stage.set(Stage.CHECK_IN);
        checkIn();
        stage.set(Stage.AUTH);
        String name = receiveAuthHash();
        stage.set(Stage.BIND);
        bindAll();
        return name;
    }

    //TODO implement the restart procedure, hopefully without repeated login (no need to store password then)
    public void restart() {

    }

    /**
     * Retrieves the friend list from the chat server and their status: online or offline.
     * Nicknames are in lower case even if their ingame names use uppercase letters. This is because chat server stores
     * the names in lower case.
     * @return map of pairs <i>String nickname : String status</i>. Status is either "online" or "offline"
     */
    public Map<String, String> getFriendsAndStatusFromServer() {
        String path = bindPathHttp;
        String body = xmlHelper.prepareGetFriendsBody(session.sid, session.nextRid());
        String response = helper(path, body);
        List<String> friends = xmlHelper.extractFriendsFromResponse(response);

        body = xmlHelper.prepareDummyBody(session.sid, session.nextRid());
        helper(path, body);

        body = xmlHelper.prepareDummyBody(session.sid, session.nextRid());
        String statusBody = helper(path, body);
        List<String> onlineFriends = xmlHelper.whoIsOnline(statusBody);
        Map<String, String> friendsWithStatus = new TreeMap<>();

        for (String friend : friends) {
            friendsWithStatus.put(friend, "offline");
        }

        for (String onlineFriend : onlineFriends) {
            friendsWithStatus.put(onlineFriend, "online");
        }
        return friendsWithStatus;
    }

    /**
     * Binds a chat channel. Before you can use a chat channel you must bind it first. This method also returns
     * chat history: up to 15 messages from that channel.
     * @param chatName the name of the chat channel
     * @return up to 15 messages of history from the channel
     */
    public List<ChatMessage> bindChat(String chatName) {
        String path = bindPathHttp;
        String body = xmlHelper.prepareBindChatBody(session.sid, session.nextRid(), chatName, session.name);
        helper(path, body);

        body = xmlHelper.prepareDummyBody(session.sid, session.nextRid());
        String history = helper(path, body);
        return xmlHelper.extractHistory(history);
    }

    /**
     * Waits in a loop until a non-empty message from chat arrives.
     * @return a message from chat. This can be text message or a status change of a friend.
     */
    public ChatMessage chatLoop() {
        String path = bindPathHttp;
        while (true) {
            String response = "";
            try {
                String body;
                if (!messages.isEmpty()) {
                    SentMessage message = messages.take();
                    body = xmlHelper.prepareMessageBody(message, session);
                } else {
                    body = xmlHelper.prepareChatBody(session.sid, session.nextRid());
                }
                response = helper(path, body);
                if ("<body xmlns=\"http://jabber.org/protocol/httpbind\"></body>".equals(response)) {
                    continue;
                }
                if (response.contains("<presence")) {
                    int from = response.indexOf("from=");
                    String further = response.substring(from + 6);
                    String name = further.substring(0, further.indexOf("@"));
                    System.out.println(name + " is online or offline");
                    continue;
                }
                return xmlHelper.extractMessage(response);
            } catch (Exception e) {
                // not all responses are handled now, ignoring some of them helps to test other things
                // print response body is ok here
            }
        }
    }

    /**
     * Sends a message to chat.
     * @param message message to be sent to chat.
     */
    public void sendMessage(SentMessage message) {
        messages.add(message);
        hPost.abort();
    }

    protected void login() {
        String path = String.format(gameSiteHttps + loginPath, session.email, session.password);
        HttpPost httpPost = new HttpPost(path);
        CloseableHttpResponse response = doPost(httpPost);
        try {
            String responseBody = EntityUtils.toString(response.getEntity());
            int status = response.getStatusLine().getStatusCode();
            if (status != SC_OK) {
                throw new RuntimeException("server didn't respond with HTTP/1.1 200 OK\n" +
                        response.getStatusLine() + "\n" +
                        responseBody);
            }
            if (responseBody.contains("UPLAYDOWN")) {
                throw new UplayDownException();
            } else if (responseBody.contains("FAILED")) {
                System.out.println(response);
                System.out.println(responseBody);
                throw new BadCredentialsException();
            }
        } catch (IOException e) {
            throw new RuntimeException("an IOException occurred", e);
        }
        close(response);
    }

    protected void close(CloseableHttpResponse response) {
        try {
            response.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void checkIn() {
        String path = gameSiteHttps + mainPage;
        HttpGet httpGet = new HttpGet(path);
        CloseableHttpResponse response = doGet(httpGet);
        Header[] cookies = response.getHeaders("Set-Cookie");
        for (Header h : cookies) {
            for (HeaderElement el : h.getElements()) {
                if (el.getName().equals("dsoAuthUser")) {
                    session.userId =el.getValue();
                }
                if (el.getName().equals("dsoAuthToken")) {
                    session.authToken=el.getValue();
                }
            }
        }
        close(response);
    }

    protected String receiveAuthHash() {
        String path = "http://w03bb01.thesettlersonline.ru/authenticate";
        HttpPost httpPost = new HttpPost(path);
        String authText = String.format("DSOAUTHTOKEN=%s&DSOAUTHUSER=%s", session.authToken, session.userId);
        HttpEntity entity = new StringEntity(authText, ContentType.TEXT_HTML);
        httpPost.setEntity(entity);
        CloseableHttpResponse response = doPost(httpPost);
        try {
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
            String[] tokens = responseBody.split("\\|");
            session.name =tokens[1];
            session.authToken=tokens[2];
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        close(response);
        return session.name;
    }

    protected void bindAll() {
        bind();
        bind2();
        bind3();
        bind4();
        bind5();
    }

    protected void bind() {
        String path = bindPathHttp;
        HttpPost httpPost = new HttpPost(path);
        String body = xmlHelper.prepareFirstBindBody(session.nextRid());
        HttpEntity entity = new StringEntity(body, ContentType.TEXT_HTML);
        httpPost.setEntity(entity);
        CloseableHttpResponse response = doPost(httpPost);
        try {
            String result = EntityUtils.toString(response.getEntity());
            session.sid= xmlHelper.extractSid(result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        close(response);
    }

    protected void bind2() {
        String path = bindPathHttp;
        HttpPost httpPost = new HttpPost(path);
        String authToken = session.name + "@null\0" + session.name + "\0" + session.authToken + "\0null";
        String base64Token = Base64.getEncoder().encodeToString(authToken.getBytes());
        String body = xmlHelper.prepareAuthBody(session.sid, session.nextRid(), base64Token);
        HttpEntity entity = new StringEntity(body, ContentType.TEXT_HTML);
        httpPost.setEntity(entity);
        CloseableHttpResponse response = doPost(httpPost);
        close(response);
    }

    protected void bind3() {
        String path = bindPathHttp;
        HttpPost httpPost = new HttpPost(path);
        String body = "<body sid=\""+session.sid+"\" rid=\"" + session.nextRid()
                + "\" xmpp:restart=\"true\" xmlns=\"http://jabber.org/protocol/httpbind\" " +
                "xml:lang=\"en\" to=\"w03chat01.thesettlersonline.ru\" xmlns:xmpp=\"urn:xmpp:xbosh\" />";
        HttpEntity entity = new StringEntity(body, ContentType.TEXT_HTML);
        httpPost.setEntity(entity);
        CloseableHttpResponse response = doPost(httpPost);
        close(response);
    }

    protected void bind4() {
        String path = bindPathHttp;
        HttpPost httpPost = new HttpPost(path);
        String body = "<body sid=\""+session.sid+"\" rid=\""+session.nextRid()+"\" " +
                "xmlns=\"http://jabber.org/protocol/httpbind\"><iq type=\"set\" " +
                "id=\"iq_1\"><bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"><resource>xiff-bosh</resource>" +
                "</bind></iq></body>";
        HttpEntity entity = new StringEntity(body, ContentType.TEXT_HTML);
        httpPost.setEntity(entity);
        CloseableHttpResponse response = doPost(httpPost);
        close(response);
    }

    protected void bind5() {
        String path = bindPathHttp;
        HttpPost httpPost = new HttpPost(path);
        String body = "<body sid=\""+session.sid+"\" rid=\""+session.nextRid()+"\" " +
                "xmlns=\"http://jabber.org/protocol/httpbind\"><iq type=\"set\" " +
                "id=\"iq_3\"><session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\" /></iq></body>";
        HttpEntity entity = new StringEntity(body, ContentType.TEXT_HTML);
        httpPost.setEntity(entity);
        CloseableHttpResponse response = doPost(httpPost);
        close(response);
    }

    //TODO get rid of this helper method
    private String helper(String path, String body) {
        HttpPost httpPost = new HttpPost(path);
        hPost = httpPost;
        String result = "none";
        try {
            httpPost.setEntity(new ByteArrayEntity(body.getBytes("UTF-8")));
            CloseableHttpResponse response = httpclient.execute(httpPost);
            result = EntityUtils.toString(response.getEntity(), "UTF-8");
            response.close();
        } catch (Exception e) {

        }
        return result;
    }

    protected CloseableHttpResponse doGet(HttpGet httpGet) {
        try {
            CloseableHttpResponse response = httpclient.execute(httpGet);
            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected CloseableHttpResponse doPost(HttpPost httpPost) {
        try {
            CloseableHttpResponse response = httpclient.execute(httpPost);
            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private class XMLHelper {
        private final SAXReader xmlReader = new SAXReader();

        private String extractSid(String body) {
            Pattern pattern = Pattern.compile("sid=\"(.*?)\"");
            Matcher m = pattern.matcher(body);
            m.find();
            return m.group(1);
        }

        private String prepareFirstBindBody(int rid) {
            return String.format("<body rid=\"%d\" xmlns:xmpp=\"urn:xmpp:xbosh\" " +
                    "xmlns=\"http://jabber.org/protocol/httpbind\" " +
                    "secure=\"false\" wait=\"20\" hold=\"1\" xml:lang=\"en\" " +
                    "xmpp:version=\"1.0\" to=\""+ bindPath +"\" ver=\"1.6\" />", rid);
        }

        private String prepareAuthBody(String sid, int rid, String authToken) {
            return String.format("<body sid=\"%s\" rid=\"%d\" xmlns=\"http://jabber.org/protocol/httpbind\">" +
                    "<auth xmlns=\"urn:ietf:params:xml:ns:xmpp-sasl\" mechanism=\"PLAIN\">%s</auth></body>", sid, rid, authToken);
        }


        private String prepareChatBody(String sid, int rid) {
            return String.format("<body sid=\"%s\" rid=\"%d\" xmlns=\"http://jabber.org/protocol/httpbind\" />", sid, rid);
        }

        private String prepareMessageBody(SentMessage message, Session session) {
            String body;
            if (message.getChannel().equals("private")) {
                body = "<body rid=\""+session.nextRid()+"\" xmlns=\"http://jabber.org/protocol/httpbind\" " +
                        "sid=\""+session.sid+"\"><message to=\""+message.getTo()+"@w03chat01.thesettlersonline.ru\" " +
                        "id=\"m_100\" from=\""+session.name+"@w03chat01.thesettlersonline.ru\"><body>"+message.getText()+"</body>" +
                        "<bbmsg playerid=\""+session.userId+"\" playertag=\""+"null"+"\" playername=\""+session.name+"\" xmlns=\"bbmsg\" />" +
                        "</message></body>";
            } else {
                body = "<body rid=\""+session.nextRid()+"\" xmlns=\"http://jabber.org/protocol/httpbind\" sid=\""+session.sid+"\">" +
                        "<message to=\""+message.getChannel()+"@conference.w03chat01.thesettlersonline.ru\" id=\"m_73\" " +
                        "from=\""+"xитрый_xаджит"+"@w03chat01.thesettlersonline.ru\" type=\"groupchat\"><body>.</body>" +
                        "<bbmsg playerid=\""+session.userId+"\" playertag=\""+"null"+"\" playername=\""+session.name+"\" " +
                        "xmlns=\"bbmsg\" /></message></body>";
            }
            return body;
        }

        private String prepareBindChatBody(String sid, int rid, String chat, String name) {
            return String.format("<body sid=\"%s\" rid=\"%d\" xmlns=\"http://jabber.org/protocol/httpbind\">" +
                    "<presence to=\"%s@conference.%s/%s\">" +
                    "<priority>0</priority><x xmlns=\"http://jabber.org/protocol/muc\" /></presence></body>", sid, rid, chat, bindPath, name);
        }

        private String prepareDummyBody(String sid, int rid) {
            return String.format("<body sid=\"%s\" rid=\"%d\" " +
                    "xmlns=\"http://jabber.org/protocol/httpbind\"><presence>" +
                    "<status>Online</status><priority>5</priority></presence></body>", sid, rid);
        }

        private String prepareGetFriendsBody(String sid, int rid) {
            return  String.format("<body sid=\"%s\" rid=\"%d\" xmlns=\"http://jabber.org/protocol/httpbind\">" +
                    "<iq type=\"get\" id=\"roster_5\"><query xmlns=\"jabber:iq:roster\" /></iq></body>", sid, rid);
        }

        private ChatMessage extractMessage(String body) {
            String cleanBody = destroyNamespaces(body); // getting rid of namespaces simplifies XML routine
            Document document = null;
            try {
                document = xmlReader.read(new StringReader(cleanBody));
            } catch (Exception e) {
                // we all love checked exceptions!
            }
            Element el = document.getRootElement();
            Node node = el.selectSingleNode("message");
            if (node==null) {
                System.out.println(cleanBody);
            }
            return extractMessage(node);
        }

        private ChatMessage extractMessage(Node node) {
            Node messageNode = node.selectSingleNode("body");
            if (messageNode == null) {
                return null;
            }
            String text = messageNode.getText();

            String type = node.valueOf("@type");
            String channelName;
            if ("groupchat".equals(type)) {
                String channel = node.valueOf("@from");
                channelName = getChatName(channel);
            } else {
                channelName = "private";
            }

            if (channelName.equals("global")) {
                channelName = "global-1";
            }

            Node bbmsg = node.selectSingleNode("bbmsg");
            String playerName = bbmsg.valueOf("@playername");
            String guild = bbmsg.valueOf("@playertag");
            String id = bbmsg.valueOf("@playerid");

            LocalDateTime dateTime;
            Node timeNode = node.selectSingleNode("delay");
            if (timeNode!=null) {
                String time = timeNode.valueOf("@stamp");
                dateTime = getMessageTime(time);
            } else {
                dateTime = LocalDateTime.now();
            }
            return new ChatMessage(channelName, guild, playerName, id, dateTime, text);
        }

        private List<ChatMessage> extractHistory(String body) {
            List<ChatMessage> messages = new ArrayList<>();
            String cleanBody = destroyNamespaces(body); // getting rid of namespaces simplifies XML routine
            Document document = null;
            try {
                document = xmlReader.read(new StringReader(cleanBody));
            } catch (Exception e) {
                // we all love checked exceptions!
            }
            Element el = document.getRootElement();
            List<Node> nodes = el.selectNodes("message");
            for (Node node : nodes) {
                ChatMessage chatMessage = extractMessage(node);
                if (chatMessage==null) {
                    continue;
                }
                messages.add(chatMessage);
            }
            return messages;
        }

        private LocalDateTime getMessageTime(String time) {
            try {
                TimeZone tz = TimeZone.getDefault();
                int offset = tz.getRawOffset();
                int seconds = offset / 1000;
                return LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME).plusSeconds(seconds);
            } catch (Exception e) {
                System.out.println(time);
                e.printStackTrace();
                throw new RuntimeException();
            }
        }

        private List<String> extractFriendsFromResponse(String response) {
            List<String> friends = new ArrayList<>();
            String cleanResponse = destroyNamespaces(response);
            Document document = null;
            try {
                document = xmlReader.read(new StringReader(cleanResponse));
            } catch (Exception e) {
                // we all love checked exceptions!
            }
            Node iq = document.getRootElement().selectSingleNode("iq");
            Node query = iq.selectSingleNode("query");
            List<Node> nodes = query.selectNodes("item");

            for (Node node : nodes) {
                String name = node.valueOf("@jid");
                name = name.split("@")[0];
                friends.add(name);
            }

            return friends;
        }

        private List<String> whoIsOnline(String response) {
            List<String> onlineFriends = new ArrayList<>();
            String cleanResponse = destroyNamespaces(response);
            Document document = null;
            try {
                document = xmlReader.read(new StringReader(cleanResponse));
            } catch (Exception e) {
                // we all love checked exceptions!
            }
            Node body = document.getRootElement();
            List<Node> statuses = body.selectNodes("presence");

            for (Node node : statuses) {
                String name = node.valueOf("@from");
                name = name.split("@")[0];
                onlineFriends.add(name);
            }
            return onlineFriends;
        }

        private String destroyNamespaces(String body) {
            return body.replaceAll("xmlns=[\"\'].+?[\"\']", "");
        }

        private String getChatName(String fromString) {
            String chatName = null;
            if (fromString.startsWith("help")) {
                chatName="help";
            }
            else if (fromString.startsWith("global")) {
                chatName="global";
            }
            else if (fromString.startsWith("trade")) {
                chatName="trade";
            }
            return chatName;
        }
    }

    private class Session {
        String email, password, name, authToken, userId, sid;
        private AtomicInteger rid = new AtomicInteger(new Random().nextInt(1_000_000));
        int nextRid() {
            return rid.getAndIncrement();
        }

        private Session(String email, String password) {
            this.email=email;
            this.password=password;
        }
    }

}


