package hudson.plugins.git.extensions.impl;

import org.junit.Test;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Basic unit tests for MessageExclusion
 */
public class MessageExclusionSimpleTest {

    MessageExclusion.DescriptorImpl descriptor = new MessageExclusion.DescriptorImpl();

    @Test
    public void testDoMatchMessage() throws Exception {
        
        assertMessageIsNotConsidered("TOKEN", false, true, "Commit Message with TOKEN"); // partial match of TOKEN
        assertMessageIsConsidered("TOKEN", false, false, "Commit Message with TOKEN"); // global (legacy match)
        assertMessageIsNotConsidered("TOKEN", true, false, "Commit Message with TOKEN"); // global, inverted match
        assertMessageIsConsidered(".*TOKEN.*", true, false, "Commit Message with TOKEN"); // global, inverted match
    }
    
    private void assertMessageIsConsidered(String pattern, boolean includeInsteadOfExclude, boolean partialMatch, String message) {
        assertTrue("Message should be considered by polling", descriptor.doMatchMessage(pattern, includeInsteadOfExclude, partialMatch, message).getMessage().contains("<b>would</b>"));
    }

    private void assertMessageIsNotConsidered(String pattern, boolean includeInsteadOfExclude, boolean partialMatch, String message) {
        assertTrue("Message should not be considered by polling", descriptor.doMatchMessage(pattern, includeInsteadOfExclude, partialMatch, message).getMessage().contains("<b>would not</b>"));
    }
}
