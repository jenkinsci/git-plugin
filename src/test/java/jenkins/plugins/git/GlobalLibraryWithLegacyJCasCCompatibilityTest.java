package jenkins.plugins.git;

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.ChangelogToBranchOptions;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.AssemblaWeb;
import hudson.plugins.git.extensions.impl.AuthorInChangelog;
import hudson.plugins.git.extensions.impl.ChangelogToBranch;
import hudson.plugins.git.extensions.impl.CheckoutOption;
import hudson.plugins.git.extensions.impl.CleanBeforeCheckout;
import hudson.plugins.git.extensions.impl.CleanCheckout;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.plugins.git.extensions.impl.DisableRemotePoll;
import hudson.plugins.git.extensions.impl.GitLFSPull;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.plugins.git.extensions.impl.MessageExclusion;
import hudson.plugins.git.extensions.impl.PathRestriction;
import hudson.plugins.git.extensions.impl.PerBuildTag;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import hudson.plugins.git.extensions.impl.PruneStaleBranch;
import hudson.plugins.git.extensions.impl.RelativeTargetDirectory;
import hudson.plugins.git.extensions.impl.ScmName;
import hudson.plugins.git.extensions.impl.SparseCheckoutPath;
import hudson.plugins.git.extensions.impl.SparseCheckoutPaths;
import hudson.plugins.git.extensions.impl.SubmoduleOption;
import hudson.plugins.git.extensions.impl.UserExclusion;
import hudson.plugins.git.extensions.impl.UserIdentity;
import hudson.plugins.git.extensions.impl.WipeWorkspace;
import hudson.scm.SCM;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryRetriever;
import org.jenkinsci.plugins.workflow.libs.SCMRetriever;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class GlobalLibraryWithLegacyJCasCCompatibilityTest extends RoundTripAbstractTest {
    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        final LibraryConfiguration library = GlobalLibraries.get().getLibraries().get(0);
        assertEquals("My Git Lib", library.getName());
        assertEquals("1.2.3", library.getDefaultVersion());
        assertTrue(library.isImplicit());

        final LibraryRetriever retriever = library.getRetriever();
        assertThat(retriever, instanceOf(SCMRetriever.class));
        final SCM scm =  ((SCMRetriever) retriever).getScm();
        assertThat(scm, instanceOf(GitSCM.class));
        final GitSCM gitSCM = (GitSCM)scm;

        assertThat(gitSCM.getUserRemoteConfigs(), hasSize(1));
        final UserRemoteConfig userRemoteConfig = gitSCM.getUserRemoteConfigs().get(0);
        assertEquals("acmeuser-cred-Id", userRemoteConfig.getCredentialsId());
        assertEquals("field_name", userRemoteConfig.getName());
        assertEquals("field_refspec", userRemoteConfig.getRefspec());
        assertEquals("https://git.acmecorp/myGitLib.git", userRemoteConfig.getUrl());

        assertThat(gitSCM.getBranches(), hasSize(2));
        assertThat(gitSCM.getBranches(), containsInAnyOrder(
                allOf(instanceOf(BranchSpec.class),
                        hasProperty("name", equalTo("master"))),
                allOf(instanceOf(BranchSpec.class),
                        hasProperty("name", equalTo("myprodbranch")))
        ));

        assertThat(gitSCM.getBrowser(), instanceOf(AssemblaWeb.class));
        assertEquals("assemblaweb.url", gitSCM.getBrowser().getRepoUrl());

        assertFalse(gitSCM.isDoGenerateSubmoduleConfigurations());

        assertThat(gitSCM.getExtensions(), hasSize(22));
        assertThat(gitSCM.getExtensions(), containsInAnyOrder(
                // Advanced checkout behaviours
                allOf(
                        instanceOf(CheckoutOption.class),
                        hasProperty("timeout", equalTo(1))
                ),
                // Advanced clone behaviours
                allOf(
                        instanceOf(CloneOption.class),
                        hasProperty("shallow", equalTo(true)),
                        hasProperty("noTags", equalTo(false)),
                        hasProperty("reference", equalTo("/my/path/2")),
                        hasProperty("timeout", equalTo(2)),
                        hasProperty("depth", equalTo(2)),
                        hasProperty("honorRefspec", equalTo(true))
                ),
                // Advanced sub-modules behaviours
                allOf(
                        instanceOf(SubmoduleOption.class),
                        hasProperty("disableSubmodules", equalTo(true)),
                        hasProperty("parentCredentials", equalTo(true)),
                        hasProperty("recursiveSubmodules", equalTo(true)),
                        hasProperty("reference", equalTo("/my/path/3")),
                        hasProperty("timeout", equalTo(3)),
                        hasProperty("trackingSubmodules", equalTo(true))
                ),
                // Calculate changelog against a specific branch
                allOf(
                        instanceOf(ChangelogToBranch.class),
                        hasProperty("options", instanceOf(ChangelogToBranchOptions.class)),
                        hasProperty("options", hasProperty("compareRemote", equalTo("myrepo"))),
                        hasProperty("options", hasProperty("compareTarget", equalTo("mybranch")))
                ),
                // Check out to a sub-directory
                allOf(
                        instanceOf(RelativeTargetDirectory.class),
                        hasProperty("relativeTargetDir", equalTo("/my/path/5"))
                ),
                // Check out to specific local branch
                allOf(
                        instanceOf(LocalBranch.class),
                        hasProperty("localBranch", equalTo("local_branch"))
                ),
                // Clean after checkout
                allOf(
                        instanceOf(CleanCheckout.class)
                ),
                // Clean before checkout
                allOf(
                        instanceOf(CleanBeforeCheckout.class)
                ),
                // Create a tag for every build
                allOf(
                        instanceOf(PerBuildTag.class)
                ),
                // Don't trigger a build on commit notifications
                allOf(
                        instanceOf(IgnoreNotifyCommit.class)
                ),
                // Force polling using workspace
                allOf(
                        instanceOf(DisableRemotePoll.class)
                ),
                // Git LFS pull after checkout
                allOf(
                        instanceOf(GitLFSPull.class)
                ),
                // Prune stale remote-tracking branches
                allOf(
                        instanceOf(PruneStaleBranch.class)
                ),
                // Use commit author in changelog
                allOf(
                        instanceOf(AuthorInChangelog.class)
                ),
                // Wipe out repository & force clone
                allOf(
                        instanceOf(WipeWorkspace.class)
                ),
                // Custom SCM name
                allOf(
                        instanceOf(ScmName.class),
                        hasProperty("name", equalTo("my_scm"))
                ),
                // Custom user name/e-mail address
                allOf(
                        instanceOf(UserIdentity.class),
                        hasProperty("name", equalTo("custom_name")),
                        hasProperty("email", equalTo("custom@mail.com"))
                ),
                // Polling ignores commits from certain users
                allOf(
                        instanceOf(UserExclusion.class),
                        hasProperty("excludedUsers", equalTo("me"))
                ),
                // Polling ignores commits in certain paths
                allOf(
                        instanceOf(PathRestriction.class),
                        hasProperty("excludedRegions", equalTo("/path/excluded")),
                        hasProperty("includedRegions", equalTo("/path/included"))
                ),
                // Polling ignores commits with certain messages
                allOf(
                        instanceOf(MessageExclusion.class),
                        hasProperty("excludedMessage", equalTo("message_excluded"))
                ),
                // Merge before build
                allOf(
                        instanceOf(PreBuildMerge.class),
                        hasProperty("options", instanceOf(UserMergeOptions.class)),
                        hasProperty("options", hasProperty("fastForwardMode", equalTo(MergeCommand.GitPluginFastForwardMode.FF_ONLY))),
                        hasProperty("options", hasProperty("mergeRemote", equalTo("repo_merge"))),
                        hasProperty("options", hasProperty("mergeTarget", equalTo("branch_merge"))),
                        hasProperty("options", hasProperty("mergeStrategy", equalTo(MergeCommand.Strategy.OCTOPUS)))
                ),
                // Sparse Checkout paths
                allOf(
                        instanceOf(SparseCheckoutPaths.class),
                        hasProperty("sparseCheckoutPaths", instanceOf(List.class)),
                        hasProperty("sparseCheckoutPaths", hasSize(2)),
                        hasProperty("sparseCheckoutPaths", containsInAnyOrder(
                                allOf(
                                        instanceOf(SparseCheckoutPath.class),
                                        hasProperty("path", equalTo("/first/last"))
                                ),
                                allOf(
                                        instanceOf(SparseCheckoutPath.class),
                                        hasProperty("path", equalTo("/other/path"))
                                )
                        ))
                )
        ));
    }

    @Override
    protected String stringInLogExpected() {
        return "Setting class hudson.plugins.git.BranchSpec.name = myprodbranch";
    }

    @Override
    protected String configResource() {
        return "global-with-legacy-casc.yaml";
    }
}
