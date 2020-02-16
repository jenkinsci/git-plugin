package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class GitLFSPullTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(GitLFSPull.class)
                .usingGetClass()
                .verify();
    }
}
