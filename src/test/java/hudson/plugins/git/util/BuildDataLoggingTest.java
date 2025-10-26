package hudson.plugins.git.util;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author Mark Waite
 */
class BuildDataLoggingTest {

    private BuildData data;

    private final Level ORIGINAL_LEVEL = BuildData.LOGGER.getLevel();
    private final boolean ORIGINAL_USE_PARENT_HANDLERS = BuildData.LOGGER.getUseParentHandlers();

    private LogHandler handler = null;

    @BeforeEach
    void beforeEach() {
        data = new BuildData();

        handler = new LogHandler();
        handler.setLevel(Level.ALL);
        BuildData.LOGGER.setUseParentHandlers(false);
        BuildData.LOGGER.addHandler(handler);
        BuildData.LOGGER.setLevel(Level.ALL);
    }

    @AfterEach
    void afterEach() throws Exception {
        BuildData.LOGGER.removeHandler(handler);
        BuildData.LOGGER.setUseParentHandlers(ORIGINAL_USE_PARENT_HANDLERS);
        BuildData.LOGGER.setLevel(ORIGINAL_LEVEL);
    }

    /* Confirm URISyntaxException is logged at FINEST on invalid URL */
    @Test
    void testSimilarToInvalidHttpsRemoteURL() {
        final String INVALID_URL = "https://github.com/jenkinsci/git-plugin?s=^IXIC";
        BuildData invalid = new BuildData();
        invalid.addRemoteUrl(INVALID_URL);
        assertTrue(invalid.similarTo(invalid), "Invalid URL not similar to itself " + INVALID_URL);

        String expectedMessage = "URI syntax exception on " + INVALID_URL;
        assertThat(handler.checkMessage(), is(expectedMessage));
        assertThat(handler.checkLevel(), is(Level.FINEST));
    }

    static class LogHandler extends Handler {

        private Level lastLevel = Level.INFO;
        private String lastMessage = "";

        public Level checkLevel() {
            return lastLevel;
        }

        public String checkMessage() {
            return lastMessage;
        }

        @Override
        public void publish(LogRecord record) {
            lastLevel = record.getLevel();
            lastMessage = record.getMessage();
        }

        @Override
        public void close() {
        }

        @Override
        public void flush() {
        }
    }
}
