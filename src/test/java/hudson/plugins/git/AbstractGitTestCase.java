package hudson.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
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
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.triggers.SCMTrigger;
import hudson.util.StreamTaskListener;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import jenkins.MasterToSlaveFileCallable;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.junit.Before;
import org.junit.Rule;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.junit.Assert.*;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Base class for single repository git plugin tests.
 *
 * @author Kohsuke Kawaguchi
 * @author ishaaq
 */
public abstract class AbstractGitTestCase {
    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    protected TaskListener listener;

    protected TestGitRepo testRepo;

    // aliases of testRepo properties
    protected PersonIdent johnDoe;
    protected PersonIdent janeDoe;
    protected File workDir; // aliases "gitDir"
    protected FilePath workspace; // aliases "gitDirPath"
    protected GitClient git;

    @Before
    public void setUp() throws Exception {
        listener = StreamTaskListener.fromStderr();

        testRepo = new TestGitRepo("unnamed", sampleRepo.getRoot(), listener);
        johnDoe = testRepo.johnDoe;
        janeDoe = testRepo.janeDoe;
        workDir = testRepo.gitDir;
        workspace = testRepo.gitDirPath;
        git = testRepo.git;
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

    protected List<UserRemoteConfig> createRemoteRepositories(StandardCredentials credential) throws IOException {
        return testRepo.remoteConfigs(credential);
    }

    protected FreeStyleProject createFreeStyleProject() throws IOException {
        return rule.createFreeStyleProject();
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

    protected FreeStyleProject setupProject(String branchString, StandardCredentials credential) throws Exception {
        return setupProject(Collections.singletonList(new BranchSpec(branchString)),
                false, null, null,
                null, null, false,
                null, null, credential);
    }

    protected FreeStyleProject setupProject(List<BranchSpec> branches, boolean authorOrCommitter,
                                            String relativeTargetDir, String excludedRegions,
                                            String excludedUsers, String localBranch, boolean fastRemotePoll,
                                            String includedRegions) throws Exception {
        return setupProject(branches,
                authorOrCommitter, relativeTargetDir, excludedRegions,
                excludedUsers, localBranch, fastRemotePoll,
                includedRegions, null, null);
    }

    protected FreeStyleProject setupProject(String branchString, List<SparseCheckoutPath> sparseCheckoutPaths) throws Exception {
        return setupProject(Collections.singletonList(new BranchSpec(branchString)),
                false, null, null,
                null, null, false,
                null, sparseCheckoutPaths, null);
    }

    protected FreeStyleProject setupProject(List<BranchSpec> branches, boolean authorOrCommitter,
                String relativeTargetDir, String excludedRegions,
                String excludedUsers, String localBranch, boolean fastRemotePoll,
                String includedRegions, List<SparseCheckoutPath> sparseCheckoutPaths,
                StandardCredentials credential) throws Exception {
        FreeStyleProject project = createFreeStyleProject();
        GitSCM scm = new GitSCM(
                createRemoteRepositories(credential),
                branches,
                false, Collections.<SubmoduleConfig>emptyList(),
                null, null,
                Collections.<GitSCMExtension>emptyList());
        if (credential != null) {
            project.getBuildersList().add(new HasCredentialBuilder(credential.getId()));
        }
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
     * @param repos git remote repositories
     * @param branchSpecs branch specs
     * @param scmTriggerSpec scm trigger spec
     * @param disableRemotePoll disable workspace-less polling via "git ls-remote"
     * @param enforceGitClient enforce git client
     * @return the created project
     * @throws Exception on error
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
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getLog());
        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(expectedNewlyCommittedFile + " file not found in workspace", build.getWorkspace().child(expectedNewlyCommittedFile).exists());
        }
        if(expectedResult != null) {
            rule.assertBuildStatus(expectedResult, build);
        }
        return build;
    }

    protected FreeStyleBuild build(final FreeStyleProject project, final String parentDir, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
        final FreeStyleBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getLog());
        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(build.getWorkspace().child(parentDir).child(expectedNewlyCommittedFile).exists());
        }
        if(expectedResult != null) {
            rule.assertBuildStatus(expectedResult, build);
        }
        return build;
    }
    
    protected MatrixBuild build(final MatrixProject project, final Result expectedResult, final String...expectedNewlyCommittedFiles) throws Exception {
        final MatrixBuild build = project.scheduleBuild2(0).get();
        System.out.println(build.getLog());
        for(final String expectedNewlyCommittedFile : expectedNewlyCommittedFiles) {
            assertTrue(expectedNewlyCommittedFile + " file not found in workspace", build.getWorkspace().child(expectedNewlyCommittedFile).exists());
        }
        if(expectedResult != null) {
            rule.assertBuildStatus(expectedResult, build);
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

    /* A utility method that displays a git repo. Useful to visualise merges. */
    public void showRepo(TestGitRepo repo, String msg) throws Exception {
        System.out.println("*********** "+msg+" ***********");
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            int returnCode = new Launcher.LocalLauncher(listener).launch().cmds("git", "log","--all","--graph","--decorate","--oneline").pwd(repo.gitDir.getCanonicalPath()).stdout(out).join();
            System.out.println(out.toString());
        }
    }

    public static class HasCredentialBuilder extends Builder {

        private final String id;

        @DataBoundConstructor
        public HasCredentialBuilder(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                throws InterruptedException, IOException {
            IdCredentials credentials = CredentialsProvider.findCredentialById(id, IdCredentials.class, build);
            if (credentials == null) {
                listener.getLogger().printf("Could not find any credentials with id %s%n", id);
                build.setResult(Result.FAILURE);
                return false;
            } else {
                listener.getLogger().printf("Found %s credentials with id %s%n", CredentialsNameProvider.name(credentials), id);
                return true;
            }
        }

        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            @Override
            public String getDisplayName() {
                return "Check that credentials exist";
            }
        }
    }
}
