package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class CleanCheckoutTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(CleanCheckout.class)
                .usingGetClass()
                .verify();
    }
}
