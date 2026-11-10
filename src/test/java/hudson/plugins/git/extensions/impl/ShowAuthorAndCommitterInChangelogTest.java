package hudson.plugins.git.extensions.impl;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class ShowAuthorAndCommitterInChangelogTest {

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(ShowAuthorAndCommitterInChangelog.class)
                .usingGetClass()
                .verify();
    }

    @Test
    void checkToString() {
        ShowAuthorAndCommitterInChangelog setting = new ShowAuthorAndCommitterInChangelog();
        assertThat(setting.toString(), is("ShowAuthorAndCommitterInChangelog{}"));
    }

    @Test
    void checkDisplayName() {
        ShowAuthorAndCommitterInChangelog.DescriptorImpl d = new ShowAuthorAndCommitterInChangelog.DescriptorImpl();
        assertThat(d.getDisplayName(), is("Show both author and committer in changelog"));
    }

    @Test
    @WithJenkins
    void configRoundtrip(JenkinsRule r) throws Exception {
        // Given a project with ShowAuthorAndCommitterInChangelog configured
        FreeStyleProject p = r.createFreeStyleProject();
        GitSCM scm = new GitSCM("https://github.com/jenkinsci/git-plugin.git");
        scm.getExtensions().add(new ShowAuthorAndCommitterInChangelog());
        p.setScm(scm);
        p.save();

        // When reloading from disk
        p.doReload();

        // Then the extension survives the round-trip
        GitSCM reloadedScm = (GitSCM) p.getScm();
        assertThat(reloadedScm.getExtensions().toList(), hasItem(instanceOf(ShowAuthorAndCommitterInChangelog.class)));
    }
}
