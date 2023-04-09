package hudson.plugins.git.extensions.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class AuthorInChangelogTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(AuthorInChangelog.class).usingGetClass().verify();
    }

    @Test
    public void checkToString() {
        AuthorInChangelog setting = new AuthorInChangelog();
        assertThat(setting.toString(), is("AuthorInChangelog{}"));
    }
}
