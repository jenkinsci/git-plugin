package hudson.plugins.git;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.util.Arrays;
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

import java.util.Collections;

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
     */
    private String randomPipelineExtensions() {
        /* Valid extensions to apply to a git checkout */
        String [] extensions = {
            "[$class: 'AuthorInChangelog']",
            "[$class: 'ChangelogToBranch', options: [compareRemote: 'origin', compareTarget: 'master']]",
            "[$class: 'CheckoutOption', timeout: 1]",
            "[$class: 'CleanBeforeCheckout']",
            "[$class: 'CleanCheckout']",
            "[$class: 'DisableRemotePoll']",
            "[$class: 'GitLFSPull']",
            "[$class: 'LocalBranch', localBranch: 'master']",
            "[$class: 'PruneStaleBranch']",
            "[$class: 'PruneStaleTag']",
            "[$class: 'WipeWorkspace']",
            "localBranch('master')",
        };
        List<String> extensionList = Arrays.asList(extensions);
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
            "[$class: 'GitLab', repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin', version: '12.10.1']",
            "[$class: 'GitLab', repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin']",
            "[$class: 'GitLabBrowser']",
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
