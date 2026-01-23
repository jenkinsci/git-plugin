package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class SparseCheckoutPathTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(SparseCheckoutPath.class)
                .usingGetClass()
                .verify();
    }
}
