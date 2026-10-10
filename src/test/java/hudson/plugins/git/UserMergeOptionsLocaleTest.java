package hudson.plugins.git;

import java.util.Locale;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserMergeOptionsLocaleTest {

    private Locale defaultLocale;

    @BeforeEach
    void setTurkishLocale() {
        defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void mergeStrategyRoundTripsInTurkishLocale() {
        for (MergeCommand.Strategy strategy : MergeCommand.Strategy.values()) {
            UserMergeOptions options = new UserMergeOptions("master");
            options.setMergeStrategy(strategy);
            assertEquals(strategy, options.getMergeStrategy());
        }
    }

    @Test
    void mergeStrategyFromConfigReadInTurkishLocale() {
        UserMergeOptions options = new UserMergeOptions("origin", "master", "recursive", MergeCommand.GitPluginFastForwardMode.FF);
        assertEquals(MergeCommand.Strategy.RECURSIVE, options.getMergeStrategy());
    }
}
