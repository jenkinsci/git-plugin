package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.plugins.git.GitStatus;
import hudson.util.LogTaskListener;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.plugins.git.traits.TagDiscoveryTrait;
import jenkins.scm.api.SCMEventListener;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import org.jvnet.hudson.test.TestExtension;
import org.mockito.Mockito;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Robin Müller
 */
public class GitSCMSourceTest {

    public static final String REMOTE = "git@remote:test/project.git";

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    private GitStatus gitStatus;

    @Before
    public void setup() {
        gitStatus = new GitStatus();
    }

    @Test
    public void testSourceOwnerTriggeredByDoNotifyCommit() throws Exception {
        GitSCMSource gitSCMSource = new GitSCMSource("id", REMOTE, "", "*", "", false);
        GitSCMSourceOwner scmSourceOwner = setupGitSCMSourceOwner(gitSCMSource);
        jenkins.getInstance().add(scmSourceOwner, "gitSourceOwner");

        gitStatus.doNotifyCommit(mock(HttpServletRequest.class), REMOTE, "master", "");

        SCMHeadEvent event =
                jenkins.getInstance().getExtensionList(SCMEventListener.class).get(SCMEventListenerImpl.class)
                        .waitSCMHeadEvent(1, TimeUnit.SECONDS);
        assertThat(event, notNullValue());
        assertThat((Iterable<SCMHead>) event.heads(gitSCMSource).keySet(), hasItem(is(new SCMHead("master"))));
        verify(scmSourceOwner, times(0)).onSCMSourceUpdated(gitSCMSource);

    }

    private GitSCMSourceOwner setupGitSCMSourceOwner(GitSCMSource gitSCMSource) {
        GitSCMSourceOwner owner = mock(GitSCMSourceOwner.class);
        when(owner.hasPermission(Item.READ)).thenReturn(true, true, true);
        when(owner.getSCMSources()).thenReturn(Collections.<SCMSource>singletonList(gitSCMSource));
        return owner;
    }

    private interface GitSCMSourceOwner extends TopLevelItem, SCMSourceOwner {
    }

    @TestExtension
    public static class SCMEventListenerImpl extends SCMEventListener {

        SCMHeadEvent<?> head = null;

        @Override
        public void onSCMHeadEvent(SCMHeadEvent<?> event) {
            synchronized (this) {
                head = event;
                notifyAll();
            }
        }

        public SCMHeadEvent<?> waitSCMHeadEvent(long timeout, TimeUnit units)
                throws TimeoutException, InterruptedException {
            long giveUp = System.currentTimeMillis() + units.toMillis(timeout);
            while (System.currentTimeMillis() < giveUp) {
                synchronized (this) {
                    SCMHeadEvent h = head;
                    if (h != null) {
                        head = null;
                        return h;
                    }
                    wait(Math.max(1L, giveUp - System.currentTimeMillis()));
                }
            }
            throw new TimeoutException();
        }
    }

    @Issue("JENKINS-47526")
    @Test
    public void telescopeFetch() throws Exception {

        GitSCMSource instance = new GitSCMSource("http://git.test/telescope.git");
        assertThat(GitSCMTelescope.of(instance), nullValue());
        instance.setOwner(mock(SCMSourceOwner.class));
        assertThat(GitSCMTelescope.of(instance), notNullValue());

        instance.setTraits(Arrays.<SCMSourceTrait>asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        Map<SCMHead, SCMRevision> result = instance.fetch(SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("manchu"), "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc"
                ),
                new GitTagSCMRevision(
                        new GitTagSCMHead("v1.0.0", 15086193840000L), "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                )));

        instance.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        result = instance.fetch(SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("manchu"), "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc"
                )));

        instance.setTraits(Collections.<SCMSourceTrait>singletonList(new TagDiscoveryTrait()));
        result = instance.fetch(SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new GitTagSCMRevision(
                        new GitTagSCMHead("v1.0.0", 15086193840000L), "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                )));
    }

    @Issue("JENKINS-47526")
    @Test
    public void telescopeFetchWithCriteria() throws Exception {

        GitSCMSource instance = new GitSCMSource("http://git.test/telescope.git");
        assertThat(GitSCMTelescope.of(instance), nullValue());
        instance.setOwner(mock(SCMSourceOwner.class));
        assertThat(GitSCMTelescope.of(instance), notNullValue());

        instance.setTraits(Arrays.<SCMSourceTrait>asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        Map<SCMHead, SCMRevision> result = instance.fetch(new MySCMSourceCriteria("Jenkinsfile"),
                SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                ),
                new GitTagSCMRevision(
                        new GitTagSCMHead("v1.0.0", 15086193840000L), "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                )));
        result = instance.fetch(new MySCMSourceCriteria("README.md"),
                SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("manchu"), "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc"
                )));

        instance.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        result = instance.fetch(new MySCMSourceCriteria("Jenkinsfile"), SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                ),
                new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                )));

        instance.setTraits(Collections.<SCMSourceTrait>singletonList(new TagDiscoveryTrait()));
        result = instance.fetch(new MySCMSourceCriteria("Jenkinsfile"), SCMHeadObserver.collect(), null).result();
        assertThat(result.values(), Matchers.<SCMRevision>containsInAnyOrder(
                new GitTagSCMRevision(
                        new GitTagSCMHead("v1.0.0", 15086193840000L), "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                )));
    }

    @Issue("JENKINS-47526")
    @Test
    public void telescopeFetchRevisions() throws Exception {

        GitSCMSource instance = new GitSCMSource("http://git.test/telescope.git");
        assertThat(GitSCMTelescope.of(instance), nullValue());
        instance.setOwner(mock(SCMSourceOwner.class));
        assertThat(GitSCMTelescope.of(instance), notNullValue());

        instance.setTraits(Arrays.<SCMSourceTrait>asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        Set<String> result = instance.fetchRevisions(null);
        assertThat(result, containsInAnyOrder("foo", "bar", "manchu", "v1.0.0"));

        instance.setTraits(Collections.<SCMSourceTrait>singletonList(new BranchDiscoveryTrait()));
        result = instance.fetchRevisions(null);
        assertThat(result, containsInAnyOrder("foo", "bar", "manchu"));

        instance.setTraits(Collections.<SCMSourceTrait>singletonList(new TagDiscoveryTrait()));
        result = instance.fetchRevisions(null);
        assertThat(result, containsInAnyOrder("v1.0.0"));
    }

    @Issue("JENKINS-47526")
    @Test
    public void telescopeFetchRevision() throws Exception {

        GitSCMSource instance = new GitSCMSource("http://git.test/telescope.git");
        assertThat(GitSCMTelescope.of(instance), nullValue());
        instance.setOwner(mock(SCMSourceOwner.class));
        assertThat(GitSCMTelescope.of(instance), notNullValue());

        instance.setTraits(Arrays.<SCMSourceTrait>asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        assertThat(instance.fetch(new SCMHead("foo"), null),
                hasProperty("hash", is("6769413a79793e242c73d7377f0006c6aea95480")));
        assertThat(instance.fetch(new SCMHead("bar"), null),
                hasProperty("hash", is("3f0b897057d8b43d3b9ff55e3fdefbb021493470")));
        assertThat(instance.fetch(new SCMHead("manchu"), null),
                hasProperty("hash", is("a94782d8d90b56b7e0d277c04589bd2e6f70d2cc")));
        assertThat(instance.fetch(new GitTagSCMHead("v1.0.0", 0L), null),
                hasProperty("hash", is("315fd8b5cae3363b29050f1aabfc27c985e22f7e")));
    }

    @Issue("JENKINS-47526")
    @Test
    public void telescopeFetchRevisionByName() throws Exception {

        GitSCMSource instance = new GitSCMSource("http://git.test/telescope.git");
        assertThat(GitSCMTelescope.of(instance), nullValue());
        instance.setOwner(mock(SCMSourceOwner.class));
        assertThat(GitSCMTelescope.of(instance), notNullValue());

        instance.setTraits(Arrays.<SCMSourceTrait>asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));
        assertThat(instance.fetch("foo", null),
                hasProperty("hash", is("6769413a79793e242c73d7377f0006c6aea95480")));
        assertThat(instance.fetch("bar", null),
                hasProperty("hash", is("3f0b897057d8b43d3b9ff55e3fdefbb021493470")));
        assertThat(instance.fetch("manchu", null),
                hasProperty("hash", is("a94782d8d90b56b7e0d277c04589bd2e6f70d2cc")));
        assertThat(instance.fetch("v1.0.0", null),
                hasProperty("hash", is("315fd8b5cae3363b29050f1aabfc27c985e22f7e")));
    }

    @Issue("JENKINS-47526")
    @Test
    public void telescopeFetchActions() throws Exception {

        GitSCMSource instance = new GitSCMSource("http://git.test/telescope.git");
        assertThat(GitSCMTelescope.of(instance), nullValue());
        AbstractGitSCMSourceTest.ActionableSCMSourceOwner owner =
                Mockito.mock(AbstractGitSCMSourceTest.ActionableSCMSourceOwner.class);
        when(owner.getSCMSource(instance.getId())).thenReturn(instance);
        when(owner.getSCMSources()).thenReturn(Collections.<SCMSource>singletonList(instance));
        instance.setOwner(owner);
        assertThat(GitSCMTelescope.of(instance), notNullValue());

        instance.setTraits(Arrays.<SCMSourceTrait>asList(new BranchDiscoveryTrait(), new TagDiscoveryTrait()));

        List<Action> actions = instance.fetchActions(null, null);
        assertThat(actions,
                contains(allOf(
                        instanceOf(GitRemoteHeadRefAction.class),
                        hasProperty("remote", is("http://git.test/telescope.git")),
                        hasProperty("name", is("manchu"))
                ))
        );
        when(owner.getActions(GitRemoteHeadRefAction.class))
                .thenReturn(Collections.singletonList((GitRemoteHeadRefAction) actions.get(0)));

        assertThat(instance.fetchActions(new SCMHead("foo"), null, null), is(Collections.<Action>emptyList()));
        assertThat(instance.fetchActions(new SCMHead("bar"), null, null), is(Collections.<Action>emptyList()));
        assertThat(instance.fetchActions(new SCMHead("manchu"), null, null), contains(
                instanceOf(PrimaryInstanceMetadataAction.class)));
        assertThat(instance.fetchActions(new GitTagSCMHead("v1.0.0", 0L), null, null),
                is(Collections.<Action>emptyList()));
    }

    @TestExtension
    public static class MyGitSCMTelescope extends GitSCMTelescope {
        @Override
        public boolean supports(@NonNull String remote) {
            return "http://git.test/telescope.git".equals(remote);
        }

        @Override
        public void validate(@NonNull String remote, StandardCredentials credentials)
                throws IOException, InterruptedException {
        }

        @Override
        protected SCMFileSystem build(@NonNull String remote, StandardCredentials credentials,
                                      @NonNull SCMHead head,
                                      final SCMRevision rev) throws IOException, InterruptedException {
            final String hash;
            if (rev instanceof AbstractGitSCMSource.SCMRevisionImpl) {
                hash = ((AbstractGitSCMSource.SCMRevisionImpl) rev).getHash();
            } else {
                switch (head.getName()) {
                    case "foo":
                        hash = "6769413a79793e242c73d7377f0006c6aea95480";
                        break;
                    case "bar":
                        hash = "3f0b897057d8b43d3b9ff55e3fdefbb021493470";
                        break;
                    case "manchu":
                        hash = "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc";
                        break;
                    case "v1.0.0":
                        hash = "315fd8b5cae3363b29050f1aabfc27c985e22f7e";
                        break;
                    default:
                        return null;
                }
            }
            return new SCMFileSystem(rev) {
                @Override
                public long lastModified() throws IOException, InterruptedException {
                    switch (hash) {
                        case "6769413a79793e242c73d7377f0006c6aea95480":
                            return 15086163840000L;
                        case "3f0b897057d8b43d3b9ff55e3fdefbb021493470":
                            return 15086173840000L;
                        case "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc":
                            return 15086183840000L;
                        case "315fd8b5cae3363b29050f1aabfc27c985e22f7e":
                            return 15086193840000L;
                    }
                    return 0L;
                }

                @NonNull
                @Override
                public SCMFile getRoot() {
                    return new MySCMFile(hash);
                }
            };
        }

        @Override
        public long getTimestamp(@NonNull String remote, StandardCredentials credentials, @NonNull String refOrHash)
                throws IOException, InterruptedException {
            switch (refOrHash) {
                case "refs/heads/foo":
                    refOrHash = "6769413a79793e242c73d7377f0006c6aea95480";
                    break;
                case "refs/heads/bar":
                    refOrHash = "3f0b897057d8b43d3b9ff55e3fdefbb021493470";
                    break;
                case "refs/heads/manchu":
                    refOrHash = "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc";
                    break;
                case "refs/tags/v1.0.0":
                    refOrHash = "315fd8b5cae3363b29050f1aabfc27c985e22f7e";
                    break;
            }
            switch (refOrHash) {
                case "6769413a79793e242c73d7377f0006c6aea95480":
                    return 15086163840000L;
                case "3f0b897057d8b43d3b9ff55e3fdefbb021493470":
                    return 15086173840000L;
                case "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc":
                    return 15086183840000L;
                case "315fd8b5cae3363b29050f1aabfc27c985e22f7e":
                    return 15086193840000L;
            }
            return 0L;
        }

        @Override
        public SCMRevision getRevision(@NonNull String remote, StandardCredentials credentials,
                                       @NonNull String refOrHash)
                throws IOException, InterruptedException {
            switch (refOrHash) {
                case "refs/heads/foo":
                    return new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                    );
                case "refs/heads/bar":
                    return new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                    );
                case "refs/heads/manchu":
                    return new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("manchu"), "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc"
                    );
                case "refs/tags/v1.0.0":
                    return new GitTagSCMRevision(
                            new GitTagSCMHead("v1.0.0", 15086193840000L),
                            "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                    );
            }
            return null;
        }

        @Override
        public Iterable<SCMRevision> getRevisions(@NonNull String remote, StandardCredentials credentials,
                                                  @NonNull Set<ReferenceType> referenceTypes)
                throws IOException, InterruptedException {
            return Arrays.<SCMRevision>asList(
                    new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("foo"), "6769413a79793e242c73d7377f0006c6aea95480"
                    ),
                    new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("bar"), "3f0b897057d8b43d3b9ff55e3fdefbb021493470"
                    ),
                    new AbstractGitSCMSource.SCMRevisionImpl(
                            new SCMHead("manchu"), "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc"
                    ),
                    new GitTagSCMRevision(
                            new GitTagSCMHead("v1.0.0", 15086193840000L), "315fd8b5cae3363b29050f1aabfc27c985e22f7e"
                    )
            );
        }

        @Override
        public String getDefaultTarget(@NonNull String remote, StandardCredentials credentials)
                throws IOException, InterruptedException {
            return "manchu";
        }

        private static class MySCMFile extends SCMFile {
            private final String hash;
            private final SCMFile.Type type;

            public MySCMFile(String hash) {
                this.hash = hash;
                this.type = Type.DIRECTORY;
            }

            public MySCMFile(MySCMFile parent, String name, SCMFile.Type type) {
                super(parent, name);
                this.type = type;
                this.hash = parent.hash;
            }

            @NonNull
            @Override
            protected SCMFile newChild(@NonNull String name, boolean assumeIsDirectory) {
                return new MySCMFile(this, name, assumeIsDirectory ? Type.DIRECTORY : Type.REGULAR_FILE);
            }

            @NonNull
            @Override
            public Iterable<SCMFile> children() throws IOException, InterruptedException {
                if (parent().isRoot()) {
                    switch (hash) {
                        case "6769413a79793e242c73d7377f0006c6aea95480":
                            return Collections.singleton(newChild("Jenkinsfile", false));
                        case "3f0b897057d8b43d3b9ff55e3fdefbb021493470":
                            return Arrays.asList(newChild("Jenkinsfile", false),
                                    newChild("README.md", false));
                        case "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc":
                            return Collections.singleton(newChild("README.md", false));
                        case "315fd8b5cae3363b29050f1aabfc27c985e22f7e":
                            return Collections.singleton(newChild("Jenkinsfile", false));
                    }
                }
                return Collections.emptySet();
            }

            @Override
            public long lastModified() throws IOException, InterruptedException {
                switch (hash) {
                    case "6769413a79793e242c73d7377f0006c6aea95480":
                        return 15086163840000L;
                    case "3f0b897057d8b43d3b9ff55e3fdefbb021493470":
                        return 15086173840000L;
                    case "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc":
                        return 15086183840000L;
                    case "315fd8b5cae3363b29050f1aabfc27c985e22f7e":
                        return 15086193840000L;
                }
                return 0L;
            }

            @NonNull
            @Override
            protected Type type() throws IOException, InterruptedException {
                switch (hash) {
                    case "6769413a79793e242c73d7377f0006c6aea95480":
                        switch (getPath()) {
                            case "Jenkinsfile":
                                return Type.REGULAR_FILE;
                        }
                        break;
                    case "3f0b897057d8b43d3b9ff55e3fdefbb021493470":
                        switch (getPath()) {
                            case "Jenkinsfile":
                                return Type.REGULAR_FILE;
                            case "README.md":
                                return Type.REGULAR_FILE;
                        }
                        break;
                    case "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc":
                        switch (getPath()) {
                            case "README.md":
                                return Type.REGULAR_FILE;
                        }
                        break;
                    case "315fd8b5cae3363b29050f1aabfc27c985e22f7e":
                        switch (getPath()) {
                            case "Jenkinsfile":
                                return Type.REGULAR_FILE;
                        }
                        break;
                }
                return type == Type.DIRECTORY ? type : Type.NONEXISTENT;
            }

            @NonNull
            @Override
            public InputStream content() throws IOException, InterruptedException {
                switch (hash) {
                    case "6769413a79793e242c73d7377f0006c6aea95480":
                        switch (getPath()) {
                            case "Jenkinsfile":
                                return new ByteArrayInputStream("pipeline{}".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    case "3f0b897057d8b43d3b9ff55e3fdefbb021493470":
                        switch (getPath()) {
                            case "Jenkinsfile":
                                return new ByteArrayInputStream("pipeline{}".getBytes(StandardCharsets.UTF_8));
                            case "README.md":
                                return new ByteArrayInputStream("Welcome".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    case "a94782d8d90b56b7e0d277c04589bd2e6f70d2cc":
                        switch (getPath()) {
                            case "README.md":
                                return new ByteArrayInputStream("Welcome".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                    case "315fd8b5cae3363b29050f1aabfc27c985e22f7e":
                        switch (getPath()) {
                            case "Jenkinsfile":
                                return new ByteArrayInputStream("pipeline{}".getBytes(StandardCharsets.UTF_8));
                        }
                        break;
                }
                throw new FileNotFoundException(getPath() + " does not exist");
            }
        }
    }

    private static class MySCMSourceCriteria implements SCMSourceCriteria {

        private final String path;

        private MySCMSourceCriteria(String path) {
            this.path = path;
        }

        @Override
        public boolean isHead(@NonNull Probe probe, @NonNull TaskListener listener) throws IOException {
            return SCMFile.Type.REGULAR_FILE.equals(probe.stat(path).getType());
        }
    }
}
