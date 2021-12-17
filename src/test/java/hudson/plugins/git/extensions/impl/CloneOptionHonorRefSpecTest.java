package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;

import java.io.File;
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

    private static final Random random = new Random();
    private FreeStyleProject project;
    private String refSpecExpectedValue;

    public CloneOptionHonorRefSpecTest(String refSpecName) {
        this.refSpecName = refSpecName;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteRefSpecVariable() {
        List<Object[]> values = new ArrayList<>();

        String[] keys = {
                "JOB_NAME", // Variable set by Jenkins
                (isWindows() ? "USERNAME" : "USER"), // Variable set by the operating system
                "USER_SELECTED_BRANCH_NAME" // Parametrised build param
        };

        for (String refSpecName : keys) {
            Object[] combination = {refSpecName};
            values.add(combination);
        }

        return values;
    }

    private static Builder createEnvEchoBuilder(String envVarName) {
        if (isWindows()) {
            return new BatchFile(String.format("echo %s=%%%s%%", envVarName, envVarName));
        }
        return new Shell(String.format("echo \"%s=${%s}\"", envVarName, envVarName));
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Setup job beforehand to get expected value of the environment variable
        project = createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("USER_SELECTED_BRANCH_NAME", "user_branch")
        ));
        project.getBuildersList().add(createEnvEchoBuilder(refSpecName));

        final FreeStyleBuild b = rule.buildAndAssertSuccess(project);

        List<String> logs = b.getLog(50);
        for (String line : logs) {
            if (line.startsWith(refSpecName + '=')) {
                refSpecExpectedValue = line.substring(refSpecName.length() + 1, line.length());
            }
        }

        if (refSpecExpectedValue == null) {
            throw new Exception("Could not obtain env var expected value");
        }
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
        if (refSpecExpectedValue == null || refSpecExpectedValue.isEmpty()) {
            /* Test does not support an empty or null expected value.
               Skip the test if the expected value is empty or null */
            System.out.println("*** testRefSpecWithExpandedVariables empty expected value for '" + refSpecName + "' ***");
            return;
        }
        // Create initial commit
        final String commitFile1 = "commitFile1";
        commit(commitFile1, johnDoe, "Commit in master branch");

        // Create branch and make initial commit
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
        GitSCM scm = new GitSCM(
                repos,
                Collections.singletonList(new BranchSpec(branchName)),
                false, Collections.emptyList(),
                null, random.nextBoolean() ? JGitTool.MAGIC_EXENAME : null,
                Collections.emptyList());
        project.setScm(scm);

        // Same result expected whether refspec honored or not
        CloneOption cloneOption = new CloneOption(false, null, null);
        cloneOption.setHonorRefspec(true);
        scm.getExtensions().add(cloneOption);

        FreeStyleBuild b = build(project, Result.SUCCESS, commitFile1);

        // Check that unexpanded refspec name is not in the log
        List<String> buildLog = b.getLog(50);
        assertThat(buildLog, not(hasItem(containsString("${" + refSpecName + "}"))));
    }

    private static boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
