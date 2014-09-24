package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Run;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitSCMCreateClientNullTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setSecurity() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
    }

    @Before
    public void configureGitTool() {
        GitTool.onLoaded();
    }

    @Test
    public void testGetClientAvoidNPEAfterSlaveDisconnected() throws Exception {
        Node node = j.createOnlineSlave();
        FreeStyleProject myProject = j.createFreeStyleProject();

        /* Force myProject to execute on the new slave */
        myProject.setAssignedLabel(j.jenkins.getLabel(node.getDisplayName()));

        /* Configure SCM for the project - use this repo as the remote */
        List<UserRemoteConfig> userRemoteConfigs = new ArrayList<UserRemoteConfig>();
        String repoURL = (new File(".git")).toURI().toURL().toString();
        String refspec = "+refs/heads/*:refs/remotes/origin/*";
        userRemoteConfigs.add(new UserRemoteConfig(repoURL, "origin", refspec, null));
        List<BranchSpec> branches = new ArrayList<BranchSpec>();
        branches.add(new BranchSpec("refs/tags/git-2.2.6"));
        Boolean doGenerateSubmoduleConfigurations = false;
        Collection<SubmoduleConfig> submoduleCfg = null;
        GitRepositoryBrowser browser = null;
        List<GitSCMExtension> extensions = null;
        GitSCM gitSCM = new GitSCM(
                userRemoteConfigs,
                branches,
                doGenerateSubmoduleConfigurations,
                submoduleCfg,
                browser,
                GitTool.DEFAULT,
                extensions
        );
        myProject.setScm(gitSCM);

        /* Build the project and assert it succeeded */
        j.buildAndAssertSuccess(myProject);
        assertNotNull(myProject.getFirstBuild().getWorkspace());

        /* Disconnect the online slave */
        node.toComputer().cliDisconnect("Disconnected the node to show NPE");
        FilePath ws = myProject.getFirstBuild().getWorkspace();
        assertNull(ws);

        /* Create a GitClient from the first build.  Failed with a null
         * pointer exception prior to git plugin 2.2.7 due to disconnected slave.
         */
        final Run myRun = myProject.getFirstBuild();
        EnvVars myEnv = new EnvVars();
        BuildListener myBuildListener = null;
        GitClient client = gitSCM.createClient(myBuildListener, myEnv, myRun, ws);
        assertNotNull(client);
    }
}
