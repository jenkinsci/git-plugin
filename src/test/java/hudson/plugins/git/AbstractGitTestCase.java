package hudson.plugins.git;

import static org.apache.commons.lang.StringUtils.isBlank;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleBuild;
import hudson.model.Hudson;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import hudson.plugins.git.extensions.impl.DisableRemotePoll;
import hudson.plugins.git.extensions.impl.PathRestriction;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.extensions.impl.SparseCheckoutPath;
import hudson.plugins.git.extensions.impl.SparseCheckoutPaths;
import hudson.plugins.git.extensions.impl.UserExclusion;
import hudson.remoting.VirtualChannel;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.triggers.SCMTrigger;
import hudson.triggers.SCMTrigger.SCMTriggerCause;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;


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

    @Override
    protected void tearDown() throws Exception {
        try { //Avoid test failures due to failed cleanup tasks
            super.tearDown();
        } catch (Exception e) {
            if (e instanceof IOException && Functions.isWindows()) {
                return;
            }
            e.printStackTrace();
        }
    }

    protected void commit(final String fileName, final PersonIdent committer, final String message)
            throws GitException, InterruptedException {
        testRepo.commit(fileName, committer, message);
    }

    protected void commit(final String fileName, final String fileContent, final PersonIdent committer, final String message)

            throws GitException, InterruptedException {
        testRepo.commit(fileName, fileContent, committer, message);
    }

    protected void commit(final String fileName, final PersonIdent author, final PersonIdent committer,
                        final String message) throws GitException, InterruptedException {
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
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(),
                branches,
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        scm.getExtensions().add(new DisableRemotePoll()); // don't work on a file:// repository
        if (relativeTargetDir!=null)
            scm.getExtensions().add(new RelativeTargetDirectory(relativeTargetDir));
        if (excludedUsers!=null)
            scm.getExtensions().add(new UserExclusion(excludedUsers));
        if (excludedRegions!=null || includedRegions!=null)
            scm.getExtensions().add(new PathRestriction(includedRegions,excludedRegions));

        scm.getExtensions().add(new SparseCheckoutPaths(sparseCheckoutPaths));

        project.setScm(scm);
        project.getBuildersList().add(new CaptureEnvironmentBuilder());
        return project;
    }

    /**
     * Creates a new project and configures the GitSCM according the parameters.
     * @param repos
     * @param branchSpecs
     * @param scmTriggerSpec
     * @param disableRemotePoll Disable Workspace-less polling via "git ls-remote"
     * @return
     * @throws Exception
     */
    protected FreeStyleProject setupProject(List<UserRemoteConfig> repos, List<BranchSpec> branchSpecs,
                String scmTriggerSpec, boolean disableRemotePoll, EnforceGitClient enforceGitClient) throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                    repos,
                    branchSpecs,
                    false, Collections.<SubmoduleConfig>emptyList(),
                    null, JGitTool.MAGIC_EXENAME,
                    Collections.<GitSCMExtension>emptyList());
        if(disableRemotePoll) scm.getExtensions().add(new DisableRemotePoll());
        if(enforceGitClient != null) scm.getExtensions().add(enforceGitClient);
        project.setScm(scm);
        if(scmTriggerSpec != null) {
            SCMTrigger trigger = new SCMTrigger(scmTriggerSpec);
            project.addTrigger(trigger);
            trigger.start(project, true);
        }
        //project.getBuildersList().add(new CaptureEnvironmentBuilder());
        project.save();
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
    
    protected MatrixBuild build(final MatrixProject project, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
        final MatrixBuild build = project.scheduleBuild2(0, new Cause.UserCause()).get();
        System.out.println(build.getLog());
        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(expectedNewlyCommittedFile + " file not found in workspace", build.getWorkspace().child(expectedNewlyCommittedFile).exists());
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
                        ObjectId oid = Git.with(null, null).in(f).getClient().getRepository().resolve("refs/heads/" + branch);
                        return oid.name();
                    } catch (GitException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
    }

    /** A utility method that displays a git repo. Useful to visualise merges. */
    public void showRepo(TestGitRepo repo, String msg) throws Exception {
        System.out.println("*********** "+msg+" ***********");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int returnCode = new Launcher.LocalLauncher(listener).launch().cmds("git", "log","--all","--graph","--decorate","--oneline").pwd(repo.gitDir.getCanonicalPath()).stdout(out).join();
        System.out.println(out.toString());
        out.close();
    }
}
