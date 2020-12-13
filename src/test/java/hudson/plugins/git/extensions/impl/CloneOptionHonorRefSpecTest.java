package hudson.plugins.git.extensions.impl;

import hudson.Functions;
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
    private final Boolean honorRefSpec;

    private static final Random random = new Random();
    private FreeStyleProject project;
    private String refSpecExpectedValue;

    public CloneOptionHonorRefSpecTest(String refSpecName, Boolean honorRefSpec) {
        this.refSpecName = refSpecName;
        this.honorRefSpec = honorRefSpec;
    }

    @Parameterized.Parameters(name = "{0}-{1}")
    public static Collection permuteRefSpecVariable() {
        List<Object[]> values = new ArrayList<>();
        /* Should behave same with honor refspec enabled or disabled */
        boolean honorRefSpec = random.nextBoolean();

        /* Variables set by Jenkins */
        String[] allowed = {"JOB_BASE_NAME", "JOB_NAME"};
        for (String refSpecName : allowed) {
            Object[] combination = {refSpecName, honorRefSpec};
            values.add(combination);
            honorRefSpec = !honorRefSpec;
        }

        /* Variable set by the operating system */
        String refSpecName = Functions.isWindows() ? "USERNAME" : "USER";
        Object[] combination = {refSpecName, !honorRefSpec};
        values.add(combination);

        /* Parametrised build */
        refSpecName = "USER_SELECTED_BRANCH_NAME";
        combination = new Object[]{refSpecName, !honorRefSpec};
        values.add(combination);

        return values;
    }

    private static Builder createEnvEchoBuilder(String envVarName) {
        if (Functions.isWindows()) {
            return new BatchFile(String.format("echo %s=%%%s%%", envVarName, envVarName));
        }
        return new Shell(String.format("echo \"%s=${%s}\"", envVarName, envVarName));
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Setup job beforehand to get expected value of the environment variable
        project = createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("USER_SELECTED_BRANCH_NAME", "user_branch")
        ));
        project.getBuildersList().add(createEnvEchoBuilder(refSpecName));

        final FreeStyleBuild b = project.scheduleBuild2(0).get();
        rule.assertBuildStatus(Result.SUCCESS, b);

        List<String> logs = b.getLog(50);
        for (String line : logs) {
            if (line.startsWith(refSpecName + '=')) {
                refSpecExpectedValue = line.split("=")[1];
            }
        }

        if (refSpecExpectedValue == null) {
            throw new Exception("Could not obtain ENV_VAR expected value");
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
        GitSCM scm = new GitSCM(
                repos,
                Collections.singletonList(new BranchSpec(branchName)),
                false, Collections.emptyList(),
                null, random.nextBoolean() ? JGitTool.MAGIC_EXENAME : null,
                Collections.emptyList());
        project.setScm(scm);
        project.save();

        /* Same result expected whether refspec honored or not */
        CloneOption cloneOption = new CloneOption(false, null, null);
        cloneOption.setHonorRefspec(honorRefSpec);
        ((GitSCM) project.getScm()).getExtensions().add(cloneOption);

        FreeStyleBuild b = build(project, Result.SUCCESS, commitFile1);

        /* Check that unexpanded refspec name is not in the log */
        List<String> buildLog = b.getLog(50);
        assertThat(buildLog, not(hasItem(containsString("${" + refSpecName + "}"))));
    }
}
