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

import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CheckoutOption;
import hudson.plugins.git.extensions.impl.GitLFSPull;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Test workflow snippet generation for the checkout step. Workflow
 * snippets should not display parameters if the default value of the
 * parameter is unmodified.
 *
 * @author Mark Waite
 */
public class CheckoutStepSnippetizerTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    private final Random random = new Random();
    private final SnippetizerTester tester = new SnippetizerTester(r);

    private final String url = "https://github.com/jenkinsci/git-plugin.git";
    private final String remoteName = "";
    private final String remoteRefspec = "";
    private final String credentialsId = "";
    private final UserRemoteConfig userRemoteConfig = new UserRemoteConfig(url, remoteName, remoteRefspec, credentialsId);
    private final List<UserRemoteConfig> userRemoteConfigList = new ArrayList(List.of(userRemoteConfig));
    private final String branchName = "";
    private final BranchSpec branchSpec = new BranchSpec(branchName);
    private final List<BranchSpec> branchSpecList = new ArrayList(List.of(branchSpec));
    private final String gitTool = null;
    private final GitRepositoryBrowser browser = null;
    private final List<GitSCMExtension> extensionList = new ArrayList();
    private final GitSCM databoundGit = new GitSCM(userRemoteConfigList, branchSpecList, browser, gitTool, extensionList);
    private final GitSCM convenienceGit = new GitSCM(url);
    private final GenericSCMStep checkoutStep = new GenericSCMStep((random.nextBoolean() || true) ? databoundGit : convenienceGit);

    /* Defaults that should be removed from round trip */
    private final String junkBranches = "branches: [[name: '**']], ";
    private final String junkExtensions = "extensions: [], ";

    /* Tested values common to many tests */
    private final String remoteConfig = "userRemoteConfigs: [[url: '" + url + "']]";

    @Test
    public void checkoutSimplest() throws Exception {
        tester.assertRoundTrip(checkoutStep, "checkout scmGit("
                + junkBranches
                + junkExtensions
                + "userRemoteConfigs: [[url: '" + url + "']])");
        tester.assertParseStep(checkoutStep, "checkout git("
                + "branches: [[name: '**']], "
                // Parses correctly with or without junkExtensions
                + (random.nextBoolean() ? junkExtensions : "")
                + remoteConfig + ")");
    }

    @Test
    public void checkoutNoPoll() throws Exception {
        checkoutStep.setPoll(false);
        tester.assertRoundTrip(checkoutStep, "checkout poll: false, scm: scmGit("
                + junkBranches
                + junkExtensions
                + remoteConfig + ")");
        tester.assertParseStep(checkoutStep, "checkout poll: false, scm: scmGit("
                + "branches: [[name: '**']], "
                // Parses correctly with or without junkExtensions
                + (random.nextBoolean() ? junkExtensions : "")
                + remoteConfig + ")");
    }

    @Test
    public void checkoutNoChangelog() throws Exception {
        checkoutStep.setChangelog(false);
        tester.assertRoundTrip(checkoutStep, "checkout changelog: false, scm: scmGit("
                + junkBranches
                + junkExtensions
                + remoteConfig + ")");
        tester.assertParseStep(checkoutStep, "checkout changelog: false, scm: scmGit("
                + "branches: [[name: '**']], "
                // Parses correctly with or without junkExtensions
                + (random.nextBoolean() ? junkExtensions : "")
                + remoteConfig + ")");
    }

    @Test
    public void checkoutCredentials() throws Exception {
        String myCredential = "my-credential";
        UserRemoteConfig config = new UserRemoteConfig(url, remoteName, remoteRefspec, myCredential);
        List<UserRemoteConfig> configList = new ArrayList(List.of(config));
        GitSCM gitSCM = new GitSCM(configList, branchSpecList, browser, gitTool, extensionList);
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        tester.assertRoundTrip(step, "checkout scmGit("
                + junkBranches
                + junkExtensions
                + "userRemoteConfigs: [[credentialsId: '" + myCredential + "', url: '" + url + "']])");
        tester.assertParseStep(step, "checkout scmGit("
                + "branches: [[name: '**']], "
                // Parses correctly with or without junkExtensions
                + (random.nextBoolean() ? junkExtensions : "")
                + "userRemoteConfigs: [[credentialsId: '" + myCredential + "', url: '" + url + "']])");
    }

    @Test
    public void checkoutBranch() throws Exception {
        String branch = "4.10.x";
        List<BranchSpec> branchList = new ArrayList(List.of(new BranchSpec(branch)));
        GitSCM gitSCM = new GitSCM(userRemoteConfigList, branchList, browser, gitTool, extensionList);
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        tester.assertRoundTrip(step, "checkout scmGit("
                + "branches: [[name: '" + branch + "']], "
                + junkExtensions
                + remoteConfig + ")");
        tester.assertParseStep(step, "checkout scmGit("
                + "branches: [[name: '" + branch + "']], "
                // Parses correctly with or without junkExtensions
                + (random.nextBoolean() ? junkExtensions : "")
                + remoteConfig + ")");
    }

    @Test
    public void checkoutSubmoduleSimplest() throws Exception {
        GitSCM gitSCM = new GitSCM(url);
        List<GitSCMExtension> extensions = gitSCM.getExtensions();
        extensions.add(new SubmoduleOption());
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        String testedExtensions = "extensions: [submodule()], ";
        tester.assertRoundTrip(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
        tester.assertParseStep(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
    }

    @Test
    public void checkoutSubmoduleOldConstructorMinimalArgs() throws Exception {
        GitSCM gitSCM = new GitSCM(url);
        List<GitSCMExtension> extensions = gitSCM.getExtensions();
        boolean disableSubmodules = false;
        boolean recursiveSubmodules = false;
        boolean trackingSubmodules = false;
        String reference = null;
        Integer timeout = null;
        boolean parentCredentials = false;
        extensions.add(new SubmoduleOption(disableSubmodules, recursiveSubmodules, trackingSubmodules, reference, timeout, parentCredentials));
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        String testedExtensions = "extensions: [submodule()], ";
        tester.assertRoundTrip(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
        tester.assertParseStep(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
    }

    @Test
    public void checkoutSubmoduleOldConstructorReferenceRepo() throws Exception {
        GitSCM gitSCM = new GitSCM(url);
        List<GitSCMExtension> extensions = gitSCM.getExtensions();
        boolean disableSubmodules = false;
        boolean recursiveSubmodules = false;
        boolean trackingSubmodules = false;
        String reference = "/var/cache/git-plugin.git"; // Only change from default values
        Integer timeout = null;
        boolean parentCredentials = false;
        extensions.add(new SubmoduleOption(disableSubmodules, recursiveSubmodules, trackingSubmodules, reference, timeout, parentCredentials));
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        String testedExtensions = "extensions: [submodule(reference: '" + reference + "')], ";
        tester.assertRoundTrip(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
        tester.assertParseStep(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
    }

    @Test
    public void checkoutSubmoduleOldConstructorDisableSubmodules() throws Exception {
        GitSCM gitSCM = new GitSCM(url);
        List<GitSCMExtension> extensions = gitSCM.getExtensions();
        boolean disableSubmodules = true; // Only change from default values
        boolean recursiveSubmodules = false;
        boolean trackingSubmodules = false;
        String reference = null;
        Integer timeout = null;
        boolean parentCredentials = false;
        extensions.add(new SubmoduleOption(disableSubmodules, recursiveSubmodules, trackingSubmodules, reference, timeout, parentCredentials));
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        String testedExtensions = "extensions: [submodule(disableSubmodules: true)], ";
        tester.assertRoundTrip(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
        tester.assertParseStep(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
    }

    @Test
    public void checkoutTimeoutCheckoutDefault() throws Exception {
        GitSCM gitSCM = new GitSCM(url);
        List<GitSCMExtension> extensions = gitSCM.getExtensions();
        Integer timeout = null;
        extensions.add(new CheckoutOption(timeout));
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        String testedExtensions = "extensions: [checkoutOption()], ";
        tester.assertRoundTrip(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
        tester.assertParseStep(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
    }

    @Test
    public void checkoutTimeoutCheckoutNonDefault() throws Exception {
        GitSCM gitSCM = new GitSCM(url);
        List<GitSCMExtension> extensions = gitSCM.getExtensions();
        Integer timeout = 347;
        extensions.add(new CheckoutOption(timeout));
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        String testedExtensions = "extensions: [checkoutOption(" + timeout + ")], ";
        tester.assertRoundTrip(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
        tester.assertParseStep(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
    }

    @Test
    public void checkoutLargeFileSupport() throws Exception {
        GitSCM gitSCM = new GitSCM(url);
        List<GitSCMExtension> extensions = gitSCM.getExtensions();
        extensions.add(new GitLFSPull());
        GenericSCMStep step = new GenericSCMStep(gitSCM);
        String testedExtensions = "extensions: [lfs()], ";
        tester.assertRoundTrip(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
        tester.assertParseStep(step, "checkout scmGit("
                + junkBranches
                + testedExtensions
                + remoteConfig + ")");
    }
}
