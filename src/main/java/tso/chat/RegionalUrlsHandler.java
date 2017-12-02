package tso.chat;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class RegionalUrlsHandler {

    private static Map<Region, RegionalUrlsHandler> handlers = new ConcurrentHashMap<>();

    private final String site;
    private final String domain;
    private final String language;
    private final String mainPage;
    private final Map<String, Map<String, String>> realms = new HashMap<>();

    static RegionalUrlsHandler getHandler(Region region) {
        if (handlers.containsKey(region)) {
            return handlers.get(region);
        } else {
            RegionalUrlsHandler handler = new RegionalUrlsHandler(region);
            handlers.put(region, handler);
            return handler;
        }
    }

    private RegionalUrlsHandler(Region region) {
        if (region==Region.TSOTESTING) {
            site="tsotesting.";
        } else {
            site="thesettlersonline.";
        }
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            InputStream is = classLoader.getResourceAsStream("regions.xml");
            SAXReader xmlReader = new SAXReader();
            Document document = xmlReader.read(is);
            List<Node> nodes = document.selectNodes("/regions/region[@name = '"+region.name()+"']");
            Node regionNode = nodes.get(0);
            domain = regionNode.selectSingleNode("domain").getText();
            language = regionNode.selectSingleNode("language").getText();
            mainPage = regionNode.selectSingleNode("main_page").getText();
            Node realmsNode = regionNode.selectSingleNode("realms");
            List<Node> realmsList = realmsNode.selectNodes("realm");
            for (Node realm : realmsList) {
                String no = realm.valueOf("@no");
                String bb = realm.selectSingleNode("bb").getText();
                String chat = realm.selectSingleNode("chat").getText();
                Map<String, String> servers = new HashMap<>();
                servers.put("bb", bb);
                servers.put("chat", chat);
                realms.put(no, servers);
            }
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        }
    }

    Set<String> getRealms() {
        return realms.keySet();
    }

    String getSite() {
        return "www."+ site +domain;
    }

    String getSiteHttps() {
        return "https://"+getSite();
    }

    String getMainPage() {
        return getSiteHttps()+"/"+language+"/"+mainPage;
    }

    String getLoginPath() {
        return getSiteHttps()+"/"+language+"//api/user/login?name=%s&password=%s&rememberUser=on";
    }

    String getAuthPath(String realmNo) {
        Map<String, String> servers = realms.get(realmNo);
        String bb = servers.get("bb");
        return "http://"+bb+"."+ site +domain+"/authenticate";
    }

    String getBindPath(String realmNo) {
        Map<String, String> servers = realms.get(realmNo);
        String chat = servers.get("chat");
        return chat+"."+ site +domain+"/http-bind/";
    }

    String getChatPath(String realmNo) {
        Map<String, String> servers = realms.get(realmNo);
        String chat = servers.get("chat");
        return chat+"."+ site +domain;
    }

    String getBindPathHttp(String realmNo) {
        return "http://"+getBindPath(realmNo);
    }

}
