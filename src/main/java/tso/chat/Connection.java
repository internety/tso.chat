package tso.chat;

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

import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_OK;

/**
 * This class provides chat connection and message exchange services.
 * As the default connection procedure is handled by {@link Chat} implementations, you should not use this class directly
 * unless you are not satisfied with the default process or want to add functionality.
 *
 * <p>
 * Each user needs their own instance of Connection because they need a separate set of cookies
 *
 */

public class Connection {

    // one client per class to handle separate cookies
    protected CloseableHttpClient httpclient = HttpClients.createDefault();

    // generates URLs based on the region and realm
    protected RegionalUrlsHandler urlHandler;

    // generates ugly XML stuff
    protected XMLHelper xmlHelper = new XMLHelper();

    // here the current session data is stored
    protected Session session;

    // used from other threads to interrupt current chat loop iteration,
    // so the new iteration would pick up a message to send
    protected volatile HttpPost hPost;

    // new messages are stored here until the chat loop picks them up and sends them
    protected ArrayBlockingQueue<SentMessage> messages = new ArrayBlockingQueue<>(10);

    /**
     * @param email  the email used to log in to Uplay
     * @param password  the password of the Uplay account
     */
    public Connection(String email, String password, Region region) {
        this.session = new Session(email, password);
        urlHandler = RegionalUrlsHandler.getHandler(region);
    }

    //TODO implement the restart procedure, hopefully without repeated login (no need to store password then)
    public void restart() {
        bindAll();
    }

    /**
     * Retrieves the friend list from the chat server and their status: online or offline.
     * Nicknames are in lower case even if their ingame names use uppercase letters. This is because chat server stores
     * the names in lower case.
     * @return map of pairs <i>String nickname : String status</i>. Status is either "online" or "offline"
     */
    public Map<String, Status> getFriendsAndStatusFromServer() {
        String path = urlHandler.getBindPathHttp(session.realm);
        String body = xmlHelper.prepareGetFriendsBody(session.sid, session.nextRid());
        ResponseContent content = doPost(path, body);
        List<String> friends = xmlHelper.extractFriendsFromResponse(content.body);

        body = xmlHelper.prepareDummyBody(session.sid, session.nextRid());
        doPost(path, body);

        body = xmlHelper.prepareDummyBody(session.sid, session.nextRid());
        content = doPost(path, body);
        String statusBody = content.body;
        List<String> onlineFriends = xmlHelper.whoIsOnline(statusBody);
        Map<String, Status> friendsWithStatus = new TreeMap<>();

        for (String friend : friends) {
            friendsWithStatus.put(friend, Status.OFFLINE);
        }

        for (String onlineFriend : onlineFriends) {
            friendsWithStatus.put(onlineFriend, Status.ONLINE);
        }
        return friendsWithStatus;
    }

    /**
     * Binds a chat channel. Before you can use a chat channel you must bind it first. This method also returns
     * chat history: up to 15 messages from that channel.
     * @param chatName  the name of the chat channel
     * @return up to 15 messages of history from the channel
     */
    public List<ChatMessage> bindChat(String chatName) {
        String path = urlHandler.getBindPathHttp(session.realm);
        String body = xmlHelper.prepareBindChatBody(session.sid, session.nextRid(), chatName, session.name);
        doPost(path, body);

        body = xmlHelper.prepareDummyBody(session.sid, session.nextRid());
        ResponseContent content = doPost(path, body);
        String history = content.body;
        return xmlHelper.extractHistory(history);
    }

    /**
     * Waits in a loop until a non-empty message from chat arrives.
     * @return a message from chat. This can be text message or a status change of a friend.
     */
    public ChatMessage chatLoop() {
        String path = urlHandler.getBindPathHttp(session.realm);
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
                e.printStackTrace();
            }
        }
    }

    /**
     * Sends a message to chat.
     * @param message  message to be sent to chat.
     */
    public void sendMessage(SentMessage message) {
        messages.add(message);
        hPost.abort();
    }

    public void login() throws BadCredentialsException, UplayDownException {
        String path = String.format(urlHandler.getLoginPath(), session.email, session.password);
        ResponseContent content = doPost(path);
        int status = content.statusCode;
        if (status != SC_OK) {
            throw new RuntimeException("server didn't respond with HTTP/1.1 200 OK\n" +
                    content.statusCode + "\n" +
                    content.body);
        }
        if (content.body.contains("UPLAYDOWN")) {
            throw new UplayDownException();
        } else if (content.body.contains("FAILED")) {
            System.out.println(content.body);
            throw new BadCredentialsException();
        }
    }

    public void checkIn() {
        String path = urlHandler.getMainPage();
        ResponseContent content = doGet(path);
        Header[] cookies = content.cookies;
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
    }

    public String receiveAuthHash() {
        // asking game server for URLs involves amf exchange, so it's easier just to bruteforce all realms
        Set<String> realms = urlHandler.getRealms();
        for (String realm : realms) {
            String path = urlHandler.getAuthPath(realm);
            String authText = String.format("DSOAUTHTOKEN=%s&DSOAUTHUSER=%s", session.authToken, session.userId);
            ResponseContent content = doPost(path, authText);
            if (content.statusCode==SC_FORBIDDEN) {
                continue;
            }
            String[] tokens = content.body.split("\\|");
            session.name =tokens[1];
            session.authToken=tokens[2];
            session.realm=realm;
            break;
        }
        return session.name;
    }

    public void bindAll() {
        bind();
        bind2();
        bind3();
        bind4();
        // it looks like bind5() is not needed
        // bind5();
    }

    public void bind() {
        String path = urlHandler.getBindPathHttp(session.realm);
        String body = xmlHelper.prepareFirstBindBody(session.nextRid());
        ResponseContent content = doPost(path, body);
        session.sid= xmlHelper.extractSid(content.body);
    }

    public void bind2() {
        String path = urlHandler.getBindPathHttp(session.realm);
        String authToken = session.name + "@null\0" + session.name + "\0" + session.authToken + "\0null";
        String base64Token = Base64.getEncoder().encodeToString(authToken.getBytes());
        String body = xmlHelper.prepareAuthBody(session.sid, session.nextRid(), base64Token);
        doPost(path, body);
    }

    public void bind3() {
        String path = urlHandler.getBindPathHttp(session.realm);
        String body = "<body sid=\""+session.sid+"\" rid=\"" + session.nextRid()
                + "\" xmpp:restart=\"true\" xmlns=\"http://jabber.org/protocol/httpbind\" " +
                "xml:lang=\"en\" to=\""+ urlHandler.getChatPath(session.realm)+"\" xmlns:xmpp=\"urn:xmpp:xbosh\" />";
        doPost(path, body);
    }

    public void bind4() {
        String path = urlHandler.getBindPathHttp(session.realm);
        String body = "<body sid=\""+session.sid+"\" rid=\""+session.nextRid()+"\" " +
                "xmlns=\"http://jabber.org/protocol/httpbind\"><iq type=\"set\" " +
                "id=\"iq_1\"><bind xmlns=\"urn:ietf:params:xml:ns:xmpp-bind\"><resource>xiff-bosh</resource>" +
                "</bind></iq></body>";
        doPost(path, body);
    }

    public void bind5() {
        String path = urlHandler.getBindPathHttp(session.realm);
        String body = "<body sid=\""+session.sid+"\" rid=\""+session.nextRid()+"\" " +
                "xmlns=\"http://jabber.org/protocol/httpbind\"><iq type=\"set\" " +
                "id=\"iq_3\"><session xmlns=\"urn:ietf:params:xml:ns:xmpp-session\" /></iq></body>";
        doPost(path, body);
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

    private ResponseContent doGet(HttpGet httpGet) {
        try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
            int code = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), "UTF-8");
            Header[] cookies = response.getHeaders("Set-Cookie");
            return new ResponseContent(code, body, cookies);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ResponseContent doGet(String path) {
        HttpGet httpGet = new HttpGet(path);
        return doGet(httpGet);
    }

    private ResponseContent doPost(String path, String body) {
        HttpPost httpPost = new HttpPost(path);
        HttpEntity entity = new StringEntity(body, ContentType.TEXT_HTML);
        httpPost.setEntity(entity);
        return doPost(httpPost);
    }

    private ResponseContent doPost(String path) {
        HttpPost httpPost = new HttpPost(path);
        return doPost(httpPost);
    }

    private ResponseContent doPost(HttpPost httpPost) {
        hPost = httpPost;
        try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
            int code = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), "UTF-8");
            Header[] cookies = response.getHeaders("Set-Cookie");
            return new ResponseContent(code, body, cookies);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected class XMLHelper {
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
                    "xmpp:version=\"1.0\" to=\""+ urlHandler.getBindPath(session.realm) +"\" ver=\"1.6\" />", rid);
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
                        "sid=\""+session.sid+"\"><message to=\""+message.getTo()+"@"+ urlHandler.getChatPath(session.realm)+"\" " +
                        "id=\"m_100\" from=\""+session.name+"@"+ urlHandler.getChatPath(session.realm)+"\"><body>"+message.getText()+"</body>" +
                        "<bbmsg playerid=\""+session.userId+"\" playertag=\""+"null"+"\" playername=\""+session.name+"\" xmlns=\"bbmsg\" />" +
                        "</message></body>";
            } else {
                body = "<body rid=\""+session.nextRid()+"\" xmlns=\"http://jabber.org/protocol/httpbind\" sid=\""+session.sid+"\">" +
                        "<message to=\""+message.getChannel()+"@conference."+ urlHandler.getChatPath(session.realm)+"\" id=\"m_73\" " +
                        "from=\""+session.name+"@"+ urlHandler.getChatPath(session.realm)+"\" type=\"groupchat\"><body>.</body>" +
                        "<bbmsg playerid=\""+session.userId+"\" playertag=\""+"null"+"\" playername=\""+session.name+"\" " +
                        "xmlns=\"bbmsg\" /></message></body>";
            }
            return body;
        }

        private String prepareBindChatBody(String sid, int rid, String chat, String name) {
            return String.format("<body sid=\"%s\" rid=\"%d\" xmlns=\"http://jabber.org/protocol/httpbind\">" +
                    "<presence to=\"%s@conference.%s/%s\">" +
                    "<priority>0</priority><x xmlns=\"http://jabber.org/protocol/muc\" /></presence></body>", sid, rid, chat, urlHandler.getBindPath(session.realm), name);
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
            try {
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
            } catch (NullPointerException e) {
                System.out.println(response);
                throw new RuntimeException(e);
            }
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
        private String email, password, name, authToken, userId, sid, realm;
        private AtomicInteger rid = new AtomicInteger(new Random().nextInt(1_000_000));
        int nextRid() {
            return rid.getAndIncrement();
        }

        private Session(String email, String password) {
            this.email=email;
            this.password=password;
        }
    }

    private class ResponseContent {
        private int statusCode;
        private String body;
        private Header[] cookies;
        private ResponseContent(int statusCode, String body, Header[] cookies) {
            this.statusCode = statusCode;
            this.body       = body;
            this.cookies    = cookies;
        }
    }
}


