package hudson.plugins.git;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.TaskListener;
import static hudson.plugins.git.CheckoutStepSnippetizerTest.r;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.JenkinsRuleUtil;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.After;
import static org.junit.Assert.assertTrue;

public class CredentialsUserRemoteConfigTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private CredentialsStore store = null;
    private boolean useSymbolForGitSCM = true;
    private Random random = new Random();
    private String credential = "undefined-credential";

    @Before
    public void enableSystemCredentialsProvider() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.emptyList()));
        for (CredentialsStore s : CredentialsProvider.lookupStores(Jenkins.get())) {
            if (s.getProvider() instanceof SystemCredentialsProvider.ProviderImpl) {
                store = s;
                break;
            }
        }
        assertThat("The system credentials provider is enabled", store, notNullValue());
    }

    @Before
    public void chooseSymbolForGitSCM() {
        /* Use the 'scmGit' symbol instead of '$class: GitSCM */
        useSymbolForGitSCM = random.nextBoolean();
    }

    @Before
    public void generateCredentialID() {
        credential = "credential-id-" + (100 + random.nextInt(900));
    }

    @Before
    public void initSampleRepo() throws Exception {
        sampleRepo.init();
        assertTrue("Failed to create src dir in sample repo", sampleRepo.mkdirs("src"));
        sampleRepo.write("src/sample.txt", "Contents of src/sample.txt");
        sampleRepo.git("add", "src/sample.txt");
        sampleRepo.git("commit", "-m", "Add src/sample.txt to sample repo");
    }
    @After
    public void makeFilesWritable(TaskListener listener) throws Exception {
        JenkinsRuleUtil.makeFilesWritable(r.getWebAppRoot(), listener);
        if (r.jenkins != null) {
            JenkinsRuleUtil.makeFilesWritable(r.jenkins.getRootDir(), listener);
        }
    }

    private String classPrologue() {
        if (useSymbolForGitSCM) {
            return "    scmGit(\n";
        }
        return "    [$class: 'GitSCM', \n";
    }

    private String classEpilogue() {
        if (useSymbolForGitSCM) {
            return "    )\n";
        }
        return "    ]\n";
    }

    private WorkflowJob createProjectWithCredential() throws Exception {
        return createProject(true);
    }

    private WorkflowJob createProject(boolean useCredential) throws Exception {
        String credentialsPhrase = useCredential ? "credentialsId: '" + credential + "', " : "";
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + classPrologue()
                        + "      userRemoteConfigs: [[" + credentialsPhrase + "url: $/" + sampleRepo + "/$]]\n"
                        + randomPipelineCheckoutExtras()
                        + classEpilogue()
                        + "  )"
                        + "}", true));
        return p;
    }

    /* SHA that will be replaced when project is created */
    private static final String SHA_TO_REPLACE = "feedbeefbeadcededeedabed";

    /* Return randomly selected pipeline checkout configurations.
     * Pipeline assertions in this file are not affected by these assertions.
     * References to invalid classes or invalid keywords will fail the tests.
     */
    private String randomPipelineExtensions() {
        /* Valid extensions to apply to a git checkout */
        String [] extensions = {
            // ancestorCommitSha1 needs to be a SHA-1 that exists in the repository
            "[$class: 'BuildChooserSetting', buildChooser: [$class: 'AncestryBuildChooser', ancestorCommitSha1: '" + SHA_TO_REPLACE + "', maximumAgeInDays: 23]]",
            // Inverse build chooser will find nothing to build and fails the test
            // "[$class: 'BuildChooserSetting', buildChooser: [$class: 'InverseBuildChooser']]",
            "[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'src'], [path: 'Makefile']]]",
            "[$class: 'AuthorInChangelog']",
            "[$class: 'BuildChooserSetting', buildChooser: [$class: 'DefaultBuildChooser']]",
            "[$class: 'BuildSingleRevisionOnly']",
            "[$class: 'ChangelogToBranch', options: [compareRemote: 'origin', compareTarget: 'master']]",
            "[$class: 'CheckoutOption', timeout: 1]",
            "[$class: 'CleanBeforeCheckout']",
            "[$class: 'CleanCheckout']",
            "[$class: 'DisableRemotePoll']",
            "[$class: 'LocalBranch', localBranch: 'master']",
            "[$class: 'PreBuildMerge', options: [mergeRemote: 'origin', mergeTarget: 'master']]",
            "[$class: 'PruneStaleBranch']",
            "[$class: 'PruneStaleTag']",
            "[$class: 'SubmoduleOption', disableSubmodules: true]",
            "[$class: 'SubmoduleOption', depth: 1, shallow: true]",
            "[$class: 'SubmoduleOption', parentCredentials: true, recursiveSubmodules: true, threads: 13]",
            "[$class: 'SubmoduleOption', depth: 17, disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '/cache/git1.git', shallow: true, threads: 13, timeout: 11, trackingSubmodules: true]",
            "[$class: 'UserIdentity', email: 'custom.user.email@example.com', name: 'Custom User Name']",
            "[$class: 'WipeWorkspace']",
            "authorInChangelog()",
            "buildSingleRevisionOnly()",
            "changelogToBranch(changelogBase(compareRemote: 'origin', compareTarget: 'master'))",
            "checkoutOption(timeout: 17)",
            "cleanAfterCheckout()",
            "cleanAfterCheckout(deleteUntrackedNestedRepositories: false)",
            "cleanAfterCheckout(deleteUntrackedNestedRepositories: true)",
            "cleanBeforeCheckout()",
            "cleanBeforeCheckout(deleteUntrackedNestedRepositories: false)",
            "cleanBeforeCheckout(deleteUntrackedNestedRepositories: true)",
            "cloneOption(depth: 3, honorRefspec: true, noTags: true, reference: '/cache/git2.git', shallow: true, timeout: 13)",
            "cloneOption(depth: 3, honorRefspec: true, noTags: true, reference: '/cache/git3.git', shallow: true)",
            "cloneOption(depth: 3, honorRefspec: true, noTags: true, reference: '/cache/git4.git')",
            "cloneOption(depth: 3, honorRefspec: true, noTags: true)",
            "cloneOption(depth: 3, honorRefspec: true)",
            "cloneOption(depth: 3)",
            "cloneOption(honorRefspec: true, noTags: true, reference: '/cache/git5.git', shallow: true, timeout: 13)",
            "cloneOption(honorRefspec: true, noTags: true, reference: '/cache/git6.git', shallow: true)",
            "cloneOption(honorRefspec: true, noTags: true, reference: '/cache/git7.git')",
            "cloneOption(honorRefspec: true, noTags: true)",
            "cloneOption(honorRefspec: true)",
            "localBranch('master')",
            "perBuildTag()",
            "pruneStaleBranch()",
            "pruneTags()",
            "pruneTags(false)",
            "pruneTags(true)",
            "submodule(disableSubmodules: true)",
            "submodule(depth: 1, shallow: true)",
            "submodule(parentCredentials: true, recursiveSubmodules: true, threads: 13)",
            "submodule(depth: 17, disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '/cache/git1.git', shallow: true, threads: 13, timeout: 11, trackingSubmodules: true)",
        };
        List<String> extensionList = Arrays.asList(extensions);
        if (sampleRepo.hasGitLFS()) {
            // Do not test git LFS unless it is installed
            // Make extensionList mutable
            extensionList = new ArrayList<>(extensionList);
            extensionList.add("[$class: 'GitLFSPull']");
            extensionList.add("lfs()");
        }
        int extensionCount = random.nextInt(extensionList.size()); // How many extensions to add
        if (extensionCount == 0) {
            return "";
        }
        Collections.shuffle(extensionList); // Randomize the list of extensions
        StringBuilder extensionBuffer = new StringBuilder();
        extensionBuffer.append("      , extensions: [\n");
        int added = 0;
        for (String extension : extensionList) {
            if (added >= extensionCount) {
                break;
            }
            extensionBuffer.append("                    ");
            if (added != 0) {
                extensionBuffer.append(',');
            }
            extensionBuffer.append(extension);
            extensionBuffer.append('\n');
            added++;
        }
        extensionBuffer.append("      ]\n");
        return extensionBuffer.toString();
    }

    /* Return randomly selected pipeline checkout configurations.
     * These pipeline configurations should not alter the assertions in the tests.
     */
    private String randomPipelineCheckoutExtras() throws Exception {
        String[] browsers = {
            "",
            // Assembla now requires login to access their URLs
            // "[$class: 'AssemblaWeb', repoUrl: 'https://app.assembla.com/spaces/git-plugin/git/source']",
            "[$class: 'BitbucketWeb', repoUrl: 'https://markewaite@bitbucket.org/markewaite/git-plugin']",
            "[$class: 'CGit', repoUrl: 'https://git.zx2c4.com/cgit']",
            "[$class: 'FisheyeGitRepositoryBrowser', repoUrl: 'https://fisheye.apache.org/browse/ant-git']",
            "[$class: 'GitBlitRepositoryBrowser', repoUrl: 'https://github.com/MarkEWaite/git-plugin', projectName: 'git-plugin-project-name-value']",
            "[$class: 'GitLab', repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin', version: '12.10.1']",
            "[$class: 'GitLab', repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin']",
            "[$class: 'GitList', repoUrl: 'http://gitlist.org/']", // Not a real gitlist site, just the org home page
            "[$class: 'GitWeb', repoUrl: 'https://git.ti.com/gitweb']",
            "[$class: 'GithubWeb', repoUrl: 'https://github.com/jenkinsci/git-plugin']",
            "[$class: 'Gitiles', repoUrl: 'https://gerrit.googlesource.com/gitiles/']",
            "[$class: 'GogsGit', repoUrl: 'https://try.gogs.io/MarkEWaite/git-plugin']",
            "[$class: 'KilnGit', repoUrl: 'https://kiln.example.com/MarkEWaite/git-plugin']",
            "[$class: 'Phabricator', repo: 'source/tool-spacemedia', repoUrl: 'https://phabricator.wikimedia.org/source/tool-spacemedia/']",
            "[$class: 'RedmineWeb', repoUrl: 'https://www.redmine.org/projects/redmine/repository']",
            "[$class: 'RhodeCode', repoUrl: 'https://code.rhodecode.com/rhodecode-enterprise-ce']",
            "[$class: 'Stash', repoUrl: 'https://markewaite@bitbucket.org/markewaite/git-plugin']",
            "[$class: 'TFS2013GitRepositoryBrowser', repoUrl: 'https://markwaite.visualstudio.com/DefaultCollection/git-plugin/_git/git-plugin']",
            "[$class: 'ViewGitWeb', repoUrl: 'https://git.ti.com/gitweb', projectName: 'viewgitweb-project-name-value']",
            // Assembla now requires login to access their URLs
            // "assembla('https://app.assembla.com/spaces/git-plugin/git/source')",
            "bitbucket('https://markewaite@bitbucket.org/markewaite/git-plugin')",
            "cgit('https://git.zx2c4.com/cgit')",
            "fisheye('https://fisheye.apache.org/browse/ant-git')",
            "gitblit(repoUrl: 'https://github.com/MarkEWaite/git-client-plugin', projectName: 'git-plugin-project-name-value')",
            "gitLab(repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin', version: '12.10.1')",
            "gitList('http://gitlist.org/')", // Not a real gitlist site, just the org home page
            "gitWeb('https://git.ti.com/gitweb')",
            "github('https://github.com/jenkinsci/git-plugin')",
            "gitiles('https://gerrit.googlesource.com/gitiles/')",
            "gogs('https://try.gogs.io/MarkEWaite/git-plugin')", // Should this be gogsGit?
            "kiln('https://kiln.example.com/MarkEWaite/git-plugin')",
            "teamFoundation('https://markwaite.visualstudio.com/DefaultCollection/git-plugin/_git/git-plugin')",
            "phabricator(repo: 'source/tool-spacemedia', repoUrl: 'https://phabricator.wikimedia.org/source/tool-spacemedia/')",
            "redmine('https://www.redmine.org/projects/redmine/repository')",
            "rhodeCode('https://code.rhodecode.com/rhodecode-enterprise-ce')",
            "viewgit(repoUrl: 'https://repo.or.cz/viewgit.git', projectName: 'viewgit-project-name-value')", // Not likely a viewgit site, but reasonable approximation
            //  The Gitea browser is provided by the Gitea plugin, not the git plugin
            // "[$class: 'GiteaBrowser', repoUrl: 'https://try.gitea.io/MarkEWaite/git-plugin']",
        };
        String browser = browsers[random.nextInt(browsers.length)];
        StringBuilder extras = new StringBuilder();
        if (!browser.isEmpty()) {
            extras.append("      , browser: ");
            extras.append(browser);
            extras.append('\n');
        }
        extras.append(randomPipelineExtensions().replace(SHA_TO_REPLACE, sampleRepo.head()));
        if (random.nextBoolean()) {
            /* Deprecated submodule options are ignored and do not need same level of testing */
            extras.append("      , doGenerateSubmoduleConfigurations: false\n");
            extras.append("      , submoduleCfg: []\n");
        }
        return extras.toString();
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithValidCredentials() throws Exception {
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, credential));
        store.save();

        WorkflowJob p = createProjectWithCredential();
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("using credential " + credential, b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithDifferentCredentials() throws Exception {
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "other"));
        store.save();

        String notOtherCredential = "not-other-" + (100 + random.nextInt(900));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + classPrologue()
                        + "      userRemoteConfigs: [[credentialsId: '" + notOtherCredential + "', url: $/" + sampleRepo + "/$]]\n"
                        + classEpilogue()
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("Warning: CredentialId \"" + notOtherCredential + "\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithInvalidCredentials() throws Exception {
        String systemCredential = "system-credential-" + (100 + random.nextInt(900));
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.SYSTEM, systemCredential));
        store.save();

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + classPrologue()
                        + "      userRemoteConfigs: [[credentialsId: '" + systemCredential + "', url: $/" + sampleRepo + "/$]]\n"
                        + classEpilogue()
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("Warning: CredentialId \"" + systemCredential + "\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithNoCredentialsStoredButUsed() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + classPrologue()
                        + "      userRemoteConfigs: [[credentialsId: '" + credential + "', url: $/" + sampleRepo + "/$]]\n"
                        + classEpilogue()
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("Warning: CredentialId \"" + credential + "\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithNoCredentialsSpecified() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + classPrologue()
                        + "      userRemoteConfigs: [[url: $/" + sampleRepo + "/$]]\n"
                        + classEpilogue()
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("No credentials specified", b);
    }

    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password");
    }
}
