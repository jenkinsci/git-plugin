package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AuthorInChangelogTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(AuthorInChangelog.class)
                .usingGetClass()
                .verify();
    }

    @Test
    void checkToString() {
        AuthorInChangelog setting = new AuthorInChangelog();
        assertThat(setting.toString(), is("AuthorInChangelog{}"));
    }

}
