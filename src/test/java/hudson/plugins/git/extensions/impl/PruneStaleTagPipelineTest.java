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

import static hudson.Functions.isWindows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import hudson.plugins.git.util.GitUtilsTest;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.util.SystemReader;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.TestCliGitAPIImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PruneStaleTagPipelineTest {

    @TempDir
    private File fileRule;

    private JenkinsRule r;

    private TaskListener listener;

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        r = rule;
        listener = new LogTaskListener(Logger.getLogger("prune tags"), Level.FINEST);

        SystemReader.getInstance().getUserConfig().clear();
        GitSCM.ALLOW_LOCAL_CHECKOUT = true;
    }

    @AfterEach
    void afterEach() {
        GitSCM.ALLOW_LOCAL_CHECKOUT = false;
    }

    @Issue("JENKINS-61869")
    @Test
    void verify_that_local_tag_is_pruned_when_not_exist_on_remote_using_pipeline() throws Exception {
        File remoteRepo = newFolder(fileRule, "remote");

        // create a remote repository without one tag
        GitClient remoteClient = initRepository(remoteRepo);
        String tagName = "tag";
        String tagComment = "tag comment";
        remoteClient.tag(tagName, tagComment);

        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "pruneTags");

        FilePath workspace = r.jenkins.getWorkspaceFor(job);
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
        this.r.assertBuildStatus(Result.SUCCESS, this.r.waitForCompletion(r));
        // Check JENKINS-66651 - token macro expansion in Pipeline
        this.r.waitForMessage("token macro expanded branch is remotes/origin/master", r); // Unexpected but current behavior

        // remove tag on remote, tag remains on local cloned repository
        remoteClient.deleteTag(tagName);

        // second run it should remove stale tags
        r = job.scheduleBuild2(0).waitForStart();
        this.r.assertBuildStatus(Result.SUCCESS, this.r.waitForCompletion(r));

        GitClient localClient = newGitClient(new File(workspace.getRemote()));
        assertFalse(localClient.tagExists(tagName), "local tag has not been pruned");
    }

    private GitClient newGitClient(File localRepo) {
        String gitExe = isWindows() ? "git.exe" : "git";
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

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
