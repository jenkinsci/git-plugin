package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class AuthorInChangelogTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(AuthorInChangelog.class)
                .usingGetClass()
                .verify();
    }
}
