package jenkins.plugins.git;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class GitSCMSourceContextTest {

    @Test
    void equalsContract_RefNameMapping() {
        EqualsVerifier.forClass(GitSCMSourceContext.RefNameMapping.class)
                .usingGetClass()
                .verify();
    }
}
