package tso.chat;

/**
 * Created by reax on 13.11.17.
 */
public class LocalizationFactory {
    private static final Localization ENGLISH_LOCALIZATION = new EnglishLocalization();
    static Localization getLocalizationInstance(String language) {
        return ENGLISH_LOCALIZATION;
    }
}
