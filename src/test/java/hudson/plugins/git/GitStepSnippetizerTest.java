/*
 * The MIT License
 *
 * Copyright 2022 Mark Waite.
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
package hudson.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import java.util.Random;
import jenkins.plugins.git.GitStep;
import jenkins.plugins.git.JenkinsRuleUtil;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Test workflow snippet generation for the git step. Workflow snippets should not
 * display parameters if the default value of the parameter is unmodified.
 *
 * @author Mark Waite
 */
public class GitStepSnippetizerTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    private final Random random = new Random();
    private final SnippetizerTester tester = new SnippetizerTester(r);

    private final String url = "https://github.com/jenkinsci/git-plugin.git";
    private final GitStep gitStep = new GitStep(url);

    @After
    public void makeFilesWritable(TaskListener listener) throws Exception {
        JenkinsRuleUtil.makeFilesWritable(r.getWebAppRoot(), listener);
        if (r.jenkins != null) {
            JenkinsRuleUtil.makeFilesWritable(r.jenkins.getRootDir(), listener);
        }
    }

    /* Adding the default values to the step should not alter the output of the
     * round trip.
     */
    private void addRandomDefaultValues(GitStep step, @NonNull String skipThese) {
        if (random.nextBoolean() && !skipThese.contains("poll")) {
            step.setPoll(true);
        }
        if (random.nextBoolean() && !skipThese.contains("changelog")) {
            step.setChangelog(true);
        }
        if (random.nextBoolean() && !skipThese.contains("branch")) {
            step.setBranch("master");
        }
        if (random.nextBoolean() && !skipThese.contains("credentialsId")) {
            step.setCredentialsId("");
        }
    }

    /* Check that a minimal `git '..url..'` step is correctly parsed */
    @Test
    public void gitSimplest() throws Exception {
        addRandomDefaultValues(gitStep, "");
        tester.assertRoundTrip(gitStep, "git '" + url + "'");
    }

    @Test
    public void gitCredentials() throws Exception {
        String credentialsId = "my-credential";
        gitStep.setCredentialsId(credentialsId);
        addRandomDefaultValues(gitStep, "credentialsId");
        tester.assertRoundTrip(gitStep, "git credentialsId: '" + credentialsId + "', url: '" + url + "'");
    }

    @Test
    public void gitBranch() throws Exception {
        String branch = "4.10.x";
        gitStep.setBranch(branch);
        addRandomDefaultValues(gitStep, "branch");
        tester.assertRoundTrip(gitStep, "git branch: '" + branch + "', url: '" + url + "'");
    }

    @Test
    public void gitNoPoll() throws Exception {
        gitStep.setPoll(false);
        addRandomDefaultValues(gitStep, "poll");
        tester.assertRoundTrip(gitStep, "git poll: false, url: '" + url + "'");
    }

    @Test
    public void gitNoChangelog() throws Exception {
        gitStep.setChangelog(false);
        addRandomDefaultValues(gitStep, "changelog");
        tester.assertRoundTrip(gitStep, "git changelog: false, url: '" + url + "'");
    }
}
