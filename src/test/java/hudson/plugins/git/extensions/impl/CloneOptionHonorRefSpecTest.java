package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jvnet.hudson.test.Issue;
import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@RunWith(Parameterized.class)
public class CloneOptionHonorRefSpecTest extends AbstractGitTestCase {

    private String refSpecVar;

    public CloneOptionHonorRefSpecTest(String useRefSpecVariable) {
        this.refSpecVar = useRefSpecVariable;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteRefSpecVariable() {
        List<Object[]> values = new ArrayList<>();
        String[] allowed = {"${GIT_REVISION}", "${GIT_COMMIT}"};
        for (String refSpecValue : allowed) {
            Object[] combination = {refSpecValue};
            values.add(combination);
        }
        return values;
    }

    /**
     * This test confirms behavior of refspecs on initial clone with expanded variables.
     * @throws Exception on error
     */
    @Test
    @Issue("JENKINS-56063")
    public void testRefSpecWithExpandedVariables() throws Exception {
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(testRepo.gitDir.getAbsolutePath(), "origin", "+refs/heads/master:refs/remotes/origin/master" + refSpecVar, null));

        /* Set CloneOption to honor refspec on initial clone with expanded var */
        FreeStyleProject projectWithMasterExpanded = setupProject(repos, Collections.singletonList(new BranchSpec("master" + refSpecVar)), null, false, null);
        CloneOption cloneOptionMasterExpanded = new CloneOption(false, null, null);
        cloneOptionMasterExpanded.setHonorRefspec(true);
        ((GitSCM)projectWithMasterExpanded.getScm()).getExtensions().add(cloneOptionMasterExpanded);

        /* Set CloneOption to honor refspec on initial clone without expanded var, should fail */
        FreeStyleProject projectWithMaster = setupProject(repos, Collections.singletonList(new BranchSpec("master")), null, false, null);
        CloneOption cloneOptionMaster = new CloneOption(false, null, null);
        cloneOptionMaster.setHonorRefspec(true);
        ((GitSCM)projectWithMaster.getScm()).getExtensions().add(cloneOptionMaster);

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master");
        // create branch and make initial commit
        git.checkout().ref("master").branch("foo").execute();
        commit(commitFile1, johnDoe, "Commit in foo");

        build(projectWithMaster, Result.FAILURE);
        build(projectWithMasterExpanded, Result.SUCCESS, commitFile1);
    }
}
