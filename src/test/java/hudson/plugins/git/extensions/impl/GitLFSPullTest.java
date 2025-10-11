package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class GitLFSPullTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(GitLFSPull.class)
                .usingGetClass()
                .verify();
    }
}
