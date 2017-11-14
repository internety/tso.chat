package tso.chat;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by reax on 13.11.17.
 */
public class EnglishLocalization implements Localization {
    private static final Map<String, String> pairs = new HashMap<>();
    static {
        pairs.put("login", "Logging in");
        pairs.put("check in", "Checking in");
        pairs.put("auth", "Authorization");
        pairs.put("bind", "Binding");
    }
    public String getLocalizedFor(String text) {
        return pairs.get(text);
    }
}
