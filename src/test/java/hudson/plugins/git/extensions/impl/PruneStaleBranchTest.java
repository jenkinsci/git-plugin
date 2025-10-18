package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class PruneStaleBranchTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(PruneStaleBranch.class)
                .usingGetClass()
                .verify();
    }
}
