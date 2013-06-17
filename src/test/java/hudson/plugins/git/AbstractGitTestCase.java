package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.*;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.PathRestriction;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.extensions.impl.UserExclusion;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.remoting.VirtualChannel;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.HudsonTestCase;


/**
 * Base class for single repository git plugin tests.
 *
 * @author Kohsuke Kawaguchi
 * @author ishaaq
 */
public abstract class AbstractGitTestCase extends HudsonTestCase {
	protected TaskListener listener;

	protected TestGitRepo testRepo;
	
	// aliases of testRepo properties
	protected PersonIdent johnDoe;
	protected PersonIdent janeDoe;
	protected File workDir; // aliases "gitDir"
	protected FilePath workspace; // aliases "gitDirPath"
	protected GitClient git;
	
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        listener = StreamTaskListener.fromStderr();

        testRepo = new TestGitRepo("unnamed", this, listener);
        johnDoe = testRepo.johnDoe;
        janeDoe = testRepo.janeDoe;
        workDir = testRepo.gitDir;
        workspace = testRepo.gitDirPath;
        git = testRepo.git;
    }

    protected void commit(final String fileName, final PersonIdent committer, final String message) throws GitException {
    	testRepo.commit(fileName, committer, message);
    }

    protected void commit(final String fileName, final String fileContent, final PersonIdent committer, final String message) throws GitException {
    	testRepo.commit(fileName, fileContent, committer, message);
    }

    protected void commit(final String fileName, final PersonIdent author, final PersonIdent committer,
                        final String message) throws GitException {
    	testRepo.commit(fileName, author, committer, message);
    }

    protected List<UserRemoteConfig> createRemoteRepositories() throws IOException {
        return testRepo.remoteConfigs();
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
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                null,
                createRemoteRepositories(),
                branches,
                false, Collections.<SubmoduleConfig>emptyList(),
                false, new DefaultBuildChooser(), null, null, authorOrCommitter, null,
                localBranch, fastRemotePoll, false,
                false, Collections.<GitSCMExtension>emptyList());
        if (relativeTargetDir!=null)
            scm.getExtensions().add(new RelativeTargetDirectory(relativeTargetDir));
        if (excludedUsers!=null)
            scm.getExtensions().add(new UserExclusion(excludedUsers));
        if (excludedRegions!=null || includedRegions!=null)
            scm.getExtensions().add(new PathRestriction(includedRegions,excludedRegions));

        project.setScm(scm);
        project.getBuildersList().add(new CaptureEnvironmentBuilder());
        return project;
    }

    protected FreeStyleProject setupSimpleProject(String branchString) throws Exception {
        return setupProject(branchString,false);
    }

    protected FreeStyleBuild build(final FreeStyleProject project, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
        final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(build.getLog());
        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(expectedNewlyCommittedFile + " file not found in workspace", build.getWorkspace().child(expectedNewlyCommittedFile).exists());
        }
        if(expectedResult != null) {
            assertBuildStatus(expectedResult, build);
        }
        return build;
    }

    protected FreeStyleBuild build(final FreeStyleProject project, final String parentDir, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
        final FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(build.getLog());
        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(build.getWorkspace().child(parentDir).child(expectedNewlyCommittedFile).exists());
        }
        if(expectedResult != null) {
            assertBuildStatus(expectedResult, build);
        }
        return build;
    }

    protected EnvVars getEnvVars(FreeStyleProject project) {
        for (hudson.tasks.Builder b : project.getBuilders()) {
            if (b instanceof CaptureEnvironmentBuilder) {
                return ((CaptureEnvironmentBuilder)b).getEnvVars();
            }
        }
        return new EnvVars();
    }

    protected void setVariables(Node node, EnvironmentVariablesNodeProperty.Entry... entries) throws IOException {
        node.getNodeProperties().replaceBy(
                Collections.singleton(new EnvironmentVariablesNodeProperty(
                        entries)));

    }

    protected String getHeadRevision(AbstractBuild build, final String branch) throws IOException, InterruptedException {
        return build.getWorkspace().act(new FilePath.FileCallable<String>() {
                public String invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
                    try {
                        return Git.with(null, null).in(f).getClient().getRepository().resolve("refs/heads/"+ branch).name();
                    } catch (GitException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
    }
}
