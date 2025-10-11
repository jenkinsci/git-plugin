package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class IgnoreNotifyCommitTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(IgnoreNotifyCommit.class)
                .usingGetClass()
                .verify();
    }
}
