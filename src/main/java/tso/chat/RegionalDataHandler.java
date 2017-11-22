package tso.chat;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RegionalDataHandler {
    private static final String SITE = "thesettlersonline.";
    private static Map<Region, RegionalDataHandler> handlers = new ConcurrentHashMap<>();

    private final SAXReader xmlReader = new SAXReader();
    private final String domain;
    private final String mainPage;
    private final Map<String, Map<String, String>> realms = new HashMap<>();

    Set<String> getRealms() {
        return realms.keySet();
    }

    static RegionalDataHandler getHandler(Region region) {
        if (handlers.containsKey(region)) {
            return handlers.get(region);
        } else {
            RegionalDataHandler handler = new RegionalDataHandler(region);
            handlers.put(region, handler);
            return handler;
        }
    }

    private RegionalDataHandler(Region region) {
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            InputStream is = classLoader.getResourceAsStream("regions.xml");
            Document document = xmlReader.read(is);
            List<Node> nodes = document.selectNodes("/regions/region[@name = '"+region.toString()+"']");
            Node regionNode = nodes.get(0);
            domain = regionNode.selectSingleNode("domain").getText();
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

    String getSite() {
        return "www."+SITE+domain;
    }

    String getSiteHttps() {
        return "https://"+getSite();
    }

    String getMainPage() {
        return getSiteHttps()+"/"+domain+"/"+mainPage;
    }

    String getAuthPath(String realmNo) {
        Map<String, String> servers = realms.get(realmNo);
        String bb = servers.get("bb");
        return "http://"+bb+"."+SITE+domain+"/authenticate";
    }

    String getLoginPath() {
        return getSiteHttps()+"/"+domain+"//api/user/login?name=%s&password=%s&rememberUser=on";
    }

    String getBindPath(String realmNo) {
        Map<String, String> servers = realms.get(realmNo);
        String chat = servers.get("chat");
        return chat+"."+SITE+domain+"/http-bind/";
    }

    String getBindPathHttp(String realmNo) {
        return "http://"+getBindPath(realmNo);
    }


}
