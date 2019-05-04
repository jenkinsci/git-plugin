package hudson.plugins.git.extensions.impl;

import static org.eclipse.jgit.lib.ObjectId.fromString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import hudson.EnvVars;
import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.UserMergeOptions.CommitMessageStyle;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.util.DescribableList;

public class PreBuildMergeMessageTest {

    private final FreeStyleBuild build = mock(FreeStyleBuild.class);

    private final GitSCM gitSCM = mock(GitSCM.class);

    private final GitClient git = mock(GitClient.class);

    private final TaskListener listener = mock(TaskListener.class);

    private final Revision marked = mock(Revision.class);

    private final MergeCommand mergeCommand = mock(MergeCommand.class);

    @Before
    public void setup() throws InterruptedException, IOException {
        given(build.getEnvironment(listener)).willReturn(new EnvVars());

        PrintStream logger = mock(PrintStream.class);
        given(listener.getLogger()).willReturn(logger);

        given(git.revParse(Mockito.anyString())).willReturn(fromString("11ec153f34767f7638378735dc2b907ed251a67d"));

        CheckoutCommand checkoutCommand = mock(CheckoutCommand.class);
        given(git.checkout()).willReturn(checkoutCommand);
        given(checkoutCommand.branch(Mockito.anyString())).willReturn(checkoutCommand);
        given(checkoutCommand.ref(Mockito.anyString())).willReturn(checkoutCommand);
        given(checkoutCommand.deleteBranchIfExist(Mockito.anyBoolean())).willReturn(checkoutCommand);

        DescribableList<GitSCMExtension, GitSCMExtensionDescriptor> extensions = new DescribableList<>(Saveable.NOOP,
                Util.fixNull(new ArrayList<GitSCMExtension>()));
        given(gitSCM.getExtensions()).willReturn(extensions);

        given(git.merge()).willReturn(mergeCommand);
        given(mergeCommand.setRevisionToMerge(Mockito.any(ObjectId.class))).willReturn(mergeCommand);
        given(mergeCommand.setMessage(Mockito.anyString())).willReturn(mergeCommand);
    }

    private void decorateRevisionToBuild(String initialBranch, String mergeToBranch)
            throws InterruptedException, IOException {
        Branch branch = new Branch(initialBranch, fromString("2cec153f34767f7638378735dc2b907ed251a67d"));
        Revision rev = new Revision(branch.getSHA1(), Arrays.asList(branch));

        UserMergeOptions userMergeOptions = new UserMergeOptions(mergeToBranch);
        userMergeOptions.setCommitMessageStyle(CommitMessageStyle.GITLAB);
        PreBuildMerge preBuildMerge = new PreBuildMerge(userMergeOptions);
        preBuildMerge.decorateRevisionToBuild(gitSCM, build, git, listener, marked, rev);
    }

    @Test
    public void branchNameSingleForwardSlash() throws InterruptedException, IOException {
        decorateRevisionToBuild("origin/stable-3.9", "origin/stable-3.10");

        verify(mergeCommand).setMessage("Merge branch 'stable-3.9' into 'stable-3.10'");
    }

    @Test
    public void branchNameMultipleForwardSlashes() throws InterruptedException, IOException {
        decorateRevisionToBuild("refs/remotes/origin/stable-3.9", "refs/remotes/origin/stable-3.10");

        verify(mergeCommand).setMessage("Merge branch 'stable-3.9' into 'stable-3.10'");
    }

    @Test
    public void branchNameNoForwardSlashes() throws InterruptedException, IOException {
        decorateRevisionToBuild("stable-3.9", "stable-3.10");

        verify(mergeCommand).setMessage("Merge branch 'stable-3.9' into 'stable-3.10'");
    }

    @Test
    public void emptyRevisionBranches() throws InterruptedException, IOException {
        Revision rev = new Revision(fromString("2cec153f34767f7638378735dc2b907ed251a67d"));

        UserMergeOptions userMergeOptions = new UserMergeOptions("123");
        userMergeOptions.setCommitMessageStyle(CommitMessageStyle.GITLAB);
        PreBuildMerge preBuildMerge = new PreBuildMerge(userMergeOptions);
        preBuildMerge.decorateRevisionToBuild(gitSCM, build, git, listener, marked, rev);

        verify(mergeCommand).setMessage("Merge branch 'null' into '123'");
    }
    
    @Test
    public void noneCommitMessageStyle() throws InterruptedException, IOException {
        Revision rev = new Revision(fromString("2cec153f34767f7638378735dc2b907ed251a67d"));

        UserMergeOptions userMergeOptions = new UserMergeOptions("123");
        PreBuildMerge preBuildMerge = new PreBuildMerge(userMergeOptions);
        preBuildMerge.decorateRevisionToBuild(gitSCM, build, git, listener, marked, rev);

        verify(mergeCommand).setMessage(null);
    }

}
