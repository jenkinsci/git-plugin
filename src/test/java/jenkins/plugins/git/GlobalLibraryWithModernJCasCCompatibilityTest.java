package jenkins.plugins.git;

import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.extensions.impl.CheckoutOption;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import hudson.plugins.git.extensions.impl.UserIdentity;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import jenkins.plugins.git.traits.AuthorInChangelogTrait;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.CheckoutOptionTrait;
import jenkins.plugins.git.traits.CleanAfterCheckoutTrait;
import jenkins.plugins.git.traits.CleanBeforeCheckoutTrait;
import jenkins.plugins.git.traits.CloneOptionTrait;
import jenkins.plugins.git.traits.DiscoverOtherRefsTrait;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.plugins.git.traits.GitLFSPullTrait;
import jenkins.plugins.git.traits.IgnoreOnPushNotificationTrait;
import jenkins.plugins.git.traits.LocalBranchTrait;
import jenkins.plugins.git.traits.PruneStaleBranchTrait;
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import jenkins.plugins.git.traits.RemoteNameSCMSourceTrait;
import jenkins.plugins.git.traits.SubmoduleOptionTrait;
import jenkins.plugins.git.traits.TagDiscoveryTrait;
import jenkins.plugins.git.traits.UserIdentityTrait;
import jenkins.plugins.git.traits.WipeWorkspaceTrait;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.trait.RegexSCMHeadFilterTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GlobalLibraryWithModernJCasCCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        final LibraryConfiguration library = GlobalLibraries.get().getLibraries().get(0);
        assertEquals("My Git Lib", library.getName());
        assertEquals("1.2.3", library.getDefaultVersion());
        assertTrue(library.isImplicit());

        final LibraryRetriever retriever = library.getRetriever();
        assertThat(retriever, instanceOf(SCMSourceRetriever.class));
        final SCMSource scm =  ((SCMSourceRetriever) retriever).getScm();
        assertThat(scm, instanceOf(GitSCMSource.class));
        final GitSCMSource gitSCMSource = (GitSCMSource)scm;

        assertEquals("acmeuser-cred-Id", gitSCMSource.getCredentialsId());
        assertEquals("https://git.acmecorp/myGitLib.git", gitSCMSource.getRemote());

        assertThat(gitSCMSource.getTraits(), hasSize(20));
        assertThat(gitSCMSource.getTraits(), containsInAnyOrder(
                //Discover branches
                allOf(
                        instanceOf(BranchDiscoveryTrait.class)
                ),
                // Discover tags
                allOf(
                        instanceOf(TagDiscoveryTrait.class)
                ),
                // Check out to matching local branch
                allOf(
                        instanceOf(LocalBranchTrait.class)
                ),
                // Clean after checkout
                allOf(
                        instanceOf(CleanAfterCheckoutTrait.class)
                ),
                // Clean before checkout
                allOf(
                        instanceOf(CleanBeforeCheckoutTrait.class)
                ),
                // Git LFS pull after checkout
                allOf(
                        instanceOf(GitLFSPullTrait.class)
                ),
                // Ignore on push notifications
                allOf(
                        instanceOf(IgnoreOnPushNotificationTrait.class)
                ),
                // Prune stale remote-tracking branches
                allOf(
                        instanceOf(PruneStaleBranchTrait.class)
                ),
                // Use commit author in changelog
                allOf(
                        instanceOf(AuthorInChangelogTrait.class)
                ),
                // Wipe out repository & force clone
                allOf(
                        instanceOf(WipeWorkspaceTrait.class)
                ),
                // Discover other refs
                allOf(
                        instanceOf(DiscoverOtherRefsTrait.class),
                        hasProperty("nameMapping", equalTo("mapping")),
                        hasProperty("ref", equalTo("other/refs"))
                ),
                // Filter by name (with regular expression)
                allOf(
                        instanceOf(RegexSCMHeadFilterTrait.class),
                        hasProperty("regex", equalTo(".*acme*"))
                ),
                // Filter by name (with wildcards)
                allOf(
                        instanceOf(WildcardSCMHeadFilterTrait.class),
                        hasProperty("excludes", equalTo("excluded")),
                        hasProperty("includes", equalTo("master"))
                ),
                // Configure remote name
                allOf(
                        instanceOf(RemoteNameSCMSourceTrait.class),
                        hasProperty("remoteName", equalTo("other_remote"))
                ),
                // Advanced checkout behaviours
                allOf(
                        instanceOf(CheckoutOptionTrait.class),
                        hasProperty("extension", instanceOf(CheckoutOption.class)),
                        hasProperty("extension", hasProperty("timeout", equalTo(1)))
                ),
                // Advanced clone behaviours
                allOf(
                        instanceOf(CloneOptionTrait.class),
                        hasProperty("extension", instanceOf(CloneOption.class)),
                        hasProperty("extension", hasProperty("depth", equalTo(2))),
                        hasProperty("extension", hasProperty("honorRefspec", equalTo(true))),
                        hasProperty("extension", hasProperty("noTags", equalTo(false))),
                        hasProperty("extension", hasProperty("reference", equalTo("/my/path/2"))),
                        hasProperty("extension", hasProperty("shallow", equalTo(true))),
                        hasProperty("extension", hasProperty("timeout", equalTo(2)))
                ),
                // Advanced sub-modules behaviours
                allOf(
                        instanceOf(SubmoduleOptionTrait.class),
                        hasProperty("extension", instanceOf(SubmoduleOption.class)),
                        hasProperty("extension", hasProperty("disableSubmodules", equalTo(true))),
                        hasProperty("extension", hasProperty("parentCredentials", equalTo(true))),
                        hasProperty("extension", hasProperty("recursiveSubmodules", equalTo(true))),
                        hasProperty("extension", hasProperty("reference", equalTo("/my/path/3"))),
                        hasProperty("extension", hasProperty("timeout", equalTo(3))),
                        hasProperty("extension", hasProperty("trackingSubmodules", equalTo(true)))
                ),
                // Configure Repository Browser
                allOf(
                        instanceOf(GitBrowserSCMSourceTrait.class),
                        hasProperty("browser", instanceOf(BitbucketWeb.class)),
                        hasProperty("browser", hasProperty("repoUrl", equalTo("bitbucketweb.url")))
                ),
                // Custom user name/e-mail address
                allOf(
                        instanceOf(UserIdentityTrait.class),
                        hasProperty("extension", instanceOf(UserIdentity.class)),
                        hasProperty("extension", hasProperty("name", equalTo("my_user"))),
                        hasProperty("extension", hasProperty("email", equalTo("my@email.com")))
                ),
                // Specify ref specs
                allOf(
                        instanceOf(RefSpecsSCMSourceTrait.class),
                        hasProperty("templates", hasSize(1)),
                        hasProperty("templates", containsInAnyOrder(
                                allOf(
                                        instanceOf(RefSpecsSCMSourceTrait.RefSpecTemplate.class),
                                        hasProperty("value", equalTo("+refs/heads/*:refs/remotes/@{remote}/*"))
                                )
                        ))
                )
        ));
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class jenkins.plugins.git.traits.UserIdentityTrait.extension = {}";
    }

    @Override
    protected String configResource() {
        return "global-with-modern-casc.yaml";
    }
}
