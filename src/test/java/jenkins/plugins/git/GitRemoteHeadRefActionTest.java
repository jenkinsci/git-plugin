package jenkins.plugins.git;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class GitRemoteHeadRefActionTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(GitRemoteHeadRefAction.class)
                .usingGetClass()
                .verify();
    }
}
