/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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
 */

package jenkins.plugins.git;

import hudson.model.Label;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitPublisherStepTest {

  @Rule
  public JenkinsRule r = new JenkinsRule();
  @Rule
  public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

  @Test
  public void pushTagToSampleRepo() throws Exception {
    sampleRepo.init();
    sampleRepo.write("nextfile", "");
    sampleRepo.git("add", "nextfile");
    sampleRepo.git("commit", "--message=next");
    WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
    r.createOnlineSlave(Label.get("remote"));
    p.setDefinition(new CpsFlowDefinition(
      "node('remote') {\n" +
        "    ws {\n" +
        "        git(url: $/" + sampleRepo + "/$, poll: false, changelog: false)\n" +
        "        gitPublisher( url: $/" + sampleRepo + "/$, tagsToPush: [[createTag: true, tagMessage: 'WOooot', tagName: '1.0-FINAL', targetRepoName: 'origin', updateTag: false]])" +
        "    }\n" +
        "}"));
    WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    r.assertLogContains("[Pipeline] gitPublisher", b);
    r.assertLogContains(" > git tag -l 1.0-FINAL # timeout=10", b);
    r.assertLogContains(" > git tag -a -f -m WOooot 1.0-FINAL # timeout=10", b);
    r.assertLogContains("Pushing tag 1.0-FINAL to repo origin", b);
  }

  @Test
  public void pushBranchToSampleRepo() throws Exception {
    sampleRepo.init();
    sampleRepo.write("nextfile", "");
    sampleRepo.git("add", "nextfile");
    sampleRepo.git("commit", "--message=next");
    WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "demo");
    r.createOnlineSlave(Label.get("remote"));
    p.setDefinition(new CpsFlowDefinition(
      "node('remote') {\n" +
        "    ws {\n" +
        "        git(url: $/" + sampleRepo + "/$, poll: false, changelog: false)\n" +
        "        gitPublisher( url: $/" + sampleRepo + "/$, branchesToPush: [[branchName: 'cheese', targetRepoName: 'origin']])" +
        "    }\n" +
        "}"));
    WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    r.assertLogContains("[Pipeline] gitPublisher", b);
    r.assertLogContains("Pushing HEAD to branch cheese at repo origin", b);
    r.assertLogContains(" > git --version # timeout=10", b);
    r.assertLogContains(" > git push " + sampleRepo + " HEAD:cheese", b);
  }
}
