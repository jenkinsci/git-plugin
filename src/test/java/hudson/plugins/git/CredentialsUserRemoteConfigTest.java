
package hudson.plugins.git;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
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

    private Random random = new Random();

    /* Return randomly selected pipeline checkout configurations.
     * Pipeline assertions in this file are not affected by these assertions.
     * References to invalid classes or invalid keywords will fail the tests.
     */
    private String randomPipelineExtensions() {
        /* Valid extensions to apply to a git checkout */
        String [] extensions = {
            "[$class: 'AuthorInChangelog']",
            "[$class: 'BuildSingleRevisionOnly']",
            "[$class: 'ChangelogToBranch', options: [compareRemote: 'origin', compareTarget: 'master']]",
            "[$class: 'CheckoutOption', timeout: 1]",
            "[$class: 'CleanBeforeCheckout']",
            "[$class: 'CleanCheckout']",
            "[$class: 'DisableRemotePoll']",
            "[$class: 'LocalBranch', localBranch: 'master']",
            "[$class: 'PruneStaleBranch']",
            "[$class: 'PruneStaleTag']",
            "[$class: 'WipeWorkspace']",
            "authorInChangelog()",
            "buildSingleRevisionOnly()",
            "localBranch('master')",
        };
        List<String> extensionList = Arrays.asList(extensions);
        if (sampleRepo.gitVersionAtLeast(1, 9)) {
            // Require at least git 1.9 before testing git large file support
            // Make extensionList mutable
            extensionList = new ArrayList<>(extensionList);
            extensionList.add( "[$class: 'GitLFSPull']");
        }
        Collections.shuffle(extensionList); // Randomize the list of extensions
        int extensionCount = random.nextInt(extensions.length); // How many extensions to add
        if (extensionCount == 0) {
            return "";
        }
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
            "[$class: 'CGit', repoUrl: 'https://git.zx2c4.com/cgit'],",
            "[$class: 'FisheyeGitRepositoryBrowser', repoUrl: 'https://fisheye.apache.org/browse/ant-git']",
            "[$class: 'GitBlit', repoUrl: 'https://github.com/MarkEWaite/git-client-plugin']",
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
            "assemblaWeb(repoUrl: 'https://app.assembla.com/spaces/git-plugin/git/source')",
            "bitbucketWeb(repoUrl: 'https://markewaite@bitbucket.org/markewaite/git-plugin')",
            "cgit(repoUrl: 'https://git.zx2c4.com/cgit')",
            "fisheye(repoUrl: 'https://fisheye.apache.org/browse/ant-git')",
            "gitBlit(repoUrl: 'https://github.com/MarkEWaite/git-client-plugin')",
            "gitLab(repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin', version: '12.10.1')",
            "gitList(repoUrl: 'http://gitlist.org/') ", // Not a real gitlist site, just the org home page
            "gitWeb(repoUrl: 'https://git.ti.com/gitweb')",
            "githubWeb(repoUrl: 'https://github.com/jenkinsci/git-plugin')",
            "gitiles(repoUrl: 'https://gerrit.googlesource.com/gitiles/')",
            "gitoriousWeb(repoUrl: 'https://gerrit.googlesource.com/gitiles/')",
            "gogs(repoUrl: 'https://try.gogs.io/MarkEWaite/git-plugin')", // Should this be gogsGit?
            "kilnGit(repoUrl: 'https://kiln.example.com/MarkEWaite/git-plugin')",
            "microsoftTFS(repoUrl: 'https://markwaite.visualstudio.com/DefaultCollection/git-plugin/_git/git-plugin')",
            "phabricator(repo: 'source/tool-spacemedia', repoUrl: 'https://phabricator.wikimedia.org/source/tool-spacemedia/')",
            "redmineWeb(repoUrl: 'https://www.redmine.org/projects/redmine/repository')",
            "rhodeCode(repoUrl: 'https://code.rhodecode.com/rhodecode-enterprise-ce')",
            "viewGit(repoUrl: 'https://git.ti.com/gitweb')", // Not likely a viewGit site, but reasonable approximation
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
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "github"));
        store.save();

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', \n"
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]\n"
                        + randomPipelineCheckoutExtras()
                        + "    ]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("using credential github", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithDifferentCredentials() throws Exception {
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.GLOBAL, "other"));
        store.save();

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', \n"
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]\n"
                        + randomPipelineCheckoutExtras()
                        + "    ]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("Warning: CredentialId \"github\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithInvalidCredentials() throws Exception {
        sampleRepo.init();
        store.addCredentials(Domain.global(), createCredential(CredentialsScope.SYSTEM, "github"));
        store.save();

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', \n"
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]\n"
                        + randomPipelineCheckoutExtras()
                        + "    ]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("Warning: CredentialId \"github\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithNoCredentialsStoredButUsed() throws Exception {
        sampleRepo.init();

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', \n"
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]\n"
                        + randomPipelineCheckoutExtras()
                        + "    ]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("Warning: CredentialId \"github\" could not be found", b);
    }

    @Issue("JENKINS-30515")
    @Test
    public void checkoutWithNoCredentialsSpecified() throws Exception {
        sampleRepo.init();

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n"
                        + "  checkout(\n"
                        + "    [$class: 'GitSCM', \n"
                        + "      userRemoteConfigs: [[url: $/" + sampleRepo + "/$]]\n"
                        + randomPipelineCheckoutExtras()
                        + "    ]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.waitForMessage("No credentials specified", b);
    }


    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password");
    }
}
