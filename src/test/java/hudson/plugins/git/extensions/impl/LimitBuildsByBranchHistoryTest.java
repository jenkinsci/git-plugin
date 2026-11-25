package hudson.plugins.git.extensions.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.InverseBuildChooser;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.junit.jupiter.WithGitSampleRepo;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@WithGitSampleRepo
@Issue("JENKINS-19022")
class LimitBuildsByBranchHistoryTest {

    private static final int BRANCHES = 20;

    @Test
    void defaultKeepsEveryBranchEverBuilt(JenkinsRule r, GitSampleRepoRule repo) throws Exception {
        List<FreeStyleBuild> builds = buildEachBranchOnce(r, repo, null);
        for (int i = 0; i < builds.size(); i++) {
            assertThat(historySize(builds.get(i)), is(i + 1));
        }
    }

    @Test
    void historyStaysAtTheBuiltBranch(JenkinsRule r, GitSampleRepoRule repo) throws Exception {
        List<FreeStyleBuild> builds = buildEachBranchOnce(r, repo, new LimitBuildsByBranchHistory());
        for (int i = 0; i < builds.size(); i++) {
            assertThat(builds.get(i).getAction(BuildData.class).getBuildsByBranchName().keySet(),
                    contains(containsString("origin/feature-" + (i + 1))));
        }
        long first = buildXmlBytes(builds.get(0));
        long last = buildXmlBytes(builds.get(builds.size() - 1));
        assertThat("build.xml must not grow with the number of branches built", last, lessThan(first + 1024));
        r.assertLogContains("Removed 1 builds-by-branch entries", builds.get(1));
    }

    @Test
    void wildcardJobStillKnowsWhatItBuilt(JenkinsRule r, GitSampleRepoRule repo) throws Exception {
        createBranches(repo, 3);
        repo.git("checkout", "-q", "-b", "other", "master");
        repo.write("other", "other");
        repo.git("add", "other");
        repo.git("commit", "--message=other");
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(scm(repo, "*/feature-*", new LimitBuildsByBranchHistory()));
        FreeStyleBuild last = null;
        for (int i = 0; i < 3; i++) {
            last = r.buildAndAssertSuccess(p);
        }
        assertThat(last.getAction(BuildData.class).getBuildsByBranchName().keySet(),
                containsInAnyOrder("origin/feature-1", "origin/feature-2", "origin/feature-3"));
        assertThat("every matching branch was built, polling must not ask for another build",
                p.poll(StreamTaskListener.fromStdout()).hasChanges(), is(false));
    }

    /**
     * Documented cost: with a parameterized branch specifier, polling uses the default value,
     * whose history was dropped by the build of another branch.
     */
    @Test
    void parameterizedSpecPollsDefaultBranchAgainAfterAnotherBranch(JenkinsRule r, GitSampleRepoRule repo) throws Exception {
        createBranches(repo, 1);
        assertThat(pollAfterMasterThenFeature(r, repo, null), is(false));
        assertThat(pollAfterMasterThenFeature(r, repo, new LimitBuildsByBranchHistory()), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"master", "*/master", "origin/master", "remotes/origin/master",
        "refs/remotes/origin/master", "refs/heads/master", ":origin/mas.*", "${MAIN}", "*/${MAIN}"})
    void everySpecFormKeepsItsOwnBranch(String spec) {
        for (String key : List.of("origin/master", "refs/remotes/origin/master")) {
            BuildData data = buildData(key, "origin/other");
            int removed = new LimitBuildsByBranchHistory().prune(scmFor(spec), data, new EnvVars("MAIN", "master"), TaskListener.NULL);
            assertThat(spec + " with key " + key, data.getBuildsByBranchName().keySet(), contains(key));
            assertThat(removed, is(1));
        }
    }

    @Test
    void slashInBranchNameKeepsItsOwnBranch() {
        BuildData data = buildData("refs/remotes/origin/feature/x", "origin/feature/y");
        new LimitBuildsByBranchHistory().prune(scmFor("feature/x"), data, new EnvVars(), TaskListener.NULL);
        assertThat(data.getBuildsByBranchName().keySet(), contains("refs/remotes/origin/feature/x"));
    }

    @Test
    void pruneKeepsBranchesMatchingAnySpec() {
        BuildData data = buildData("origin/feature-1", "origin/feature-2", "origin/other", "origin/master");
        GitSCM scm = new GitSCM(GitSCM.createRepoList("https://example.com/repo.git", null),
                List.of(new BranchSpec("*/feature-*"), new BranchSpec("${MAIN}")), null, null, List.of());
        int removed = new LimitBuildsByBranchHistory().prune(scm, data, new EnvVars("MAIN", "master"), TaskListener.NULL);
        assertThat(removed, is(1));
        assertThat(data.getBuildsByBranchName().keySet(),
                containsInAnyOrder("origin/feature-1", "origin/feature-2", "origin/master"));
    }

    @Test
    void otherBuildChoosersKeepHistory() {
        BuildData data = buildData("origin/feature-1", "origin/other");
        GitSCM scm = new GitSCM(GitSCM.createRepoList("https://example.com/repo.git", null),
                List.of(new BranchSpec("*/feature-*")), null, null,
                List.of(new BuildChooserSetting(new InverseBuildChooser())));
        int removed = new LimitBuildsByBranchHistory().prune(scm, data, new EnvVars(), TaskListener.NULL);
        assertThat(removed, is(0));
        assertThat(data.getBuildsByBranchName().size(), is(2));
    }

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(LimitBuildsByBranchHistory.class).usingGetClass().verify();
    }

    private static boolean pollAfterMasterThenFeature(JenkinsRule r, GitSampleRepoRule repo, GitSCMExtension extension) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("BRANCH", "master")));
        p.setScm(scm(repo, "*/${BRANCH}", extension));
        build(r, p, "master");
        build(r, p, "feature-1");
        return p.poll(StreamTaskListener.fromStdout()).hasChanges();
    }

    private static GitSCM scmFor(String spec) {
        return new GitSCM(GitSCM.createRepoList("https://example.com/repo.git", null),
                List.of(new BranchSpec(spec)), null, null, List.of());
    }

    private static GitSCM scm(GitSampleRepoRule repo, String spec, GitSCMExtension extension) {
        List<GitSCMExtension> extensions = new ArrayList<>();
        if (extension != null) {
            extensions.add(extension);
        }
        return new GitSCM(GitSCM.createRepoList(repo.fileUrl(), null), List.of(new BranchSpec(spec)), null, null, extensions);
    }

    private static BuildData buildData(String... branches) {
        BuildData data = new BuildData();
        int number = 1;
        for (String branch : branches) {
            data.buildsByBranchName.put(branch, new Build(new Revision(ObjectId.zeroId()), number++, Result.SUCCESS));
        }
        return data;
    }

    private static void createBranches(GitSampleRepoRule repo, int count) throws Exception {
        repo.init();
        for (int i = 1; i <= count; i++) {
            repo.git("checkout", "-q", "-b", "feature-" + i, "master");
            repo.write("f" + i, "content " + i);
            repo.git("add", "f" + i);
            repo.git("commit", "--message=commit on feature-" + i);
        }
        repo.git("checkout", "-q", "master");
    }

    private static FreeStyleBuild build(JenkinsRule r, FreeStyleProject p, String branch) throws Exception {
        return r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(
                List.of(new StringParameterValue("BRANCH", branch)))));
    }

    private static List<FreeStyleBuild> buildEachBranchOnce(JenkinsRule r, GitSampleRepoRule repo, GitSCMExtension extension) throws Exception {
        createBranches(repo, BRANCHES);
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("BRANCH", "master")));
        p.setScm(scm(repo, "*/${BRANCH}", extension));
        List<FreeStyleBuild> builds = new ArrayList<>();
        for (int i = 1; i <= BRANCHES; i++) {
            builds.add(build(r, p, "feature-" + i));
        }
        return builds;
    }

    private static int historySize(FreeStyleBuild b) {
        return b.getAction(BuildData.class).getBuildsByBranchName().size();
    }

    private static long buildXmlBytes(FreeStyleBuild b) {
        return new File(b.getRootDir(), "build.xml").length();
    }
}
