package jenkins.plugins.git;

import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GithubWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.AuthorInChangelog;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.util.InverseBuildChooser;
import java.util.Collections;
import jenkins.scm.api.SCMHead;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

public class GitSCMBuilderTest {

    private GitSCMBuilder<?> instance = new GitSCMBuilder<>(
            new SCMHead("master"),
            null,
            "http://git.test/repo.git",
            null);

    @Test
    public void build() throws Exception {
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withRevision() throws Exception {
        instance.withExtension(new BuildChooserSetting(new InverseBuildChooser()));
        GitSCM scm = instance.build();
        assertThat(scm.getExtensions().get(BuildChooserSetting.class), notNullValue());
        assertThat(scm.getExtensions().get(BuildChooserSetting.class).getBuildChooser(),
                instanceOf(InverseBuildChooser.class));
        instance.withRevision(
                new AbstractGitSCMSource.SCMRevisionImpl(instance.head(), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions().get(BuildChooserSetting.class), notNullValue());
        assertThat(scm.getExtensions().get(BuildChooserSetting.class).getBuildChooser(),
                instanceOf(AbstractGitSCMSource.SpecificRevisionBuildChooser.class));
        assertThat(scm.getExtensions().get(BuildChooserSetting.class).getBuildChooser()
                .getCandidateRevisions(false, null, (GitClient) null, null, null, null).iterator().next()
                .getSha1String(), is("3f0b897057d8b43d3b9ff55e3fdefbb021493470"));
    }

    @Test
    public void withBrowser() throws Exception {
        instance.withBrowser(new GithubWeb("http://git.test/repo.git"));
        assertThat(instance.browser(), is(instanceOf(GithubWeb.class)));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(instanceOf(GithubWeb.class)));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withCredentials() throws Exception {
        instance.withCredentials("example-id");
        assertThat(instance.credentialsId(), is("example-id"));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is("example-id")))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withExtension() throws Exception {
        instance.withExtension(new AuthorInChangelog());
        assertThat(instance.extensions(), contains(instanceOf(AuthorInChangelog.class)));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), contains(instanceOf(AuthorInChangelog.class)));

        // repeated calls build up new extensions
        instance.withExtension(new LocalBranch("**"));
        assertThat(instance.extensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                allOf(instanceOf(LocalBranch.class), hasProperty("localBranch", is("**")))
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                allOf(instanceOf(LocalBranch.class), hasProperty("localBranch", is("**")))
        ));

        // repeated calls re-define up existing extensions
        instance.withExtension(new LocalBranch("master"));
        assertThat(instance.extensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                allOf(instanceOf(LocalBranch.class), hasProperty("localBranch", is("master")))
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                allOf(instanceOf(LocalBranch.class), hasProperty("localBranch", is("master")))
        ));
    }

    @Test
    public void withExtensions() throws Exception {
        instance.withExtensions(new AuthorInChangelog());
        assertThat(instance.extensions(), contains(instanceOf(AuthorInChangelog.class)));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), contains(instanceOf(AuthorInChangelog.class)));

        // repeated calls build up extensions
        instance.withExtensions(new CleanCheckout());
        assertThat(instance.extensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                instanceOf(CleanCheckout.class)
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                instanceOf(CleanCheckout.class)
        ));

        instance.withExtension(new LocalBranch("**"));
        assertThat(instance.extensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                instanceOf(CleanCheckout.class),
                allOf(instanceOf(LocalBranch.class), hasProperty("localBranch", is("**")))
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                instanceOf(CleanCheckout.class),
                allOf(instanceOf(LocalBranch.class), hasProperty("localBranch", is("**")))
        ));

        // repeated calls re-define up existing extensions
        instance.withExtension(new LocalBranch("master"));
        assertThat(instance.extensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                instanceOf(CleanCheckout.class),
                allOf(instanceOf(LocalBranch.class), hasProperty("localBranch", is("master")))
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), containsInAnyOrder(
                instanceOf(AuthorInChangelog.class),
                instanceOf(CleanCheckout.class),
                allOf(instanceOf(LocalBranch.class), hasProperty("localBranch", is("master")))
        ));
    }

    @Test
    public void withGitTool() throws Exception {
        instance.withGitTool("git");
        assertThat(instance.gitTool(), is("git"));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is("git"));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withRefSpec() throws Exception {
        instance.withRefSpec("+refs/heads/master:refs/remotes/@{remote}/master");
        assertThat(instance.refSpecs(), contains("+refs/heads/master:refs/remotes/@{remote}/master"));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/master:refs/remotes/origin/master")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));

        // repeated calls build up
        instance.withRefSpec("+refs/heads/feature:refs/remotes/@{remote}/feature");
        assertThat(instance.refSpecs(), containsInAnyOrder(
                "+refs/heads/master:refs/remotes/@{remote}/master",
                "+refs/heads/feature:refs/remotes/@{remote}/feature"
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), containsInAnyOrder(
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/master:refs/remotes/origin/master")),
                        hasProperty("credentialsId", is(nullValue()))
                ),
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/feature:refs/remotes/origin/feature")),
                        hasProperty("credentialsId", is(nullValue()))
                )
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));

        // repeated calls build up but remote configs de-duplicated
        instance.withRefSpec("+refs/heads/master:refs/remotes/@{remote}/master");
        assertThat(instance.refSpecs(), containsInAnyOrder(
                "+refs/heads/master:refs/remotes/@{remote}/master",
                "+refs/heads/feature:refs/remotes/@{remote}/feature",
                "+refs/heads/master:refs/remotes/@{remote}/master"
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), containsInAnyOrder(
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/master:refs/remotes/origin/master")),
                        hasProperty("credentialsId", is(nullValue()))
                ),
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/feature:refs/remotes/origin/feature")),
                        hasProperty("credentialsId", is(nullValue()))
                )
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));

        // de-duplication is applied after template substitution
        instance.withRefSpec("+refs/heads/master:refs/remotes/origin/master");
        assertThat(instance.refSpecs(), containsInAnyOrder(
                "+refs/heads/master:refs/remotes/@{remote}/master",
                "+refs/heads/feature:refs/remotes/@{remote}/feature",
                "+refs/heads/master:refs/remotes/@{remote}/master",
                "+refs/heads/master:refs/remotes/origin/master"
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), containsInAnyOrder(
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/master:refs/remotes/origin/master")),
                        hasProperty("credentialsId", is(nullValue()))
                ),
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/feature:refs/remotes/origin/feature")),
                        hasProperty("credentialsId", is(nullValue()))
                )
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withRefSpecs() throws Exception {
        instance.withRefSpecs(Collections.singletonList("+refs/heads/master:refs/remotes/@{remote}/master"));
        assertThat(instance.refSpecs(), contains("+refs/heads/master:refs/remotes/@{remote}/master"));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/master:refs/remotes/origin/master")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));

        // repeated calls accumulate
        instance.withRefSpecs(Collections.singletonList("+refs/heads/feature:refs/remotes/@{remote}/feature"));
        assertThat(instance.refSpecs(), contains(
                "+refs/heads/master:refs/remotes/@{remote}/master",
                "+refs/heads/feature:refs/remotes/@{remote}/feature"
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/master:refs/remotes/origin/master")),
                        hasProperty("credentialsId", is(nullValue()))
                ),
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/feature:refs/remotes/origin/feature")),
                        hasProperty("credentialsId", is(nullValue()))
                )
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));

        // empty list is no-op
        instance.withRefSpecs(Collections.<String>emptyList());
        assertThat(instance.refSpecs(), contains(
                "+refs/heads/master:refs/remotes/@{remote}/master",
                "+refs/heads/feature:refs/remotes/@{remote}/feature"
        ));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/master:refs/remotes/origin/master")),
                        hasProperty("credentialsId", is(nullValue()))
                ),
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/feature:refs/remotes/origin/feature")),
                        hasProperty("credentialsId", is(nullValue()))
                )
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withoutRefSpecs() throws Exception {
        instance.withRefSpecs(Collections.singletonList("+refs/heads/feature:refs/remotes/@{remote}/feature"));
        assumeThat(instance.refSpecs(), not(contains(
                "+refs/heads/*:refs/remotes/@{remote}/*"
        )));
        instance.withoutRefSpecs();
        assertThat(instance.refSpecs(), contains("+refs/heads/*:refs/remotes/@{remote}/*"));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withRemote() throws Exception {
        instance.withRemote("http://git.test/my-repo.git");
        assertThat(instance.remote(), is("http://git.test/my-repo.git"));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/my-repo.git")),
                hasProperty("name", is("origin")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withRemoteName() throws Exception {
        instance.withRemoteName("my-remote");
        assertThat(instance.remoteName(), is("my-remote"));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), contains(allOf(
                instanceOf(UserRemoteConfig.class),
                hasProperty("url", is("http://git.test/repo.git")),
                hasProperty("name", is("my-remote")),
                hasProperty("refspec", is("+refs/heads/*:refs/remotes/my-remote/*")),
                hasProperty("credentialsId", is(nullValue())))
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));
    }

    @Test
    public void withAdditionalRemote() throws Exception {
        instance.withAdditionalRemote("upstream", "http://git.test/upstream.git",
                "+refs/heads/master:refs/remotes/@{remote}/master");
        assertThat(instance.additionalRemoteNames(), contains("upstream"));
        assertThat(instance.additionalRemote("upstream"), is("http://git.test/upstream.git"));
        assertThat(instance.additionalRemoteRefSpecs("upstream"), contains(
                "+refs/heads/master:refs/remotes/@{remote}/master"));
        GitSCM scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), containsInAnyOrder(
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                        hasProperty("credentialsId", is(nullValue()))
                ),
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/upstream.git")),
                        hasProperty("name", is("upstream")),
                        hasProperty("refspec", is("+refs/heads/master:refs/remotes/upstream/master")),
                        hasProperty("credentialsId", is(nullValue()))
                )
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));

        instance.withAdditionalRemote("production", "http://git.test/production.git");
        assertThat(instance.additionalRemoteNames(), containsInAnyOrder("upstream", "production"));
        assertThat(instance.additionalRemote("upstream"), is("http://git.test/upstream.git"));
        assertThat(instance.additionalRemoteRefSpecs("upstream"), contains(
                "+refs/heads/master:refs/remotes/@{remote}/master"));
        assertThat(instance.additionalRemote("production"), is("http://git.test/production.git"));
        assertThat(instance.additionalRemoteRefSpecs("production"), contains(
                "+refs/heads/*:refs/remotes/@{remote}/*"));
        scm = instance.build();
        assertThat(scm.getBrowser(), is(nullValue()));
        assertThat(scm.getUserRemoteConfigs(), containsInAnyOrder(
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/repo.git")),
                        hasProperty("name", is("origin")),
                        hasProperty("refspec", is("+refs/heads/*:refs/remotes/origin/*")),
                        hasProperty("credentialsId", is(nullValue()))
                ),
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/upstream.git")),
                        hasProperty("name", is("upstream")),
                        hasProperty("refspec", is("+refs/heads/master:refs/remotes/upstream/master")),
                        hasProperty("credentialsId", is(nullValue()))
                ),
                allOf(
                        instanceOf(UserRemoteConfig.class),
                        hasProperty("url", is("http://git.test/production.git")),
                        hasProperty("name", is("production")),
                        hasProperty("refspec", is("+refs/heads/*:refs/remotes/production/*")),
                        hasProperty("credentialsId", is(nullValue()))
                )
        ));
        assertThat(scm.getGitTool(), is(nullValue()));
        assertThat(scm.getExtensions(), is(Collections.<GitSCMExtension>emptyList()));

    }

}
