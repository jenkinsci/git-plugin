package jenkins.plugins.git;

import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.extensions.impl.AuthorInChangelog;
import hudson.plugins.git.extensions.impl.CheckoutOption;
import hudson.plugins.git.extensions.impl.CleanBeforeCheckout;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.plugins.git.extensions.impl.GitLFSPull;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.extensions.impl.SparseCheckoutPaths;
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
import jenkins.plugins.git.traits.SparseCheckoutPathsTrait;
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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

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
    public void cleancheckout_v1_extension() {
        verifyCleanCheckoutTraits(false);
    }

    @Test
    public void cleancheckout_v1_trait() {
        verifyCleanCheckoutTraits(false);
    }

    @Test
    public void cleancheckout_v2_extension() {
        verifyCleanCheckoutTraits(true);
    }

    @Test
    public void cleancheckout_v2_trait() {
        verifyCleanCheckoutTraits(true);
    }

    /**
     * Tests loading of {@link CleanCheckout}/{@link CleanBeforeCheckout}.
     */
    private void verifyCleanCheckoutTraits(boolean deleteUntrackedNestedRepositories) {
        GitSCMSource instance = load();

        assertThat(instance.getTraits(),
                hasItems(
                        allOf(
                                instanceOf(CleanBeforeCheckoutTrait.class),
                                hasProperty("extension",
                                        hasProperty(
                                                "deleteUntrackedNestedRepositories",
                                                is(deleteUntrackedNestedRepositories)
                                        )
                                )
                        ),
                        allOf(
                                instanceOf(CleanAfterCheckoutTrait.class),
                                hasProperty("extension",
                                        hasProperty(
                                                "deleteUntrackedNestedRepositories",
                                                is(deleteUntrackedNestedRepositories)
                                        )
                                )
                        )
                )
        );
    }

    @Test
    @Deprecated // Includes tests of deprecated methods getIncludes, getExcludes, & getRawRefSpecs
    public void pimpped_out() throws Exception {
        GitSCMSource instance = load();
        assertThat(instance.getId(), is("fd2380f8-d34f-48d5-8006-c34542bc4a89"));
        assertThat(instance.getRemote(), is("git://git.test/example.git"));
        assertThat(instance.getCredentialsId(), is("e4d8c11a-0d24-472f-b86b-4b017c160e9a"));
        assertThat(instance.getTraits(),
                containsInAnyOrder(
                        Matchers.instanceOf(BranchDiscoveryTrait.class),
                        Matchers.allOf(
                                instanceOf(WildcardSCMHeadFilterTrait.class),
                                hasProperty("includes", is("foo/*")),
                                hasProperty("excludes", is("bar/*"))
                        ),
                        Matchers.allOf(
                                instanceOf(CheckoutOptionTrait.class),
                                hasProperty("extension",
                                        hasProperty("timeout", is(5))
                                )
                        ),
                        Matchers.allOf(
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
                        Matchers.allOf(
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
                                                hasProperty("depth", is(3)),
                                                hasProperty("threads", is(4))
                                        )
                                )
                        ),
                        Matchers.allOf(
                                instanceOf(LocalBranchTrait.class),
                                hasProperty("extension",
                                        hasProperty("localBranch", is("**"))
                                )
                        ),
                        Matchers.allOf(
                                instanceOf(CleanBeforeCheckoutTrait.class),
                                hasProperty("extension",
                                        hasProperty("deleteUntrackedNestedRepositories", is(true))
                                )
                        ),
                        Matchers.allOf(
                                instanceOf(CleanAfterCheckoutTrait.class),
                                hasProperty("extension",
                                        hasProperty("deleteUntrackedNestedRepositories", is(true))
                                )
                        ),
                        Matchers.allOf(
                                instanceOf(UserIdentityTrait.class),
                                hasProperty("extension",
                                        allOf(
                                                hasProperty("name", is("bob")),
                                                hasProperty("email", is("bob@example.com"))
                                        )
                                )
                        ),
                        Matchers.instanceOf(GitLFSPullTrait.class),
                        Matchers.instanceOf(PruneStaleBranchTrait.class),
                        Matchers.instanceOf(IgnoreOnPushNotificationTrait.class),
                        Matchers.instanceOf(AuthorInChangelogTrait.class),
                        Matchers.instanceOf(WipeWorkspaceTrait.class),
                        Matchers.allOf(
                                instanceOf(GitBrowserSCMSourceTrait.class),
                                hasProperty("browser",
                                        allOf(
                                                instanceOf(BitbucketWeb.class),
                                                hasProperty("repoUrl", is("foo"))
                                        )
                                )
                        ),
                        Matchers.allOf(
                                instanceOf(SparseCheckoutPathsTrait.class),
                                hasProperty("extension",
                                        allOf(
                                                hasProperty("sparseCheckoutPaths", hasSize(2))
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
                        Matchers.allOf(
                                instanceOf(CheckoutOption.class),
                                hasProperty("timeout", is(5))
                        ),
                        Matchers.allOf(
                                instanceOf(CloneOption.class),
                                hasProperty("shallow", is(true)),
                                hasProperty("noTags", is(true)),
                                hasProperty("reference", is("origin/foo")),
                                hasProperty("timeout", is(3)),
                                hasProperty("depth", is(3))
                        ),
                        Matchers.allOf(
                                instanceOf(SubmoduleOption.class),
                                hasProperty("disableSubmodules", is(true)),
                                hasProperty("recursiveSubmodules", is(true)),
                                hasProperty("trackingSubmodules", is(true)),
                                hasProperty("reference", is("origin/bar")),
                                hasProperty("parentCredentials", is(true)),
                                hasProperty("timeout", is(4))
                        ),
                        Matchers.allOf(
                                instanceOf(LocalBranch.class),
                                hasProperty("localBranch", is("**"))
                        ),
                        Matchers.instanceOf(CleanCheckout.class),
                        Matchers.instanceOf(CleanBeforeCheckout.class),
                        Matchers.allOf(
                                instanceOf(UserIdentity.class),
                                hasProperty("name", is("bob")),
                                hasProperty("email", is("bob@example.com"))
                        ),
                        Matchers.instanceOf(GitLFSPull.class),
                        Matchers.instanceOf(PruneStaleBranch.class),
                        Matchers.instanceOf(AuthorInChangelog.class),
                        Matchers.instanceOf(WipeWorkspace.class),
                        Matchers.allOf(
                                instanceOf(SparseCheckoutPaths.class),
                                hasProperty("sparseCheckoutPaths", hasSize(2))
                        )
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
    @Deprecated // Testing deprecated GitSCMSource constructor
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
    @Deprecated // Testing deprecated GitSCMSource constructor
    public void given__legacyCode__when__constructor__then__traitsContainLegacyDefaults2() throws Exception {
        GitSCMSource instance = new GitSCMSource("id", "git://git.test/example.git", null, "*", "", true);
        assertThat(instance.getTraits(), containsInAnyOrder(
                instanceOf(BranchDiscoveryTrait.class),
                instanceOf(IgnoreOnPushNotificationTrait.class)
        ));
        assertThat(instance.isIgnoreOnPushNotifications(), is(true));
    }

    @Test
    @Deprecated // Testing deprecated GitSCMSource constructor
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
    @Deprecated // Testing deprecated GitSCMSource constructor
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
    @Deprecated // Testing deprecated GitSCMSource constructor
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
    @Deprecated // Testing deprecated GitSCMSource constructor
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
