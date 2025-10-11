package jenkins.plugins.git;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

class GitRemoteHeadRefActionTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(GitRemoteHeadRefAction.class)
                .usingGetClass()
                .verify();
    }
}
