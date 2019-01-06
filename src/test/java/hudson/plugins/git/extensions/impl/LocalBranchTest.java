package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class LocalBranchTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(LocalBranch.class)
                .usingGetClass()
                .verify();
    }
}
