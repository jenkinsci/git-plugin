package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class PruneStaleBranchTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(PruneStaleBranch.class)
                .usingGetClass()
                .verify();
    }
}
