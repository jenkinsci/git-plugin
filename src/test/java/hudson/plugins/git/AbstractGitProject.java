/*
 * The MIT License
 *
 * Copyright 2015 Mark Waite.
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
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.DisableRemotePoll;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import hudson.plugins.git.extensions.impl.PathRestriction;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.extensions.impl.SparseCheckoutPath;
import hudson.plugins.git.extensions.impl.SparseCheckoutPaths;
import hudson.plugins.git.extensions.impl.UserExclusion;
import hudson.remoting.VirtualChannel;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.triggers.SCMTrigger;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import jenkins.MasterToSlaveFileCallable;
import org.eclipse.jgit.lib.ObjectId;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.JGitTool;

import static org.junit.Assert.*;

import org.junit.Rule;

import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Abstract class that provides convenience methods to configure projects.
 * @author Mark Waite
 */
public class AbstractGitProject extends AbstractGitRepository {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    protected FreeStyleProject setupProject(List<BranchSpec> branches, boolean authorOrCommitter) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        GitSCM scm = new GitSCM(remoteConfigs(), branches, false,
                Collections.<SubmoduleConfig>emptyList(), null, null,
                Collections.<GitSCMExtension>singletonList(new DisableRemotePoll()));
        project.setScm(scm);
        project.getBuildersList().add(new CaptureEnvironmentBuilder());
        return project;
    }

    protected FreeStyleProject setupSimpleProject(String branchString) throws Exception {
        return setupProject(Collections.singletonList(new BranchSpec(branchString)), false);
    }

    protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter) throws Exception {
        return setupProject(branchString, authorOrCommitter, null);
    }

    protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
            String relativeTargetDir) throws Exception {
        return setupProject(branchString, authorOrCommitter, relativeTargetDir, null, null, null);
    }

    protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
            String relativeTargetDir,
            String excludedRegions,
            String excludedUsers,
            String includedRegions) throws Exception {
        return setupProject(branchString, authorOrCommitter, relativeTargetDir, excludedRegions, excludedUsers, null, false, includedRegions);
    }

    protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
            String relativeTargetDir,
            String excludedRegions,
            String excludedUsers,
            boolean fastRemotePoll,
            String includedRegions) throws Exception {
        return setupProject(branchString, authorOrCommitter, relativeTargetDir, excludedRegions, excludedUsers, null, fastRemotePoll, includedRegions);
    }

    protected FreeStyleProject setupProject(String branchString, boolean authorOrCommitter,
            String relativeTargetDir, String excludedRegions,
            String excludedUsers, String localBranch, boolean fastRemotePoll,
            String includedRegions) throws Exception {
        return setupProject(Collections.singletonList(new BranchSpec(branchString)),
                authorOrCommitter, relativeTargetDir, excludedRegions,
                excludedUsers, localBranch, fastRemotePoll,
                includedRegions);
    }

    protected FreeStyleProject setupProject(List<BranchSpec> branches, boolean authorOrCommitter,
            String relativeTargetDir, String excludedRegions,
            String excludedUsers, String localBranch, boolean fastRemotePoll,
            String includedRegions) throws Exception {
        return setupProject(branches,
                authorOrCommitter, relativeTargetDir, excludedRegions,
                excludedUsers, localBranch, fastRemotePoll,
                includedRegions, null);
    }

    protected FreeStyleProject setupProject(String branchString, List<SparseCheckoutPath> sparseCheckoutPaths) throws Exception {
        return setupProject(Collections.singletonList(new BranchSpec(branchString)),
                false, null, null,
                null, null, false,
                null, sparseCheckoutPaths);
    }

    protected FreeStyleProject setupProject(List<BranchSpec> branches, boolean authorOrCommitter,
            String relativeTargetDir, String excludedRegions,
            String excludedUsers, String localBranch, boolean fastRemotePoll,
            String includedRegions, List<SparseCheckoutPath> sparseCheckoutPaths) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        GitSCM scm = new GitSCM(
                remoteConfigs(),
                branches,
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new DisableRemotePoll()); // don't work on a file:// repository
        if (relativeTargetDir != null) {
            scm.getExtensions().add(new RelativeTargetDirectory(relativeTargetDir));
        }
        if (excludedUsers != null) {
            scm.getExtensions().add(new UserExclusion(excludedUsers));
        }
        if (excludedRegions != null || includedRegions != null) {
            scm.getExtensions().add(new PathRestriction(includedRegions, excludedRegions));
        }

        scm.getExtensions().add(new SparseCheckoutPaths(sparseCheckoutPaths));

        project.setScm(scm);
        project.getBuildersList().add(new CaptureEnvironmentBuilder());
        return project;
    }

    /**
     * Creates a new project and configures the GitSCM according the parameters.
     *
     * @param repos
     * @param branchSpecs
     * @param scmTriggerSpec
     * @param disableRemotePoll Disable Workspace-less polling via "git
     * ls-remote"
     * @return
     * @throws Exception
     */
    protected FreeStyleProject setupProject(List<UserRemoteConfig> repos, List<BranchSpec> branchSpecs,
            String scmTriggerSpec, boolean disableRemotePoll, EnforceGitClient enforceGitClient) throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        GitSCM scm = new GitSCM(
                repos,
                branchSpecs,
                false, Collections.<SubmoduleConfig>emptyList(),
                null, JGitTool.MAGIC_EXENAME,
                Collections.<GitSCMExtension>emptyList());
        if (disableRemotePoll) {
            scm.getExtensions().add(new DisableRemotePoll());
        }
        if (enforceGitClient != null) {
            scm.getExtensions().add(enforceGitClient);
        }
        project.setScm(scm);
        if (scmTriggerSpec != null) {
            SCMTrigger trigger = new SCMTrigger(scmTriggerSpec);
            project.addTrigger(trigger);
            trigger.start(project, true);
        }
        project.getBuildersList().add(new CaptureEnvironmentBuilder());
        project.save();
        return project;
    }

    protected FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult, final String... expectedNewlyCommittedFiles) throws Exception {
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        for (final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(expectedNewlyCommittedFile + " file not found in workspace", build.getWorkspace().child(expectedNewlyCommittedFile).exists());
        }
        if (expectedResult != null) {
            jenkins.assertBuildStatus(expectedResult, build);
        }
        return build;
    }

    protected FreeStyleBuild build(final FreeStyleProject project, final String parentDir, final Result expectedResult, final String... expectedNewlyCommittedFiles) throws Exception {
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        for (final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(build.getWorkspace().child(parentDir).child(expectedNewlyCommittedFile).exists());
        }
        if (expectedResult != null) {
            jenkins.assertBuildStatus(expectedResult, build);
        }
        return build;
    }

    protected MatrixBuild build(final MatrixProject project, final Result expectedResult, final String... expectedNewlyCommittedFiles) throws Exception {
        final MatrixBuild build = project.scheduleBuild2(0).get();
        for (final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(expectedNewlyCommittedFile + " file not found in workspace", build.getWorkspace().child(expectedNewlyCommittedFile).exists());
        }
        if (expectedResult != null) {
            jenkins.assertBuildStatus(expectedResult, build);
        }
        return build;
    }

    protected String getHeadRevision(AbstractBuild build, final String branch) throws IOException, InterruptedException {
        return build.getWorkspace().act(new MasterToSlaveFileCallable<String>() {
            public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                try {
                    ObjectId oid = Git.with(null, null).in(f).getClient().getRepository().resolve("refs/heads/" + branch);
                    return oid.name();
                } catch (GitException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    protected EnvVars getEnvVars(FreeStyleProject project) {
        for (hudson.tasks.Builder b : project.getBuilders()) {
            if (b instanceof CaptureEnvironmentBuilder) {
                return ((CaptureEnvironmentBuilder) b).getEnvVars();
            }
        }
        return new EnvVars();
    }

    protected void setVariables(Node node, EnvironmentVariablesNodeProperty.Entry... entries) throws IOException {
        node.getNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                                entries)));

    }
}
