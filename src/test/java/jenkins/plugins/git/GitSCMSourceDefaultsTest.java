package jenkins.plugins.git;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class GitSCMSourceDefaultsTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(GitSCMSourceDefaults.class)
                .usingGetClass()
                .verify();
    }
}
