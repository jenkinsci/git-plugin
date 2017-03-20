package hudson.plugins.git.util;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Mark Waite
 */
public class BuildDataLoggingTest {

    private BuildData data;

    private final Level ORIGINAL_LEVEL = BuildData.LOGGER.getLevel();
    private final boolean ORIGINAL_USE_PARENT_HANDLERS = BuildData.LOGGER.getUseParentHandlers();

    private LogHandler handler = null;

    @Before
    public void createTestData() throws Exception {
        data = new BuildData();
    }

    @Before
    public void reconfigureLogging() throws Exception {
        handler = new LogHandler();
        handler.setLevel(Level.ALL);
        BuildData.LOGGER.setUseParentHandlers(false);
        BuildData.LOGGER.addHandler(handler);
        BuildData.LOGGER.setLevel(Level.ALL);
    }

    @After
    public void restoreLogging() throws Exception {
        BuildData.LOGGER.removeHandler(handler);
        BuildData.LOGGER.setUseParentHandlers(ORIGINAL_USE_PARENT_HANDLERS);
        BuildData.LOGGER.setLevel(ORIGINAL_LEVEL);
    }

    /* Confirm URISyntaxException is logged at FINEST on invalid URL */
    @Test
    public void testSimilarToInvalidHttpsRemoteURL() {
        final String INVALID_URL = "https://github.com/jenkinsci/git-plugin?s=^IXIC";
        BuildData invalid = new BuildData();
        invalid.addRemoteUrl(INVALID_URL);
        assertTrue("Invalid URL not similar to itself " + INVALID_URL, invalid.similarTo(invalid));

        String expectedMessage = "URI syntax exception on " + INVALID_URL;
        assertThat(handler.checkMessage(), is(expectedMessage));
        assertThat(handler.checkLevel(), is(Level.FINEST));
    }

    class LogHandler extends Handler {

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
