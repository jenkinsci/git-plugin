package jenkins.plugins.git;

import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.AuthorInChangelog;
import hudson.plugins.git.extensions.impl.CheckoutOption;
import hudson.plugins.git.extensions.impl.CleanBeforeCheckout;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.plugins.git.extensions.impl.GitLFSPull;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import hudson.plugins.git.extensions.impl.UserIdentity;
import hudson.plugins.git.extensions.impl.WipeWorkspace;
import java.util.Collections;
import jenkins.model.Jenkins;
import jenkins.plugins.git.traits.AuthorInChangelogTrait;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.CheckoutOptionTrait;
import jenkins.plugins.git.traits.CleanAfterCheckoutTrait;
import jenkins.plugins.git.traits.CleanBeforeCheckoutTrait;
import jenkins.plugins.git.traits.CloneOptionTrait;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.plugins.git.traits.GitLFSPullTrait;
import jenkins.plugins.git.traits.IgnoreOnPushNotificationTrait;
import jenkins.plugins.git.traits.LocalBranchTrait;
import jenkins.plugins.git.traits.PruneStaleBranchTrait;
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import jenkins.plugins.git.traits.RemoteNameSCMSourceTrait;
import jenkins.plugins.git.traits.SubmoduleOptionTrait;
import jenkins.plugins.git.traits.UserIdentityTrait;
import jenkins.plugins.git.traits.WipeWorkspaceTrait;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.hamcrest.Matchers;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class GitSCMSourceTraitsTest {
    /**
     * All tests in this class only use Jenkins for the extensions
     */
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public TestName currentTestName = new TestName();

    private GitSCMSource load() {
        return load(currentTestName.getMethodName());
    }

    private GitSCMSource load(String dataSet) {
        return (GitSCMSource) Jenkins.XSTREAM2.fromXML(
                getClass().getResource(getClass().getSimpleName() + "/" + dataSet + ".xml"));
    }

    @Test
    public void modern() throws Exception {
        GitSCMSource instance = load();
        assertThat(instance.getId(), is("5b061c87-da5c-4d69-b9d5-b041d065c945"));
        assertThat(instance.getRemote(), is("git://git.test/example.git"));
        assertThat(instance.getCredentialsId(), is(nullValue()));
        assertThat(instance.getTraits(), is(Collections.<SCMSourceTrait>emptyList()));
    }

    @Test
    public void pimpped_out() throws Exception {
        GitSCMSource instance = load();
        assertThat(instance.getId(), is("fd2380f8-d34f-48d5-8006-c34542bc4a89"));
        assertThat(instance.getRemote(), is("git://git.test/example.git"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.<SCMSourceTrait>instanceOf(BranchDiscoveryTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(WildcardSCMHeadFilterTrait.class),
                                hasProperty("includes", is("foo/*")),
                                hasProperty("excludes", is("bar/*"))
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(CheckoutOptionTrait.class),
                                hasProperty("extension",
                                        hasProperty("timeout", is(5))
                                )
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(CloneOptionTrait.class),
                                hasProperty("extension",
                                        allOf(
                                                hasProperty("shallow", is(true)),
                                                hasProperty("noTags", is(true)),
                                                hasProperty("reference", is("origin/foo")),
                                                hasProperty("timeout", is(3)),
                                                hasProperty("depth", is(3))
                                        )
                                )
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(SubmoduleOptionTrait.class),
                                hasProperty("extension",
                                        allOf(
                                                hasProperty("disableSubmodules", is(true)),
                                                hasProperty("recursiveSubmodules", is(true)),
                                                hasProperty("trackingSubmodules", is(true)),
                                                hasProperty("reference", is("origin/bar")),
                                                hasProperty("parentCredentials", is(true)),
                                                hasProperty("timeout", is(4)),
                                                hasProperty("shallow", is(true)),
                                                hasProperty("depth", is(3))
                                        )
                                )
                        ),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(LocalBranchTrait.class),
                                hasProperty("extension",
                                        hasProperty("localBranch", is("**"))
                                )
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(CleanAfterCheckoutTrait.class),
                        Matchers.<SCMSourceTrait>instanceOf(CleanBeforeCheckoutTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(UserIdentityTrait.class),
                                hasProperty("extension",
                                        allOf(
                                                hasProperty("name", is("bob")),
                                                hasProperty("email", is("bob@example.com"))
                                        )
                                )
                        ),
                        Matchers.<SCMSourceTrait>instanceOf(GitLFSPullTrait.class),
                        Matchers.<SCMSourceTrait>instanceOf(PruneStaleBranchTrait.class),
                        Matchers.<SCMSourceTrait>instanceOf(IgnoreOnPushNotificationTrait.class),
                        Matchers.<SCMSourceTrait>instanceOf(AuthorInChangelogTrait.class),
                        Matchers.<SCMSourceTrait>instanceOf(WipeWorkspaceTrait.class),
                        Matchers.<SCMSourceTrait>allOf(
                                instanceOf(GitBrowserSCMSourceTrait.class),
                                hasProperty("browser",
                                        allOf(
                                                instanceOf(BitbucketWeb.class),
                                                hasProperty("repoUrl", is("foo"))
                                        )
                                )
                        )
                )
        );
        // Legacy API
        assertThat(instance.getIncludes(), is("foo/*"));
        assertThat(instance.getExcludes(), is("bar/*"));
        assertThat(
                "We have trimmed the extension to only those that are supported on GitSCMSource",
                instance.getExtensions(),
                containsInAnyOrder(
                        Matchers.<GitSCMExtension>allOf(
                                instanceOf(CheckoutOption.class),
                                hasProperty("timeout", is(5))
                        ),
                        Matchers.<GitSCMExtension>allOf(
                                instanceOf(CloneOption.class),
                                hasProperty("shallow", is(true)),
                                hasProperty("noTags", is(true)),
                                hasProperty("reference", is("origin/foo")),
                                hasProperty("timeout", is(3)),
                                hasProperty("depth", is(3))
                        ),
                        Matchers.<GitSCMExtension>allOf(
                                instanceOf(SubmoduleOption.class),
                                hasProperty("disableSubmodules", is(true)),
                                hasProperty("recursiveSubmodules", is(true)),
                                hasProperty("trackingSubmodules", is(true)),
                                hasProperty("reference", is("origin/bar")),
                                hasProperty("parentCredentials", is(true)),
                                hasProperty("timeout", is(4))
                        ),
                        Matchers.<GitSCMExtension>allOf(
                                instanceOf(LocalBranch.class),
                                hasProperty("localBranch", is("**"))
                        ),
                        Matchers.<GitSCMExtension>instanceOf(CleanCheckout.class),
                        Matchers.<GitSCMExtension>instanceOf(CleanBeforeCheckout.class),
                        Matchers.<GitSCMExtension>allOf(
                                instanceOf(UserIdentity.class),
                                hasProperty("name", is("bob")),
                                hasProperty("email", is("bob@example.com"))
                        ),
                        Matchers.<GitSCMExtension>instanceOf(GitLFSPull.class),
                        Matchers.<GitSCMExtension>instanceOf(PruneStaleBranch.class),
                        Matchers.<GitSCMExtension>instanceOf(AuthorInChangelog.class),
                        Matchers.<GitSCMExtension>instanceOf(WipeWorkspace.class)
                )
        );
        assertThat(instance.getBrowser(), allOf(
                instanceOf(BitbucketWeb.class),
                hasProperty("repoUrl", is("foo"))
        ));
        assertThat(instance.isIgnoreOnPushNotifications(), is(true));
        assertThat(instance.getRemoteName(), is("origin"));
        assertThat(instance.getRawRefSpecs(), is("+refs/heads/*:refs/remotes/origin/*"));
    }

    @Test
    public void given__modernCode__when__constructor__then__traitsEmpty() throws Exception {
        assertThat(new GitSCMSource("git://git.test/example.git").getTraits(), is(empty()));
    }

    @Test
    public void given__legacyCode__when__constructor__then__traitsContainLegacyDefaults1() throws Exception {
        GitSCMSource instance = new GitSCMSource("id", "git://git.test/example.git", null, "*", "", false);
        assertThat(instance.getTraits(), contains(
                instanceOf(BranchDiscoveryTrait.class)
        ));
        assertThat(instance.isIgnoreOnPushNotifications(), is(false));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is(""));
        assertThat(instance.getRemoteName(), is("origin"));
        assertThat(instance.getRawRefSpecs(), is("+refs/heads/*:refs/remotes/origin/*"));
    }

    @Test
    public void given__legacyCode__when__constructor__then__traitsContainLegacyDefaults2() throws Exception {
        GitSCMSource instance = new GitSCMSource("id", "git://git.test/example.git", null, "*", "", true);
        assertThat(instance.getTraits(), containsInAnyOrder(
                instanceOf(BranchDiscoveryTrait.class),
                instanceOf(IgnoreOnPushNotificationTrait.class)
        ));
        assertThat(instance.isIgnoreOnPushNotifications(), is(true));
    }

    @Test
    public void given__legacyCode__when__constructor__then__traitsContainLegacyDefaults3() throws Exception {
        GitSCMSource instance = new GitSCMSource("id", "git://git.test/example.git", null, "foo/*", "", false);
        assertThat(instance.getTraits(), contains(
                instanceOf(BranchDiscoveryTrait.class),
                allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("foo/*")),
                        hasProperty("excludes", is(""))
                )
        ));
        assertThat(instance.getIncludes(), is("foo/*"));
        assertThat(instance.getExcludes(), is(""));
    }

    @Test
    public void given__legacyCode__when__constructor__then__traitsContainLegacyDefaults4() throws Exception {
        GitSCMSource instance = new GitSCMSource("id", "git://git.test/example.git", null, "", "foo/*", false);
        assertThat(instance.getTraits(), contains(
                instanceOf(BranchDiscoveryTrait.class),
                allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("includes", is("*")),
                        hasProperty("excludes", is("foo/*"))
                )
        ));
        assertThat(instance.getIncludes(), is("*"));
        assertThat(instance.getExcludes(), is("foo/*"));
    }

    @Test
    public void given__legacyCode__when__constructor__then__traitsContainLegacyDefaults5() throws Exception {
        GitSCMSource instance =
                new GitSCMSource("id", "git://git.test/example.git", null, "upstream", null, "*", "", false);
        assertThat(instance.getTraits(), contains(
                instanceOf(BranchDiscoveryTrait.class),
                allOf(
                        instanceOf(RemoteNameSCMSourceTrait.class),
                        hasProperty("remoteName", is("upstream"))
                )
        ));
        assertThat(instance.getRemoteName(), is("upstream"));
    }

    @Test
    public void given__legacyCode__when__constructor__then__traitsContainLegacyDefaults6() throws Exception {
        GitSCMSource instance =
                new GitSCMSource("id", "git://git.test/example.git", null, null, "refs/pulls/*:refs/upstream/*", "*",
                        "", false);
        assertThat(instance.getTraits(), contains(
                instanceOf(BranchDiscoveryTrait.class),
                allOf(
                        instanceOf(RefSpecsSCMSourceTrait.class),
                        hasProperty("templates", contains(hasProperty("value", is("refs/pulls/*:refs/upstream/*"))))
                )
        ));
        assertThat(instance.getRawRefSpecs(), is("refs/pulls/*:refs/upstream/*"));
    }


}
