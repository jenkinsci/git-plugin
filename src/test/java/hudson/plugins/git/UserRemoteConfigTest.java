package hudson.plugins.git;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.collect.Sets;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class UserRemoteConfigTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-38048")
    @Test
    public void credentialsDropdown() throws Exception {
        SystemCredentialsProvider.getInstance().getCredentials().add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "mycreds", null, "jenkins", "s3cr3t"));
        SystemCredentialsProvider.getInstance().save();
        FreeStyleProject p1 = r.createFreeStyleProject("p1");
        FreeStyleProject p2 = r.createFreeStyleProject("p2");
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("admin").
            grant(Jenkins.READ, Item.READ).everywhere().to("dev").
            grant(Item.EXTENDED_READ).onItems(p1).to("dev"));
        assertCredentials(p1, null, "dev", "", "mycreds");
        assertCredentials(p2, null, "dev", "");
        assertCredentials(p1, null, "admin", "", "mycreds");
        assertCredentials(p2, null, "admin", "", "mycreds");
        assertCredentials(p1, "othercreds", "dev", "", "mycreds", "othercreds");
        assertCredentials(null, null, "dev", "");
        assertCredentials(null, null, "admin", "", "mycreds");
        assertCredentials(null, "othercreds", "admin", "", "mycreds", "othercreds");
    }
    
    private void assertCredentials(@CheckForNull final Item project, @CheckForNull final String currentCredentialsId, @Nonnull String user, @Nonnull String... expectedCredentialsIds) {
        final Set<String> actual = new TreeSet<>(); // for purposes of this test we do not care about order (though StandardListBoxModel does define some)
        ACL.impersonate(User.get(user).impersonate(), new Runnable() {
            @Override
            public void run() {
                for (ListBoxModel.Option option : r.jenkins.getDescriptorByType(UserRemoteConfig.DescriptorImpl.class).
                        doFillCredentialsIdItems(project, "http://wherever.jenkins.io/", currentCredentialsId)) {
                    actual.add(option.value);
                }
            }
        });
        assertEquals("expected completions on " + project + " as " + user + " starting with " + currentCredentialsId,
                Sets.newTreeSet(Arrays.asList(expectedCredentialsIds)), actual);
    }

}
