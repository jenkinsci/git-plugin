package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class IgnoreNotifyCommitTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(IgnoreNotifyCommit.class)
                .usingGetClass()
                .verify();
    }
}
