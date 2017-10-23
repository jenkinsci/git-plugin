/*
 * The MIT License
 *
 * Copyright 2017 Mark Waite.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.search.Search;
import hudson.search.SearchIndex;
import hudson.security.ACL;
import hudson.security.Permission;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import static jenkins.plugins.git.AbstractGitSCMSourceRetrieveHeadsTest.EXPECTED_GIT_EXE;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.plugins.git.traits.GitToolSCMSourceTrait;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.acegisecurity.AccessDeniedException;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.gitclient.GitClient;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.ClassRule;

public class GitSCMTelescopeTest /* extends AbstractGitRepository */ {

    private final StandardCredentials credentials = null;

    /* REPO can be allocated once for the whole test suite so long as nothing changes it */
    @ClassRule
    static public final GitSampleRepoRule READ_ONLY_REPO = new GitSampleRepoRule();

    private final String remote;
    private GitSCMTelescope telescope;

    public GitSCMTelescopeTest() {
        remote = READ_ONLY_REPO.fileUrl();
    }

    @Before
    public void createTelescopeForRemote() {
        telescope = new GitSCMTelescopeImpl(remote);
    }

    @Test
    public void testOf_GitSCM() {
        /* Testing GitSCMTelescope.of() for non null return needs JenkinsRule */
        GitSCM multiBranchSource = new GitSCM(remote);
        GitSCMTelescope telescope = GitSCMTelescope.of(multiBranchSource);
        assertThat(telescope, is(nullValue()));
    }

    @Test
    public void testOf_AbstractGitSCMSource() {
        AbstractGitSCMSource source = new AbstractGitSCMSourceImpl();
        GitSCMTelescope telescope = GitSCMTelescope.of(source);
        assertThat(telescope, is(nullValue()));
    }

    @Test
    public void testSupports_StringFalse() {
        GitSCMTelescope telescopeWithoutRemote = new GitSCMTelescopeImpl();
        assertFalse(telescopeWithoutRemote.supports(remote));
    }

    @Test
    public void testSupports_String() {
        assertTrue(telescope.supports(remote));
    }

    @Test
    public void testValidate() throws Exception {
        telescope.validate(remote, credentials);
    }

    /**
     * Return a GitSCM defined with a branchSpecList which exactly matches a
     * single branch. GitSCMTelescope requires a GitSCM that matches a single
     * branch, with no wildcards in the branch name.
     *
     * @param repoUrl URL to the repository for the returned GitSCM
     * @return GitSCM with a single branch in its definition
     */
    private GitSCM getSingleBranchSource(String repoUrl) {
        UserRemoteConfig remoteConfig = new UserRemoteConfig(
                repoUrl,
                "origin",
                "+refs/heads/master:refs/remotes/origin/master",
                null);
        List<UserRemoteConfig> remoteConfigList = new ArrayList<>();
        remoteConfigList.add(remoteConfig);
        BranchSpec masterBranchSpec = new BranchSpec("master");
        List<BranchSpec> branchSpecList = new ArrayList<>();
        branchSpecList.add(masterBranchSpec);
        boolean doGenerateSubmoduleConfigurations = false;
        Collection<SubmoduleConfig> submoduleCfg = new ArrayList<>();
        GitRepositoryBrowser browser = new GitWeb(repoUrl);
        String gitTool = "Default";
        List<GitSCMExtension> extensions = null;
        GitSCM singleBranchSource = new GitSCM(remoteConfigList,
                branchSpecList,
                doGenerateSubmoduleConfigurations,
                submoduleCfg,
                browser,
                gitTool,
                extensions);
        return singleBranchSource;
    }

    @Test
    public void testSupports_SCM() throws Exception {
        GitSCM singleBranchSource = getSingleBranchSource(remote);
        // single branch source is supported by telescope
        assertTrue(telescope.supports(singleBranchSource));
    }

    @Test
    public void testSupports_SCMNullSCM() throws Exception {
        NullSCM nullSCM = new NullSCM();
        // NullSCM is not supported by telescope
        assertFalse(telescope.supports(nullSCM));
    }

    @Test
    public void testSupports_SCMMultiBranchSource() throws Exception {
        GitSCM multiBranchSource = new GitSCM(remote);
        // Multi-branch source is not supported by telescope
        assertFalse(telescope.supports(multiBranchSource));
    }

    @Test
    public void testSupports_SCMSource() {
        SCMSource source = new GitSCMSource(remote);
        SCMSourceOwner sourceOwner = new SCMSourceOwnerImpl();
        source.setOwner(sourceOwner);
        assertTrue(telescope.supports(source));
    }

    @Test
    public void testSupports_SCMSourceNoOwner() {
        SCMSource source = new GitSCMSource(remote);
        // SCMSource without an owner not supported by telescope
        assertFalse(telescope.supports(source));
    }

    @Test
    public void testSupports_SCMSourceNullSource() {
        SCMSource source = new SCMSourceImpl();
        // Non AbstractGitSCMSource is not supported by telescope
        assertFalse(telescope.supports(source));
    }

    @Test
    public void testGetTimestamp_3args_1() throws Exception {
        String refOrHash = "master";
        assertThat(telescope.getTimestamp(remote, credentials, refOrHash), is(12345L));
    }

    @Test
    public void testGetTimestamp_3args_2Tag() throws Exception {
        SCMHead head = new GitTagSCMHead("git-tag-name", 56789L);
        assertThat(telescope.getTimestamp(remote, credentials, head), is(12345L));
    }

    @Test
    public void testGetTimestamp_3args_2() throws Exception {
        SCMHead head = new SCMHead("git-tag-name");
        assertThat(telescope.getTimestamp(remote, credentials, head), is(12345L));
    }

    @Test
    public void testGetDefaultTarget() throws Exception {
        assertThat(telescope.getDefaultTarget(remote, null), is(""));
    }

    @Test
    public void testBuild_3args_1() throws Exception {
        SCMSource source = new GitSCMSource(remote);
        SCMSourceOwner sourceOwner = new SCMSourceOwnerImpl();
        source.setOwner(sourceOwner);
        SCMHead head = new SCMHead("some-name");
        String SHA1 = "0123456789abcdef0123456789abcdef01234567";
        SCMRevision rev = new AbstractGitSCMSource.SCMRevisionImpl(head, SHA1);
        SCMFileSystem fileSystem = telescope.build(source, head, rev);
        assertThat(fileSystem.getRevision(), is(rev));
        assertThat(fileSystem.isFixedRevision(), is(true));
    }

    @Test
    public void testBuild_3args_1NoOwner() throws Exception {
        SCMSource source = new GitSCMSource(remote);
        SCMHead head = new SCMHead("some-name");
        SCMRevision rev = null;
        // When source has no owner, build returns null
        assertThat(telescope.build(source, head, rev), is(nullValue()));
    }

    @Test
    public void testBuild_3args_2() throws Exception {
        Item owner = new ItemImpl();
        SCM scm = getSingleBranchSource(remote);
        SCMHead head = new SCMHead("some-name");
        String SHA1 = "0123456789abcdef0123456789abcdef01234567";
        SCMRevision rev = new AbstractGitSCMSource.SCMRevisionImpl(head, SHA1);
        SCMFileSystem fileSystem = telescope.build(owner, scm, rev);
        assertThat(fileSystem.getRevision(), is(rev));
        assertThat(fileSystem.isFixedRevision(), is(true));
    }

    @Test
    public void testBuild_4args() throws Exception {
        SCMHead head = new SCMHead("some-name");
        String SHA1 = "0123456789abcdef0123456789abcdef01234567";
        SCMRevision rev = new AbstractGitSCMSource.SCMRevisionImpl(head, SHA1);
        SCMFileSystem fileSystem = telescope.build(remote, credentials, head, rev);
        assertThat(fileSystem, is(notNullValue()));
        assertThat(fileSystem.getRevision(), is(rev));
        assertThat(fileSystem.isFixedRevision(), is(true));
    }

    @Test
    public void testGetRevision_3args_1() throws Exception {
        SCMHead head = new SCMHead("some-name");
        String SHA1 = "0123456789abcdef0123456789abcdef01234567";
        SCMRevision rev = new AbstractGitSCMSource.SCMRevisionImpl(head, SHA1);
        GitSCMTelescope telescopeWithRev = new GitSCMTelescopeImpl(remote, rev);
        String refOrHash = "master";
        assertThat(telescopeWithRev.getRevision(remote, credentials, refOrHash), is(rev));
    }

    @Test
    public void testGetRevision_3args_2() throws Exception {
        SCMHead head = new GitTagSCMHead("git-tag-name", 56789L);
        String SHA1 = "0123456789abcdef0123456789abcdef01234567";
        SCMRevision rev = new AbstractGitSCMSource.SCMRevisionImpl(head, SHA1);
        GitSCMTelescope telescopeWithRev = new GitSCMTelescopeImpl(remote, rev);
        assertThat(telescopeWithRev.getRevision(remote, credentials, head), is(rev));
    }

    @Test
    public void testGetRevisions_3args() throws Exception {
        Set<GitSCMTelescope.ReferenceType> referenceTypes = new HashSet<>();
        Iterable<SCMRevision> revisions = telescope.getRevisions(remote, credentials, referenceTypes);
        assertThat(revisions, is(notNullValue()));
        assertFalse(revisions.iterator().hasNext());
    }

    @Test
    public void testGetRevisions_3argsWithRev() throws Exception {
        Set<GitSCMTelescope.ReferenceType> referenceTypes = new HashSet<>();
        SCMHead head = new GitTagSCMHead("git-tag-name", 56789L);
        String SHA1 = "0123456789abcdef0123456789abcdef01234567";
        SCMRevision rev = new AbstractGitSCMSource.SCMRevisionImpl(head, SHA1);
        GitSCMTelescope telescopeWithRev = new GitSCMTelescopeImpl(remote, rev);
        Iterable<SCMRevision> revisions = telescopeWithRev.getRevisions(remote, credentials, referenceTypes);
        assertThat(revisions, is(notNullValue()));
        Iterator<SCMRevision> revIterator = revisions.iterator();
        assertThat(revIterator.next(), is(rev));
        assertFalse(revIterator.hasNext());
    }

    @Test
    public void testGetRevisions_String_StandardCredentials() throws Exception {
        String SHA1 = "0123456789abcdef0123456789abcdef01234567";
        SCMHead head = new GitTagSCMHead("git-tag-name", 56789L);
        SCMRevision rev = new AbstractGitSCMSource.SCMRevisionImpl(head, SHA1);
        GitSCMTelescope telescopeWithRev = new GitSCMTelescopeImpl(remote, rev);
        Iterable<SCMRevision> revisions = telescopeWithRev.getRevisions(remote, credentials);
        assertThat(revisions, is(notNullValue()));
        Iterator<SCMRevision> revIterator = revisions.iterator();
        assertThat(revIterator.next(), is(rev));
        assertFalse(revIterator.hasNext());
    }

    /* ********************* Test helper classes **************************** */
    private static class ItemImpl implements Item {

        public ItemImpl() {
        }

        @Override
        public ItemGroup<? extends Item> getParent() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public Collection<? extends Job> getAllJobs() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getFullName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getDisplayName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getFullDisplayName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getRelativeNameFrom(ItemGroup ig) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getRelativeNameFrom(Item item) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getUrl() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getShortUrl() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getAbsoluteUrl() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void onLoad(ItemGroup<? extends Item> ig, String string) throws IOException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void onCopiedFrom(Item item) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void onCreatedFromScratch() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void save() throws IOException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void delete() throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public File getRootDir() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public Search getSearch() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getSearchName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getSearchUrl() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public SearchIndex getSearchIndex() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public ACL getACL() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void checkPermission(Permission prmsn) throws AccessDeniedException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public boolean hasPermission(Permission prmsn) {
            throw new UnsupportedOperationException("Not called.");
        }
    }

    private static class SCMSourceImpl extends SCMSource {

        public SCMSourceImpl() {
        }

        @Override
        protected void retrieve(SCMSourceCriteria scmsc, SCMHeadObserver scmho, SCMHeadEvent<?> scmhe, TaskListener tl) throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public SCM build(SCMHead scmh, SCMRevision scmr) {
            throw new UnsupportedOperationException("Not called.");
        }
    }

    private static class GitSCMTelescopeImpl extends GitSCMTelescope {

        final private String allowedRemote;
        final private SCMRevision revision;
        private List<SCMRevision> revisionList = new ArrayList<>();

        public GitSCMTelescopeImpl(String allowedRemote, SCMRevision revision) {
            this.allowedRemote = allowedRemote;
            this.revision = revision;
            revisionList = new ArrayList<>();
            revisionList.add(this.revision);
        }

        public GitSCMTelescopeImpl(String allowedRemote) {
            this.allowedRemote = allowedRemote;
            this.revision = null;
            revisionList = new ArrayList<>();
        }

        public GitSCMTelescopeImpl() {
            this.allowedRemote = null;
            this.revision = null;
            revisionList = new ArrayList<>();
        }

        @Override
        public boolean supports(String remote) {
            if (allowedRemote == null) {
                return false;
            }
            return allowedRemote.equals(remote);
        }

        @Override
        public void validate(String remote, StandardCredentials credentials) throws IOException, InterruptedException {
        }

        @Override
        public SCMFileSystem build(String remote, StandardCredentials credentials, SCMHead head, SCMRevision rev) throws IOException, InterruptedException {
            GitClient client = null;
            AbstractGitSCMSource.SCMRevisionImpl myRev = null;
            if (rev != null) {
                String SHA1 = rev.toString();
                myRev = new AbstractGitSCMSource.SCMRevisionImpl(head, SHA1);
            }
            return new GitSCMFileSystem(client, remote, head.toString(), myRev);
        }

        @Override
        public long getTimestamp(String remote, StandardCredentials credentials, String refOrHash) throws IOException, InterruptedException {
            return 12345L;
        }

        @Override
        public SCMRevision getRevision(String remote, StandardCredentials credentials, String refOrHash) throws IOException, InterruptedException {
            return revision;
        }

        @Override
        public Iterable<SCMRevision> getRevisions(String remote, StandardCredentials credentials, Set<ReferenceType> referenceTypes) throws IOException, InterruptedException {
            return revisionList;
        }

        @Override
        public String getDefaultTarget(String remote, StandardCredentials credentials) throws IOException, InterruptedException {
            return "";
        }
    }

    private static class AbstractGitSCMSourceImpl extends AbstractGitSCMSource {

        public AbstractGitSCMSourceImpl() {
            setId("AbstractGitSCMSourceImpl-id");
        }

        @NonNull
        @Override
        public List<SCMSourceTrait> getTraits() {
            return Collections.<SCMSourceTrait>singletonList(new GitToolSCMSourceTrait(EXPECTED_GIT_EXE) {
                @Override
                public SCMSourceTraitDescriptor getDescriptor() {
                    return new GitBrowserSCMSourceTrait.DescriptorImpl();
                }
            });
        }

        @Override
        public String getCredentialsId() {
            return "";
        }

        @Override
        public String getRemote() {
            return "";
        }

        @Override
        public SCMSourceDescriptor getDescriptor() {
            return new DescriptorImpl();
        }

        public static class DescriptorImpl extends SCMSourceDescriptor {

            @Override
            public String getDisplayName() {
                return null;
            }
        }
    }

    private static class SCMSourceOwnerImpl implements SCMSourceOwner {

        @Override
        public List<SCMSource> getSCMSources() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public SCMSource getSCMSource(String string) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void onSCMSourceUpdated(SCMSource scms) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public SCMSourceCriteria getSCMSourceCriteria(SCMSource scms) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public ItemGroup<? extends Item> getParent() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public Collection<? extends Job> getAllJobs() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getFullName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getDisplayName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getFullDisplayName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getRelativeNameFrom(ItemGroup ig) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getRelativeNameFrom(Item item) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getUrl() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getShortUrl() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getAbsoluteUrl() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void onLoad(ItemGroup<? extends Item> ig, String string) throws IOException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void onCopiedFrom(Item item) {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void onCreatedFromScratch() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void save() throws IOException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void delete() throws IOException, InterruptedException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public File getRootDir() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public Search getSearch() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getSearchName() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public String getSearchUrl() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public SearchIndex getSearchIndex() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public ACL getACL() {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public void checkPermission(Permission prmsn) throws AccessDeniedException {
            throw new UnsupportedOperationException("Not called.");
        }

        @Override
        public boolean hasPermission(Permission prmsn) {
            throw new UnsupportedOperationException("Not called.");
        }
    }
}
