package hudson.plugins.git.extensions.impl;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import java.util.List;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PartialCloneFilterTest {

    private static final String BLOBLESS_FILTER_SPEC = "blob:none";

    private PartialCloneFilter bloblessPartialCloneFilter;

    @BeforeEach
    void beforeEach() {
        bloblessPartialCloneFilter = new PartialCloneFilter(BLOBLESS_FILTER_SPEC);
    }

    @Test
    void testGetFilterSpec() {
        assertThat(bloblessPartialCloneFilter.getFilterSpec(), is(BLOBLESS_FILTER_SPEC));
    }

    @Test
    void testDecorateCloneCommand() throws Exception {
        GitSCM scm = null;
        Run build = null;
        GitClient git = null;
        TaskListener listener = null;
        MyCloneCommand cmd = new MyCloneCommand();
        bloblessPartialCloneFilter.decorateCloneCommand(scm, build, git, listener, cmd);
        assertThat(cmd.getFilterSpec(), is(BLOBLESS_FILTER_SPEC));
    }

    @Test
    void testDecorateFetchCommand() throws Exception {
        GitSCM scm = null;
        Run build = null;
        GitClient git = null;
        TaskListener listener = null;
        MyFetchCommand cmd = new MyFetchCommand();
        bloblessPartialCloneFilter.decorateFetchCommand(scm, build, git, listener, cmd);
        assertThat(cmd.getFilterSpec(), is(BLOBLESS_FILTER_SPEC));
    }

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(PartialCloneFilter.class).usingGetClass().verify();
    }

    @Test
    void testHashCode() {
        PartialCloneFilter bloblessPartialCloneFilterCopy = new PartialCloneFilter(BLOBLESS_FILTER_SPEC);
        assertThat(bloblessPartialCloneFilter.hashCode(), is(bloblessPartialCloneFilterCopy.hashCode()));
        assertThat(bloblessPartialCloneFilter, is(bloblessPartialCloneFilterCopy));
    }

    @Test
    void testToString() {
        assertThat(
                bloblessPartialCloneFilter.toString(),
                is("PartialCloneFilter{filterSpec=" + BLOBLESS_FILTER_SPEC + "}"));
    }

    private static class MyCloneCommand implements CloneCommand {

        private String filterSpec;

        String getFilterSpec() {
            return filterSpec;
        }

        @Override
        public CloneCommand url(String url) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand repositoryName(String name) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand shallow() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand shallow(boolean shallow) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand shared() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand shared(boolean shared) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand reference(String reference) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand timeout(Integer timeout) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand noCheckout() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand tags(boolean tags) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand refspecs(List<RefSpec> refspecs) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand depth(Integer depth) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CloneCommand filter(String filterSpec) {
            this.filterSpec = filterSpec;
            return this;
        }

        @Override
        public void execute() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static class MyFetchCommand implements FetchCommand {

        private String filterSpec;

        String getFilterSpec() {
            return filterSpec;
        }

        @Override
        public FetchCommand from(URIish remote, List<RefSpec> refspecs) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FetchCommand prune() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FetchCommand prune(boolean prune) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FetchCommand shallow(boolean shallow) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FetchCommand timeout(Integer timeout) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FetchCommand tags(boolean tags) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FetchCommand depth(Integer depth) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public FetchCommand filter(String filterSpec) {
            this.filterSpec = filterSpec;
            return this;
        }

        @Override
        public void execute() throws GitException, InterruptedException {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }
}
