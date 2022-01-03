package hudson.plugins.git;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
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
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
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
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
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
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
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
                        + "      userRemoteConfigs: [[credentialsId: 'github', url: $/" + sampleRepo + "/$]]]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
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
                        + "      userRemoteConfigs: [[url: $/" + sampleRepo + "/$]]]\n"
                        + "  )"
                        + "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.waitForMessage("No credentials specified", b);
    }


    private StandardCredentials createCredential(CredentialsScope scope, String id) {
        return new UsernamePasswordCredentialsImpl(scope, id, "desc: " + id, "username", "password");
    }
}
