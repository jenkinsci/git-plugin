package hudson.plugins.git;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.plugins.git.util.DefaultBuildChooser;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import org.jvnet.hudson.test.CaptureEnvironmentBuilder;

public class GitRefParameterTest extends AbstractGitTestCase {

    public void testParameterInRefspec() throws Exception {
        // Setup Git repository test fixture, featuring two non-identical branches
        final String BRANCH = "research";
        final String PARAM_NAME = "GIT_REF";

        testRepo.commit("ipomoea-triloba", johnDoe, "Adding Ipomoea Triloba to the catalog.");
        testRepo.git.branch(BRANCH);
        testRepo.git.checkout(BRANCH);
        testRepo.commit("Cordyline-fruticosa", johnDoe, "Identified Cordyline fruticosa.");
        testRepo.git.checkout("master");
        testRepo.commit("Bauhinia blakeana", johnDoe, "Cataloging Bauhinia blakeana. Oh ya!");

        final String showRefOutput = testRepo.git.launchCommand("show-ref");
        final Map<String, String> refmap = new HashMap<String, String>();
        final BufferedReader rdr = new BufferedReader(new StringReader(showRefOutput));
        String line;
        while ((line = rdr.readLine()) != null) {
            final Matcher matcher = GitStandaloneAPI.SHA1_REF_ENTRY.matcher(line);
            if (matcher.matches()) {
                refmap.put(matcher.group(2), matcher.group(1));
            }
        }
        assertEquals(2, refmap.size()); // One for each of the master and the branch

        final String repositoryURL = testRepo.remoteConfigs().get(0).getUrl();
        final String refspec = "+${" + PARAM_NAME + "}:refs/remotes/origin/master";
        UserRemoteConfig plain = testRepo.remoteConfigs().get(0);
        List<UserRemoteConfig> userRemoteConfigs = new ArrayList<UserRemoteConfig>();
        userRemoteConfigs.add(new UserRemoteConfig(plain.getUrl(), plain.getName(), refspec));

        // Setup the Jenkins project, which uses the Git repository test fixture with a parameterized refspec
        final FreeStyleProject project = createFreeStyleProject();
        project.setScm(new GitSCM(
                null,
                userRemoteConfigs,
                Collections.singletonList(new BranchSpec("")),
                null,
                false, Collections.<SubmoduleConfig>emptyList(), false,
                false, new DefaultBuildChooser(), null, null, false, null,
                null,
                null, null, null, false, false, false, false, null, null, false, null, false));
        project.addProperty(new ParametersDefinitionProperty(
                new GitRefParameterDefinition(PARAM_NAME, repositoryURL, false)
                ));
        project.getBuildersList().add(new CaptureEnvironmentBuilder());

        // Build project, setting the build parameter to BRANCH
        FreeStyleBuild build = project.scheduleBuild2(0,
                new Cause.UserCause(),
                new ParametersAction(new GitRefParameterValue(PARAM_NAME, repositoryURL, "refs/heads/" + BRANCH))
                ).get();

        // Confirm that the work directory / cloned Git repository HEAD SHA-1 is identical to the selected branch
        final String expectedHash = refmap.get("refs/heads/" + BRANCH);
        assertNotNull(expectedHash);
        final GitAPI workspaceGit = new GitAPI("git", build.getWorkspace(), listener, new EnvVars());
        final String actualHash = workspaceGit.launchCommand("log", "--pretty=format:%H", "-n", "1");
        assertEquals(expectedHash, actualHash.trim());
    }

    public void testDisplayInReverseOrder() throws Exception {
        // Create a Git repository
        testRepo.commit("teensy", johnDoe, "Imperceptible changeset.");

        // Lengthen the list of refs until we can meaningfully test ordering
        testRepo.tag("earlier");
        testRepo.tag("later");

        // Obtain the list in its "natural" ordering
        final GitRefParameterDefinition pn = new GitRefParameterDefinition("ignore", testRepo.gitDir.getAbsolutePath(), false);
        final List<String> natural = pn.getRefs();

        // Obtain the list in its reverse ordering
        final GitRefParameterDefinition pr = new GitRefParameterDefinition("ignore", testRepo.gitDir.getAbsolutePath(), true);
        final List<String> reversed = pr.getRefs();

        // Assert that the orderings are the reverse of each other
        Collections.reverse(reversed);
        assertEquals(natural, reversed);
    }

}
