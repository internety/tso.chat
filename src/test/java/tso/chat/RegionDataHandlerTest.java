package tso.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static tso.chat.Region.RUSSIA;

class RegionDataHandlerTest {

    private static RegionalDataHandler handler;
    private static final String REALM_NO = "3";

    @BeforeAll
    static void setup() {
        handler = RegionalDataHandler.getHandler(RUSSIA);
    }

    @Test
    void getSiteTest() {
        String site = handler.getSite();
        assertEquals("www.thesettlersonline.ru", site);
    }

    @Test
    void getSiteHttpsTest() {
        String siteHttps = handler.getSiteHttps();
        assertEquals("https://www.thesettlersonline.ru", siteHttps);
    }

    @Test
    void getMainPageTest() {
        String mainPage = handler.getMainPage();
        assertEquals("https://www.thesettlersonline.ru/ru/главная-страница", mainPage);
    }

    @Test
    void getLoginPathTest() {
        String loginPath = handler.getLoginPath();
        String expected = "https://www.thesettlersonline.ru/ru//api/user/login?name=%s&password=%s&rememberUser=on";
        assertEquals(expected, loginPath);
    }

    @Test
    void getAuthPathTest() {
        String authPath = handler.getAuthPath(REALM_NO);
        assertEquals("http://w03bb01.thesettlersonline.ru/authenticate", authPath);
    }

    @Test
    void getBindPathTest() {
        String bindPath = handler.getBindPath(REALM_NO);
        assertEquals("w03chat01.thesettlersonline.ru/http-bind/", bindPath);
    }

    @Test
    void getChatPathTest() {
        String chatPath = handler.getChatPath(REALM_NO);
        assertEquals("w03chat01.thesettlersonline.ru", chatPath);
    }

    @Test
    void getBindPathHttpTest() {
        String bindPathHttp = handler.getBindPathHttp(REALM_NO);
        assertEquals("http://w03chat01.thesettlersonline.ru/http-bind/", bindPathHttp);
    }

    @Test
    void getRealmsTest() {
        Set<String> realms = handler.getRealms();
        assertEquals(3, realms.size());
    }

}
