package hudson.plugins.git;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSampleRepoRule;
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

public class CredentialsUserRemoteConfigTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private CredentialsStore store = null;
    private boolean useSymbolForGitSCM = true;
    private Random random = new Random();
    private String credential = "undefined";

    @Before
    public void enableSystemCredentialsProvider() {
        SystemCredentialsProvider.getInstance().setDomainCredentialsMap(
                Collections.singletonMap(Domain.global(), Collections.<Credentials>emptyList()));
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
        /* Use the 'gitSCM' symbol instead of '$class: GitSCM */
        useSymbolForGitSCM = random.nextBoolean();
    }

    @Before
    public void generateCredentialID() {
        credential = "credential-id-" + (100 + random.nextInt(900));
    }

    private String classProlog() {
        if (useSymbolForGitSCM) {
            return "    gitSCM(\n";
        }
        return "    [$class: 'GitSCM', \n";
    }

    private String classEpilog() {
        if (useSymbolForGitSCM) {
            return "    )\n";
        }
        return "    ]\n";
    }

    private WorkflowJob createProject() throws IOException {
        return createProject(true);
    }

    private WorkflowJob createProject(boolean useCredential) throws IOException {
        String credentialsPhrase = useCredential ? "credentialsId: '" + credential + "', " : "";
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + classProlog()
                        + "      userRemoteConfigs: [[" + credentialsPhrase + "url: $/" + sampleRepo + "/$]]\n"
                        + randomPipelineCheckoutExtras()
                        + classEpilog()
                        + "  )"
                        + "}", true));
        return p;
    }

    /* Return randomly selected pipeline checkout configurations.
     * Pipeline assertions in this file are not affected by these assertions.
     * References to invalid classes or invalid keywords will fail the tests.
     */
    private String randomPipelineExtensions() {
        /* Valid extensions to apply to a git checkout */
        String [] extensions = {
            // "[$class: 'BuildChooserSetting', buildChooser: [$class: 'AncestryBuildChooser', ancestorCommitSha1: 'feedbeefbeadcededeedabed', maximumAgeInDays: 23]]",
            // "[$class: 'BuildChooserSetting', buildChooser: [$class: 'InverseBuildChooser']]",
            // "[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: 'src'], [path: 'Makefile']]]",
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
            "[$class: 'SubmoduleOption', depth: 17, disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '/cache/git1.git', shallow: true, threads: 13, timeout: 11, trackingSubmodules: true]",
            "[$class: 'UserIdentity', email: 'custom.user.email@example.com', name: 'Custom User Name']",
            "[$class: 'WipeWorkspace']",
            "authorInChangelog()",
            "buildSingleRevisionOnly()",
            "changelogToBranch(changelogToBranchOptions(compareRemote: 'origin', compareTarget: 'master'))",
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
        };
        List<String> extensionList = Arrays.asList(extensions);
        if (sampleRepo.hasGitLFS()) {
            // Do not test git LFS unless it is installed
            // Make extensionList mutable
            extensionList = new ArrayList<>(extensionList);
            extensionList.add("[$class: 'GitLFSPull']");
            extensionList.add("gitLFSPull()");
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
    private String randomPipelineCheckoutExtras() {
        String[] browsers = {
            "",
            "[$class: 'AssemblaWeb', repoUrl: 'https://app.assembla.com/spaces/git-plugin/git/source']",
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
            "assemblaWeb('https://app.assembla.com/spaces/git-plugin/git/source')",
            "bitbucketWeb('https://markewaite@bitbucket.org/markewaite/git-plugin')",
            "cgit('https://git.zx2c4.com/cgit')",
            "fisheye('https://fisheye.apache.org/browse/ant-git')",
            "gitBlitRepositoryBrowser(repoUrl: 'https://github.com/MarkEWaite/git-client-plugin', projectName: 'git-plugin-project-name-value')",
            "gitLabBrowser(repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin', version: '12.10.1')",
            "gitList('http://gitlist.org/')", // Not a real gitlist site, just the org home page
            "gitWeb('https://git.ti.com/gitweb')",
            "githubWeb('https://github.com/jenkinsci/git-plugin')",
            "gitiles('https://gerrit.googlesource.com/gitiles/')",
            "gitoriousWeb('https://gerrit.googlesource.com/gitiles/')",
            "gogs('https://try.gogs.io/MarkEWaite/git-plugin')", // Should this be gogsGit?
            "kiln('https://kiln.example.com/MarkEWaite/git-plugin')",
            "microsoftTFS('https://markwaite.visualstudio.com/DefaultCollection/git-plugin/_git/git-plugin')",
            "phabricator(repo: 'source/tool-spacemedia', repoUrl: 'https://phabricator.wikimedia.org/source/tool-spacemedia/')",
            "redmineWeb('https://www.redmine.org/projects/redmine/repository')",
            "rhodeCode('https://code.rhodecode.com/rhodecode-enterprise-ce')",
            "viewGit(repoUrl: 'https://repo.or.cz/viewgit.git', projectName: 'viewgit-project-name-value')", // Not likely a viewGit site, but reasonable approximation
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
        extras.append(randomPipelineExtensions());
        if (random.nextBoolean()) {
            extras.append("      , ");
            extras.append("doGenerateSubmoduleConfigurations: ");
            extras.append(random.nextBoolean() ? "true" : "false");
            extras.append('\n');
        }
        return extras.toString();
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithValidCredentials() throws Exception {
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, credential));
        store.save();

        WorkflowJob p = createProject();
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("using credential " + credential, b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithDifferentCredentials() throws Exception {
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "other"));
        store.save();

        WorkflowJob p = createProject();
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("Warning: CredentialId \"" + credential + "\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithInvalidCredentials() throws Exception {
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.SYSTEM, "github"));
        store.save();

        WorkflowJob p = createProject();
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("Warning: CredentialId \"" + credential + "\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithNoCredentialsStoredButUsed() throws Exception {
        sampleRepo.init();

        WorkflowJob p = createProject();
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("Warning: CredentialId \"" + credential + "\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithNoCredentialsSpecified() throws Exception {
        sampleRepo.init();

        WorkflowJob p = createProject(false);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("No credentials specified", b);
    }


    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password");
    }
}
