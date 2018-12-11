package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class WipeWorkspaceTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(WipeWorkspace.class)
                .usingGetClass()
                .verify();
    }
}
