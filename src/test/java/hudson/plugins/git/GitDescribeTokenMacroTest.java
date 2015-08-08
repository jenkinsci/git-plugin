package hudson.plugins.git;

import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.io.IOException;

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
    public void evaluate_defaultParameters() throws Exception {
        GitDescribeTokenMacro tokenMacro = new GitDescribeTokenMacro();

        assertEquals("2.4.1-12-gabcdef01", tokenMacro.evaluate(mockBuild("2.4.1-12-gabcdef01"), null, null));
    }

    @Test
    public void evaluate_withParameters() throws Exception {
        assertEquals("2.4.1", new GitDescribeTokenMacro(true, false, false).evaluate(mockBuild("2.4.1-12-gabcdef01"), null, null));
        assertEquals("2.4.1-12", new GitDescribeTokenMacro(true, false, false).evaluate(mockBuild("2.4.1-12"), null, null));
        assertEquals("2.4.1", new GitDescribeTokenMacro(true, false, false).evaluate(mockBuild("2.4.1"), null, null));
        assertEquals("tag-111-gaaa", new GitDescribeTokenMacro(true, false, false).evaluate(mockBuild("tag-111-gaaa-12-gabcdef01"), null, null));

        assertEquals("12-gabcdef01", new GitDescribeTokenMacro(false, true, true).evaluate(mockBuild("2.4.1-12-gabcdef01"), null, null));
        assertEquals("12-gabcdef01", new GitDescribeTokenMacro(false, true, true).evaluate(mockBuild("tag-111-gaaa-12-gabcdef01"), null, null));
        assertEquals("", new GitDescribeTokenMacro(false, true, true).evaluate(mockBuild("2.4.1"), null, null));

        assertEquals("abcdef01", new GitDescribeTokenMacro(false, false, true).evaluate(mockBuild("2.4.1-12-gabcdef01"), null, null));
        assertEquals("abcdef01", new GitDescribeTokenMacro(false, false, true).evaluate(mockBuild("tag-111-gaaa-12-gabcdef01"), null, null));
        assertEquals("", new GitDescribeTokenMacro(false, false, true).evaluate(mockBuild("2.4.1"), null, null));

        assertEquals("2.4.1-gabcdef01", new GitDescribeTokenMacro(true, false, true).evaluate(mockBuild("2.4.1-12-gabcdef01"), null, null));
        assertEquals("2.4.1-12", new GitDescribeTokenMacro(true, true, false).evaluate(mockBuild("2.4.1-12-gabcdef01"), null, null));
        assertEquals("2.4.1", new GitDescribeTokenMacro(true, false, true).evaluate(mockBuild("2.4.1"), null, null));
    }

    @Test
    public void evaluate_notAGitSCM() throws Exception {
        GitDescribeTokenMacro gitDescribeTokenMacro = new GitDescribeTokenMacro();

        AbstractBuild build = mock(AbstractBuild.class, RETURNS_DEEP_STUBS);
        when(build.getProject().getScm()).thenReturn(mock(SCM.class));

        assertThat(gitDescribeTokenMacro.evaluate(build, null, null), CoreMatchers.startsWith("error:"));
    }

    private AbstractBuild mockBuild(String gitDescribe) throws IOException, InterruptedException {
        GitSCM gitSCM = mock(GitSCM.class);
        when(gitSCM.readGitDescribe(any(AbstractBuild.class), any(TaskListener.class))).thenReturn(gitDescribe);

        AbstractBuild build = mock(AbstractBuild.class, RETURNS_DEEP_STUBS);
        when(build.getProject().getScm()).thenReturn(gitSCM);
        return build;
    }
}