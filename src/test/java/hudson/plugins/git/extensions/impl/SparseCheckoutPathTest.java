package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class SparseCheckoutPathTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(SparseCheckoutPath.class)
                .usingGetClass()
                .verify();
    }
}
