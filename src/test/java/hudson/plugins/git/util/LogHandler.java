package hudson.plugins.git.util;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Recording log handler to allow assertions on logging. Not intended for use
 * outside this package. Not intended for use outside tests.
 *
 * @author <a href="mailto:mark.earl.waite@gmail.com">Mark Waite</a>
 */
public class LogHandler extends Handler {

    private List<String> messages = new ArrayList<>();

    @Override
    public void publish(LogRecord lr) {
        messages.add(lr.getMessage());
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() throws SecurityException {
        messages = new ArrayList<>();
    }

    /* package */ List<String> getMessages() {
        return messages;
    }

    /* package */ boolean containsMessageSubstring(String messageSubstring) {
        for (String message : messages) {
            if (message.contains(messageSubstring)) {
                return true;
            }
        }
        return false;
    }
}
