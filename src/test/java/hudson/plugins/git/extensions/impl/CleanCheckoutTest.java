package hudson.plugins.git.extensions.impl;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CleanCheckoutTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(CleanCheckout.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }

    @Test
    void checkToStringFalse() {
        CleanCheckout setting = new CleanCheckout();
        assertThat(setting.toString(), is("CleanCheckout{deleteUntrackedNestedRepositories=false}"));
    }

    @Test
    void checkToStringTrue() {
        CleanCheckout setting = new CleanCheckout();
        setting.setDeleteUntrackedNestedRepositories(true);
        assertThat(setting.toString(), is("CleanCheckout{deleteUntrackedNestedRepositories=true}"));
        setting.setDeleteUntrackedNestedRepositories(false);
        assertThat(setting.toString(), is("CleanCheckout{deleteUntrackedNestedRepositories=false}"));
    }
}
