package jenkins.plugins.git;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class GitSCMSourceDefaultsTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(GitSCMSourceDefaults.class)
                .usingGetClass()
                .verify();
    }
}
