package hudson.plugins.git.extensions.impl;

import hudson.plugins.git.extensions.GitClientType;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import org.junit.Test;

public class CloneOptionTest {

    @Test
    public void getRequiredClient() {
        CloneOption cloneOption = new CloneOption(false, "", 1);
        assertThat(cloneOption.getRequiredClient(), is(GitClientType.GITCLI));
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(CloneOption.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
}
