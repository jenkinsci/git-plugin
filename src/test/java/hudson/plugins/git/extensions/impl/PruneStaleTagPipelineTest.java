/*
 * The MIT License
 *
 * Copyright (c) 2020 Nikolas Falco
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */
package hudson.plugins.git.extensions.impl;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.GitUtilsTest;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.util.SystemReader;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.TestCliGitAPIImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;

public class PruneStaleTagPipelineTest {

    @Rule
    public TemporaryFolder fileRule = new TemporaryFolder();
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private TaskListener listener;

    @Before
    public void setup() throws Exception {
        listener = new LogTaskListener(Logger.getLogger("prune tags"), Level.FINEST);
    }

    @Before
    public void allowNonRemoteCheckout() throws ConfigInvalidException, IOException {
        SystemReader.getInstance().getUserConfig().clear();
        GitSCM.ALLOW_LOCAL_CHECKOUT = true;
    }

    @After
    public void disallowNonRemoteCheckout() {
        GitSCM.ALLOW_LOCAL_CHECKOUT = false;
    }

    @Issue("JENKINS-61869")
    @Test
    public void verify_that_local_tag_is_pruned_when_not_exist_on_remote_using_pipeline() throws Exception {
        File remoteRepo = fileRule.newFolder("remote");

        // create a remote repository without one tag
        GitClient remoteClient = initRepository(remoteRepo);
        String tagName = "tag";
        String tagComment = "tag comment";
        remoteClient.tag(tagName, tagComment);

        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "pruneTags");

        FilePath workspace = j.jenkins.getWorkspaceFor(job);
        String remoteURL = "file://" + remoteRepo.toURI().getPath();

        job.setDefinition(new CpsFlowDefinition(""
                + "  node {\n"
                + "    checkout([$class: 'GitSCM',\n"
                + "             branches: [[name: '*/master']],\n"
                + "             extensions: [pruneTags(true)],\n"
                + "             userRemoteConfigs: [[url: '" + remoteURL + "']]\n"
                + "    ])\n"
                + "    def tokenBranch = tm '${GIT_BRANCH,fullName=false}'\n"
                + "    echo \"token macro expanded branch is ${tokenBranch}\"\n"
                + "  }\n", true));

        // first run clone the repository
        WorkflowRun r = job.scheduleBuild2(0).waitForStart();
        j.assertBuildStatus(Result.SUCCESS, j.waitForCompletion(r));
        // Check JENKINS-66651 - token macro expansion in Pipeline
        j.waitForMessage("token macro expanded branch is remotes/origin/master", r); // Unexpected but current behavior

        // remove tag on remote, tag remains on local cloned repository
        remoteClient.deleteTag(tagName);

        // second run it should remove stale tags
        r = job.scheduleBuild2(0).waitForStart();
        j.assertBuildStatus(Result.SUCCESS, j.waitForCompletion(r));

        GitClient localClient = newGitClient(new File(workspace.getRemote()));
        Assert.assertFalse("local tag has not been pruned", localClient.tagExists(tagName));
    }

    private GitClient newGitClient(File localRepo) {
        String gitExe = Functions.isWindows() ? "git.exe" : "git";
        return new TestCliGitAPIImpl(gitExe, localRepo, listener, GitUtilsTest.getConfigNoSystemEnvsVars());
    }

    private GitClient initRepository(File workspace) throws Exception {
        GitClient remoteClient = newGitClient(workspace);
        remoteClient.init();
        FileUtils.touch(new File(workspace, "test"));
        remoteClient.config(GitClient.ConfigLevel.LOCAL, "commit.gpgsign", "false");
        remoteClient.config(GitClient.ConfigLevel.LOCAL, "tag.gpgSign", "false");
        remoteClient.add("test");
        remoteClient.commit("initial commit");
        return remoteClient;
    }

}
