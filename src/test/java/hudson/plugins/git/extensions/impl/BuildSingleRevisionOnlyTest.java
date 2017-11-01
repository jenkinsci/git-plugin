package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;

import hudson.plugins.git.GitSCM;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class BuildSingleRevisionOnlyTest extends AbstractGitTestCase {

    @Test
    public void testSingleRevision() throws Exception {
        // This is the new behaviour
        List<BranchSpec> branchSpec = new ArrayList<>();
        branchSpec.add(new BranchSpec("master"));
        branchSpec.add(new BranchSpec("foo"));
        branchSpec.add(new BranchSpec("bar"));
        FreeStyleProject project = setupProject(branchSpec, false, "",
                "","",
                "", false, "");

        ((GitSCM) project.getScm()).getExtensions().add(new BuildSingleRevisionOnly());
        final String commitFile = "commitFile1";
        // create the initial master commit
        commit(commitFile, johnDoe, "Initial commit in master");

        // create additional branches and commits
        git.branch("foo");
        git.branch("bar");
        git.checkoutBranch("foo", "master");
        commit(commitFile, johnDoe, "Commit in foo");
        git.checkoutBranch("bar", "master");
        commit(commitFile, johnDoe, "Commit in bar");

        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile);

        rule.assertBuildStatusSuccess(build);
        boolean result = build.getLog(100).contains(
                String.format("Scheduling another build to catch up with %s", project.getName()));
        assert !result;
    }

    @Test
    public void testMultiRevision() throws Exception {
        // This is the old and now default behaviour
        List<BranchSpec> branchSpec = new ArrayList<>();
        branchSpec.add(new BranchSpec("master"));
        branchSpec.add(new BranchSpec("foo"));
        branchSpec.add(new BranchSpec("bar"));
        FreeStyleProject project = setupProject(branchSpec, false, "",
                "","",
                "", false, "");

        final String commitFile = "commitFile1";
        // create the initial master commit
        commit(commitFile, johnDoe, "Initial commit in master");

        // create additional branches and commits
        git.branch("foo");
        git.branch("bar");
        git.checkoutBranch("foo", "master");
        commit(commitFile, johnDoe, "Commit in foo");
        git.checkoutBranch("bar", "master");
        commit(commitFile, johnDoe, "Commit in bar");

        final FreeStyleBuild build = build(project, Result.SUCCESS, commitFile);

        rule.assertBuildStatusSuccess(build);
        boolean result = build.getLog(100).contains(
                String.format("Scheduling another build to catch up with %s", project.getName()));
        assert result;
    }
}