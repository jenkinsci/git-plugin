package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.TestGitRepo;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionTest;
import hudson.plugins.git.util.GitUtilsTest;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Test;
import org.jvnet.hudson.test.WithoutJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class UserIdentityTest extends GitSCMExtensionTest  {

    TestGitRepo repo;
    GitClient git;

    @Override
    public void before() {
        // do nothing
    }

    @Override
    protected GitSCMExtension getExtension() {
        return new UserIdentity("Jane Doe", "janeDoe@xyz.com");
    }

    @Test
    public void testUserIdentity() throws Exception {
        repo = new TestGitRepo("repo", tmp.newFolder(), listener);
        git = Git.with(listener, GitUtilsTest.getConfigNoSystemEnvsVars()).in(repo.gitDir).getClient();

        FreeStyleProject projectWithMaster = setupBasicProject(repo);
        git.commit("First commit");
        FreeStyleBuild build = build(projectWithMaster, Result.SUCCESS);
        EnvVars envVars = build.getEnvironment(listener);

        assertThat("Jane Doe", is(envVars.get("GIT_AUTHOR_NAME")));
        assertThat("janeDoe@xyz.com", is(envVars.get("GIT_AUTHOR_EMAIL")));
    }

    @Test
    @WithoutJenkins
    public void testGetNameAndEmail(){
        UserIdentity userIdentity = new UserIdentity("Jane Doe", "janeDoe@xyz.com");

        assertThat("Jane Doe", is(userIdentity.getName()));
        assertThat("janeDoe@xyz.com", is(userIdentity.getEmail()));
    }

    @Test
    @WithoutJenkins
    public void equalsContract() {
        EqualsVerifier.forClass(UserIdentity.class)
                .usingGetClass()
                .verify();
    }
}
