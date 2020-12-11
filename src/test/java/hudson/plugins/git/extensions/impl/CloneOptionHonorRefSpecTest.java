package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

@RunWith(Parameterized.class)
public class CloneOptionHonorRefSpecTest extends AbstractGitTestCase {

    private final String refSpecName;
    private final String refSpecExpectedValue;
    private final Boolean honorRefSpec;

    private static final Random random = new Random();

    public CloneOptionHonorRefSpecTest(String refSpecName, String refSpecExpectedValue, Boolean honorRefSpec) {
        this.refSpecName = refSpecName;
        this.refSpecExpectedValue = refSpecExpectedValue;
        this.honorRefSpec = honorRefSpec;
    }

    private static String getExpectedValue(String reference) {
        if (reference.startsWith("JOB_")) {
            return "test0";
        }
        if (reference.contains("USER")) {
            return System.getProperty("user.name", "java-user.name-property-not-found");
        }
        return "not-master"; // fake value for other variables
    }

    @Parameterized.Parameters(name = "{0}-{1}-{2}")
    public static Collection permuteRefSpecVariable() {
        List<Object[]> values = new ArrayList<>();
        /* Should behave same with honor refspec enabled or disabled */
        boolean honorRefSpec = random.nextBoolean();

        /* Variables set by Jenkins */
        String[] allowed = {"JOB_BASE_NAME", "JOB_NAME", "JENKINS_USERNAME"};
        for (String refSpecName : allowed) {
            String refSpecExpectedValue = getExpectedValue(refSpecName);
            Object[] combination = {refSpecName, refSpecExpectedValue, honorRefSpec};
            values.add(combination);
            honorRefSpec = !honorRefSpec;
        }

        /* Variable set by the operating system */
        String refSpecName = isWindows() ? "USERNAME" : "USER";
        String refSpecExpectedValue = getExpectedValue(refSpecName);
        Object[] combination = {refSpecName, refSpecExpectedValue, honorRefSpec};
        values.add(combination);

        return values;
    }

    /**
     * This test confirms behavior of refspecs on initial clone with expanded
     * variables. When an environment variable reference is embedded in the
     * refspec, it should be expanded in all cases.
     *
     * @throws Exception on error
     */
    @Test
    @Issue("JENKINS-56063")
    public void testRefSpecWithExpandedVariables() throws Exception {

        // create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master branch");

        // create branch and make initial commit
        git.checkout().ref("master").branch(refSpecExpectedValue).execute();
        commit(commitFile1, johnDoe, "Commit in '" + refSpecExpectedValue + "' branch");

        // Use the variable reference in the refspec
        // Should be expanded by the clone option whether or not honor refspec is enabled
        List<UserRemoteConfig> repos = new ArrayList<>();
        repos.add(new UserRemoteConfig(
                testRepo.gitDir.getAbsolutePath(),
                "origin",
                "+refs/heads/${" + refSpecName + "}:refs/remotes/origin/${" + refSpecName + "}", null));

        /* Use the variable or its value as the branch name.
         * Same result expected in either case.
         */
        String branchName = random.nextBoolean() ? "${" + refSpecName + "}" : refSpecExpectedValue;
        FreeStyleProject project = setupProject(repos, Collections.singletonList(new BranchSpec(branchName)), null, false, null);

        /* Same result expected whether refspec honored or not */
        CloneOption cloneOption = new CloneOption(false, null, null);
        cloneOption.setHonorRefspec(honorRefSpec);
        ((GitSCM) project.getScm()).getExtensions().add(cloneOption);

        FreeStyleBuild b = build(project, Result.SUCCESS, commitFile1);
        /* Check that unexpanded refspec name is not in the log */
        List<String> buildLog = b.getLog(50);
        assertThat(buildLog, not(hasItem(containsString("${" + refSpecName + "}"))));
    }

    private static boolean isWindows() {
        return false;
    }
}
