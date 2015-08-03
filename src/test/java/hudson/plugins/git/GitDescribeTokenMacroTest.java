package hudson.plugins.git;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class GitDescribeTokenMacroTest {

    @Test
    public void acceptsMacroName() throws Exception {
        GitDescribeTokenMacro gitDescribeTokenMacro = new GitDescribeTokenMacro();

        assertFalse(gitDescribeTokenMacro.acceptsMacroName("dupa"));
        assertFalse(gitDescribeTokenMacro.acceptsMacroName(""));
        assertFalse(gitDescribeTokenMacro.acceptsMacroName(null));

        assertTrue(gitDescribeTokenMacro.acceptsMacroName("GIT_DESCRIBE"));
    }

    @Test
    public void evaluate() throws Exception {
        GitDescribeTokenMacro gitDescribeTokenMacro = new GitDescribeTokenMacro();

        GitSCM gitSCM = mock(GitSCM.class);
        when(gitSCM.readGitDescribe(any(AbstractBuild.class), any(TaskListener.class))).thenReturn("2.4.1-12-gabcdef01");

        AbstractBuild build = mock(AbstractBuild.class, RETURNS_DEEP_STUBS);
        when(build.getProject().getScm()).thenReturn(gitSCM);

        assertEquals("2.4.1-12-gabcdef01", gitDescribeTokenMacro.evaluate(build, null, null));

    }

    @Test
    public void evaluate_notAGitSCM() throws Exception {
        GitDescribeTokenMacro gitDescribeTokenMacro = new GitDescribeTokenMacro();

        AbstractBuild build = mock(AbstractBuild.class, RETURNS_DEEP_STUBS);
        when(build.getProject().getScm()).thenReturn(mock(SCM.class));

        assertThat(gitDescribeTokenMacro.evaluate(build, null, null), CoreMatchers.startsWith("error:"));
    }
}