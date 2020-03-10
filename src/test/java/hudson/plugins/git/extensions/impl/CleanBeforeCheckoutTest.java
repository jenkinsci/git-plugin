package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class CleanBeforeCheckoutTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(CleanBeforeCheckout.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
}
