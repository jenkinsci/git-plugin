package hudson.plugins.git.extensions.impl;

import hudson.plugins.git.extensions.GitClientType;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

class CloneOptionTest {

    @Test
    void getRequiredClient() {
        CloneOption cloneOption = new CloneOption(false, "", 1);
        assertThat(cloneOption.getRequiredClient(), is(GitClientType.GITCLI));
    }

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(CloneOption.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
}
