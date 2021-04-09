/*
 * The MIT License
 *
 * Copyright 2020 Mark Waite.
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
package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import hudson.model.Run;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.util.LogTaskListener;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SparseCheckoutPathsTest {

    private final SparseCheckoutPaths emptySparseCheckoutPaths;
    private final List<SparseCheckoutPath> emptySparseCheckoutPathList;

    private final SparseCheckoutPaths sparseCheckoutPaths;
    private final List<SparseCheckoutPath> sparseCheckoutPathList;

    private static final String SRC_DIR_NAME = "src";
    private static final SparseCheckoutPath SRC_SPARSE_CHECKOUT_PATH = new SparseCheckoutPath(SRC_DIR_NAME);

    private LogTaskListener listener;
    private LogHandler handler;
    private int logCount = 0;

    public SparseCheckoutPathsTest() {
        emptySparseCheckoutPathList = new ArrayList<>();
        emptySparseCheckoutPaths = new SparseCheckoutPaths(emptySparseCheckoutPathList);

        sparseCheckoutPathList = new ArrayList<>();
        sparseCheckoutPathList.add(SRC_SPARSE_CHECKOUT_PATH);
        sparseCheckoutPaths = new SparseCheckoutPaths(sparseCheckoutPathList);

        listener = null;
        handler = null;
    }

    @Before
    public void createLogger() {
        Logger logger = Logger.getLogger(this.getClass().getPackage().getName() + "-" + logCount++);
        handler = new LogHandler();
        handler.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        listener = new LogTaskListener(logger, Level.ALL);
    }

    @Test
    public void testGetSparseCheckoutPaths() {
        assertThat(sparseCheckoutPaths.getSparseCheckoutPaths(), hasItem(SRC_SPARSE_CHECKOUT_PATH));
    }

    @Test
    public void testGetSparseCheckoutPathsEmpty() {
        assertThat(emptySparseCheckoutPaths.getSparseCheckoutPaths(), is(empty()));
    }

    @Test
    public void testDecorateCloneCommand() throws Exception {
        GitSCM scm = null;
        Run build = null;
        GitClient git = null;
        CloneCommand cmd = null;
        sparseCheckoutPaths.decorateCloneCommand(scm, build, git, listener, cmd);
        assertThat(handler.getMessages(), hasItem("Using no checkout clone with sparse checkout."));
    }

    @Test
    public void testDecorateCloneCommandEmpty() throws Exception {
        GitSCM scm = null;
        Run build = null;
        GitClient git = null;
        CloneCommand cmd = null;
        emptySparseCheckoutPaths.decorateCloneCommand(scm, build, git, listener, cmd);
        assertThat(handler.getMessages(), is(empty()));
    }

    @Test
    public void testDecorateCheckoutCommand() throws Exception {
        GitSCM scm = null;
        Run<?, ?> build = mock(Run.class);
        when(build.getEnvironment(listener)).thenReturn(new EnvVars());
        GitClient git = null;
        MyCheckoutCommand cmd = new MyCheckoutCommand();
        sparseCheckoutPaths.decorateCheckoutCommand(scm, build, git, listener, cmd);
        assertThat(cmd.getSparsePathNames(), hasItems(SRC_DIR_NAME));
    }

    @Test
    public void testDecorateCheckoutCommandExpandsEnvVariable() throws Exception {
        GitSCM scm = null;
        GitClient git = null;
        Run<?, ?> build = mock(Run.class);
        EnvVars envVars = new EnvVars();
        envVars.put("SPARSE_CHECKOUT_DIRECTORY", SRC_DIR_NAME);
        when(build.getEnvironment(listener)).thenReturn(envVars);

        MyCheckoutCommand cmd = new MyCheckoutCommand();
        List<SparseCheckoutPath> sparseCheckoutPathList = new ArrayList<>();
        sparseCheckoutPathList.add(new SparseCheckoutPath("${SPARSE_CHECKOUT_DIRECTORY}"));
        SparseCheckoutPaths sparseCheckoutPaths = new SparseCheckoutPaths(sparseCheckoutPathList);
        sparseCheckoutPaths.decorateCheckoutCommand(scm, build, git, listener, cmd);

        assertThat(cmd.getSparsePathNames(), hasItems(SRC_DIR_NAME));
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(SparseCheckoutPaths.class).usingGetClass().verify();
    }

    @Test
    public void testHashCode() {
        SparseCheckoutPaths emptySparseCheckoutPathsCopy = new SparseCheckoutPaths(emptySparseCheckoutPathList);
        assertThat(emptySparseCheckoutPaths.hashCode(), is(emptySparseCheckoutPathsCopy.hashCode()));
        assertThat(emptySparseCheckoutPaths, is(emptySparseCheckoutPathsCopy));
    }

    @Test
    public void testToString() {
        assertThat(emptySparseCheckoutPaths.toString(), is("SparseCheckoutPaths{sparseCheckoutPaths=[]}"));
    }

    private class MyCheckoutCommand implements CheckoutCommand {

        private List<String> sparsePathNames;

        List<String> getSparsePathNames() {
            return sparsePathNames;
        }

        @Override
        public CheckoutCommand ref(String string) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CheckoutCommand branch(String string) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CheckoutCommand deleteBranchIfExist(boolean bln) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CheckoutCommand sparseCheckoutPaths(List<String> list) {
            this.sparsePathNames = list;
            return this;
        }

        @Override
        public CheckoutCommand timeout(Integer intgr) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CheckoutCommand lfsRemote(String string) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public CheckoutCommand lfsCredentials(StandardCredentials sc) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public void execute() throws GitException, InterruptedException {
            throw new UnsupportedOperationException("Not supported yet.");
        }

    }
}
