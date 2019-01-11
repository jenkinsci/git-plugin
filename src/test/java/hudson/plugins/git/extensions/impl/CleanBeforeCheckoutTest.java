package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class CleanBeforeCheckoutTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(CleanBeforeCheckout.class)
                .usingGetClass()
                .verify();
    }
}
