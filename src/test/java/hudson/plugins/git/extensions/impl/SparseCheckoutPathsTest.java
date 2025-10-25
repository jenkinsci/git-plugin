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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

class SparseCheckoutPathsTest {

    private SparseCheckoutPaths emptySparseCheckoutPaths;
    private List<SparseCheckoutPath> emptySparseCheckoutPathList;

    private SparseCheckoutPaths sparseCheckoutPaths;
    private List<SparseCheckoutPath> sparseCheckoutPathList;

    private static final String SRC_DIR_NAME = "src";
    private static final SparseCheckoutPath SRC_SPARSE_CHECKOUT_PATH = new SparseCheckoutPath(SRC_DIR_NAME);

    private LogTaskListener listener;
    private LogHandler handler;
    private int logCount = 0;


    @BeforeEach
    void beforeEach() {
        emptySparseCheckoutPathList = new ArrayList<>();
        emptySparseCheckoutPaths = new SparseCheckoutPaths(emptySparseCheckoutPathList);

        sparseCheckoutPathList = new ArrayList<>();
        sparseCheckoutPathList.add(SRC_SPARSE_CHECKOUT_PATH);
        sparseCheckoutPaths = new SparseCheckoutPaths(sparseCheckoutPathList);

        listener = null;
        handler = null;

        Logger logger = Logger.getLogger(this.getClass().getPackage().getName() + "-" + logCount++);
        handler = new LogHandler();
        handler.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        listener = new LogTaskListener(logger, Level.ALL);
    }

    @Test
    void testGetSparseCheckoutPaths() {
        assertThat(sparseCheckoutPaths.getSparseCheckoutPaths(), hasItem(SRC_SPARSE_CHECKOUT_PATH));
    }

    @Test
    void testGetSparseCheckoutPathsEmpty() {
        assertThat(emptySparseCheckoutPaths.getSparseCheckoutPaths(), is(empty()));
    }

    @Test
    void testDecorateCloneCommand() throws Exception {
        GitSCM scm = null;
        Run build = null;
        GitClient git = null;
        CloneCommand cmd = null;
        sparseCheckoutPaths.decorateCloneCommand(scm, build, git, listener, cmd);
        assertThat(handler.getMessages(), hasItem("Using no checkout clone with sparse checkout."));
    }

    @Test
    void testDecorateCloneCommandEmpty() throws Exception {
        GitSCM scm = null;
        Run build = null;
        GitClient git = null;
        CloneCommand cmd = null;
        emptySparseCheckoutPaths.decorateCloneCommand(scm, build, git, listener, cmd);
        assertThat(handler.getMessages(), is(empty()));
    }

    @Test
    void testDecorateCheckoutCommand() throws Exception {
        GitSCM scm = null;
        Run build = null;
        GitClient git = null;
        MyCheckoutCommand cmd = new MyCheckoutCommand();
        sparseCheckoutPaths.decorateCheckoutCommand(scm, build, git, listener, cmd);
        assertThat(cmd.getSparsePathNames(), hasItems(SRC_DIR_NAME));
    }

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(SparseCheckoutPaths.class).usingGetClass().verify();
    }

    @Test
    void testHashCode() {
        SparseCheckoutPaths emptySparseCheckoutPathsCopy = new SparseCheckoutPaths(emptySparseCheckoutPathList);
        assertThat(emptySparseCheckoutPaths.hashCode(), is(emptySparseCheckoutPathsCopy.hashCode()));
        assertThat(emptySparseCheckoutPaths, is(emptySparseCheckoutPathsCopy));
    }

    @Test
    void testToString() {
        assertThat(emptySparseCheckoutPaths.toString(), is("SparseCheckoutPaths{sparseCheckoutPaths=[]}"));
    }

    private static class MyCheckoutCommand implements CheckoutCommand {

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
