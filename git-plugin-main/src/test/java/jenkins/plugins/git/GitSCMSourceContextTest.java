package jenkins.plugins.git;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class GitSCMSourceContextTest {

    @Test
    public void equalsContract_RefNameMapping() {
        EqualsVerifier.forClass(GitSCMSourceContext.RefNameMapping.class)
                .usingGetClass()
                .verify();
    }
}
