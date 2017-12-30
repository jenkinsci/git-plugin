/*
 * The MIT License
 *
 * Copyright 2017 Mark Waite.
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

import hudson.EnvVars;
import static hudson.plugins.git.GitSCM.createRepoList;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.AncestryBuildChooser;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.scm.RepositoryBrowser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class GitSCMUnitTest {

    private final String gitDir = ".";
    private final GitSCM gitSCM = new GitSCM(gitDir);
    private final String repoURL = "https://github.com/jenkinsci/git-plugin";

    public GitSCMUnitTest() {
    }

    @Test
    public void testGetSubmoduleCfg() {
        Collection<SubmoduleConfig> emptySubmoduleConfigList = new ArrayList<>();
        assertThat(gitSCM.getSubmoduleCfg(), is(emptySubmoduleConfigList));
    }

    @Test
    public void testSetSubmoduleCfg() {
        Collection<SubmoduleConfig> submoduleConfigList = new ArrayList<>();
        SubmoduleConfig config = new SubmoduleConfig();
        submoduleConfigList.add(config);
        gitSCM.setSubmoduleCfg(submoduleConfigList);
        assertThat(gitSCM.getSubmoduleCfg(), is(submoduleConfigList));
    }

    @Test
    public void testCreateRepoList() {
        String name = null;
        String refspec = null;
        String credentialsId = null;
        List<UserRemoteConfig> expectedRemoteConfigList = new ArrayList<>();
        UserRemoteConfig remoteConfig = new UserRemoteConfig(repoURL, name, refspec, credentialsId);
        expectedRemoteConfigList.add(remoteConfig);
        List<UserRemoteConfig> remoteConfigList = GitSCM.createRepoList(repoURL, credentialsId);
        assertUserRemoteConfigListEquals(remoteConfigList, expectedRemoteConfigList);
    }

    private void assertUserRemoteConfigListEquals(List<UserRemoteConfig> remoteConfigList, List<UserRemoteConfig> expectedRemoteConfigList) {
        /* UserRemoteConfig lacks an equals method - ugh */
        assertThat(remoteConfigList.toString(), is(expectedRemoteConfigList.toString()));
        assertThat(remoteConfigList.get(0).getUrl(), is(expectedRemoteConfigList.get(0).getUrl()));
        assertThat(remoteConfigList.get(0).getName(), is(expectedRemoteConfigList.get(0).getName()));
        assertThat(remoteConfigList.get(0).getRefspec(), is(expectedRemoteConfigList.get(0).getRefspec()));
        assertThat(remoteConfigList.get(0).getCredentialsId(), is(expectedRemoteConfigList.get(0).getCredentialsId()));
        assertThat(remoteConfigList.size(), is(1));
    }

    @Test
    public void testGetBrowser() {
        assertThat(gitSCM.getBrowser(), is(nullValue()));
    }

    @Test
    public void testSetBrowser() {
        GitRepositoryBrowser browser = new GithubWeb(repoURL);
        gitSCM.setBrowser(browser);
        assertThat(gitSCM.getBrowser(), is(browser));
    }

    @Test
    public void testGuessBrowser() {
        /* Well tested in other classes */
        RepositoryBrowser result = gitSCM.guessBrowser();
        assertThat(result, is(nullValue()));
    }

    @Test
    public void testGetBuildChooser() {
        assertThat(gitSCM.getBuildChooser(), is(instanceOf(DefaultBuildChooser.class)));
    }

    @Test
    public void testSetBuildChooser() throws Exception {
        BuildChooser ancestryBuildChooser = new AncestryBuildChooser(1, "string");
        gitSCM.setBuildChooser(ancestryBuildChooser);
        assertThat(gitSCM.getBuildChooser(), is(ancestryBuildChooser));
    }

    @Test
    public void testSetBuildChooserDefault() throws Exception {
        BuildChooser ancestryBuildChooser = new AncestryBuildChooser(1, "string");
        gitSCM.setBuildChooser(ancestryBuildChooser);
        BuildChooser defaultBuildChooser = new DefaultBuildChooser();
        gitSCM.setBuildChooser(defaultBuildChooser);
        assertThat(gitSCM.getBuildChooser(), is(instanceOf(DefaultBuildChooser.class)));
    }

    @Test
    public void testGetRepositoryByName() throws Exception {
        RemoteConfig expected = new RemoteConfig(new Config(), "origin");
        expected.addURI(new URIish(gitDir));
        assertRemoteConfigEquals(gitSCM.getRepositoryByName("origin"), expected);
    }

    private void assertRemoteConfigEquals(RemoteConfig remoteConfig, RemoteConfig expected) {
        assertThat(remoteConfig.getName(), is(expected.getName()));
        assertThat(remoteConfig.getURIs(), is(expected.getURIs()));
    }

    private void assertRemoteConfigListEquals(List<RemoteConfig> remoteConfigList, List<RemoteConfig> expectedList) {
        int expectedIndex = 0;
        for (RemoteConfig remoteConfig : remoteConfigList) {
            assertRemoteConfigEquals(remoteConfig, expectedList.get(expectedIndex++));
        }
    }

    @Test
    public void testGetRepositoryByNameNoSuchName() {
        assertThat(gitSCM.getRepositoryByName("no-such-name"), is(nullValue()));
    }

    @Test
    public void testGetRepositoryByNameEmptyName() {
        assertThat(gitSCM.getRepositoryByName(""), is(nullValue()));
    }

    @Test
    public void testGetRepositoryByNameNullName() {
        assertThat(gitSCM.getRepositoryByName(null), is(nullValue()));
    }

    @Test
    public void testGetUserRemoteConfigs() {
        String name = null;
        String refspec = null;
        String credentialsId = null;
        List<UserRemoteConfig> expectedRemoteConfigList = new ArrayList<>();
        UserRemoteConfig remoteConfig = new UserRemoteConfig(gitDir, name, refspec, credentialsId);
        expectedRemoteConfigList.add(remoteConfig);
        assertUserRemoteConfigListEquals(gitSCM.getUserRemoteConfigs(), expectedRemoteConfigList);
    }

    @Test
    public void testGetRepositories() throws Exception {
        List<RemoteConfig> expectedRemoteConfigList = new ArrayList<>();
        RemoteConfig remoteConfig = new RemoteConfig(new Config(), "origin");
        remoteConfig.addURI(new URIish(gitDir));
        expectedRemoteConfigList.add(remoteConfig);
        assertRemoteConfigListEquals(gitSCM.getRepositories(), expectedRemoteConfigList);
    }

    @Test
    public void testDeriveLocalBranchName() {
        assertThat(gitSCM.deriveLocalBranchName("origin/master"), is("master"));
        assertThat(gitSCM.deriveLocalBranchName("master"), is("master"));
        assertThat(gitSCM.deriveLocalBranchName("origin/feature/xyzzy"), is("feature/xyzzy"));
        assertThat(gitSCM.deriveLocalBranchName("feature/xyzzy"), is("feature/xyzzy"));
    }

    @Test
    public void testGetGitTool() {
        assertThat(gitSCM.getGitTool(), is(nullValue()));
    }

    @Test
    public void testGetParameterString() {
        String original = "${A}/${B} ${A}/${C}";
        EnvVars env = new EnvVars();
        env.put("A", "A-value");
        env.put("B", "B-value");
        assertThat(GitSCM.getParameterString(original, env), is("A-value/B-value A-value/${C}"));
    }

    @Test
    public void testRequiresWorkspaceForPolling() {
        /* Assumes workspace is required */
        assertTrue(gitSCM.requiresWorkspaceForPolling());
    }

    @Test
    public void testRequiresWorkspaceForPollingSingleBranch() {
        /* Force single-branch use case */
        GitSCM bigGitSCM = new GitSCM(createRepoList(repoURL, null),
                Collections.singletonList(new BranchSpec("master")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, Collections.<GitSCMExtension>emptyList());
        assertFalse(bigGitSCM.requiresWorkspaceForPolling());
    }

    @Test
    public void testRequiresWorkspaceForPollingMultiBranch() {
        /* Force multi-branch use case, no workspace required */
        GitSCM bigGitSCM = new GitSCM(createRepoList(repoURL, null),
                Collections.singletonList(new BranchSpec("**")),
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null, Collections.<GitSCMExtension>emptyList());
        assertTrue(bigGitSCM.requiresWorkspaceForPolling());
    }

    @Test
    public void testCreateChangeLogParser() {
        assertThat(gitSCM.createChangeLogParser(), is(instanceOf(GitChangeLogParser.class)));
    }

    @Test
    public void testIsDoGenerateSubmoduleConfigurations() {
        assertFalse(gitSCM.isDoGenerateSubmoduleConfigurations());
    }

    @Test
    public void testIsDoGenerateSubmoduleConfigurationsTrue() {
        GitSCM bigGitSCM = new GitSCM(createRepoList(repoURL, null),
                Collections.singletonList(new BranchSpec("master")),
                true, Collections.<SubmoduleConfig>emptyList(),
                null, null, Collections.<GitSCMExtension>emptyList());
        assertTrue(bigGitSCM.isDoGenerateSubmoduleConfigurations());
    }

    @Test
    public void testGetBranches() {
        List<BranchSpec> expectedBranchList = new ArrayList<>();
        expectedBranchList.add(new BranchSpec("**"));
        assertBranchSpecListEquals(gitSCM.getBranches(), expectedBranchList);
    }

    private void assertBranchSpecListEquals(List<BranchSpec> branchList, List<BranchSpec> expectedBranchList) {
        int expectedIndex = 0;
        for (BranchSpec branchSpec : branchList) {
            assertThat(branchSpec.getName(), is(expectedBranchList.get(expectedIndex++).getName()));
        }
        assertThat(branchList.size(), is(expectedBranchList.size()));
    }

    @Test
    public void testGetKey() {
        assertThat(gitSCM.getKey(), is("git " + gitDir));
    }

    @Test
    @Deprecated
    public void testGetMergeOptions() throws Exception {
        PreBuildMergeOptions expectedMergeOptions = new PreBuildMergeOptions();
        PreBuildMergeOptions mergeOptions = gitSCM.getMergeOptions();
        assertThat(mergeOptions.getRemoteBranchName(), is(expectedMergeOptions.getRemoteBranchName()));
        assertThat(mergeOptions.getMergeTarget(), is(expectedMergeOptions.getMergeTarget()));
    }
}
