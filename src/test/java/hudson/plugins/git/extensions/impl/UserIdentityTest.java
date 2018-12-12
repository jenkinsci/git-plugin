package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class UserIdentityTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(UserIdentity.class)
                .usingGetClass()
                .verify();
    }
}
