package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class LocalBranchTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(LocalBranch.class)
                .usingGetClass()
                .verify();
    }

    @Test
    void checkToString() {
        LocalBranch setting = new LocalBranch("branch-name");
        assertThat(setting.toString(), is("LocalBranch{localBranch='branch-name'}"));
    }

    @Test
    void checkToStringNull() {
        LocalBranch setting = new LocalBranch(null);
        assertThat(setting.toString(), is("LocalBranch{same-as-remote}"));
    }

    @Test
    void checkToStringWildcards() {
        LocalBranch setting = new LocalBranch("**");
        assertThat(setting.toString(), is("LocalBranch{same-as-remote}"));
    }
}
