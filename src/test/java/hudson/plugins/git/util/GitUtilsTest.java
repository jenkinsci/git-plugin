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
package hudson.plugins.git.util;

import hudson.EnvVars;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.Revision;
import hudson.util.LogTaskListener;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.not;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class GitUtilsTest {

    private final EnvVars env;
    private final File gitDir;
    private final ObjectId headId;
    private final ObjectId priorId;
    private final Set<String> tagNames;
    private final Set<Branch> branches;
    private final String branchName;

    private final List<BranchSpec> currentBranchSpecList;
    private final Revision headRevision;
    private final Revision priorRevision;

    private LogHandler handler;
    private LogTaskListener listener;
    private int logCount = 0;

    private GitUtils gitUtils;
    private GitClient gitClient;

    private static final String LOGGING_STARTED = "*** Logging started ***";
    private final List<String> expectedLogSubstrings;
    private final List<String> unexpectedLogSubstrings;

    public GitUtilsTest() throws IOException, InterruptedException {
        this.env = new EnvVars();

        String gitDirName = ".";
        this.gitDir = new File(gitDirName);

        /* Assemble information about the repository in this directory */
        GitClient oneTimeGitClient = Git.with(StreamTaskListener.NULL, env).in(gitDir).using("git").getClient();
        this.headId = oneTimeGitClient.revParse("HEAD");
        this.headRevision = new Revision(headId);
        this.priorId = oneTimeGitClient.revParse("HEAD^");
        this.priorRevision = new Revision(priorId);

        /* Tag names and remote branch names */
        this.tagNames = Collections.unmodifiableSet(oneTimeGitClient.getTagNames(null));
        assertFalse("No tags in this repository - reduces test strength", this.tagNames.isEmpty());
        Set<Branch> remoteBranches = oneTimeGitClient.getRemoteBranches();
        this.branches = Collections.unmodifiableSet(remoteBranches);
        assertFalse("No branches in this repository - reduces test strength", this.branches.isEmpty());
        String guessedBranchName = "";
        for (Branch branch : this.branches) {
            // Choose one of the remote branch names that matches the current checked out version
            if (branch.getSHA1().equals(this.headRevision.getSha1()) && !branch.getName().endsWith("/HEAD")) {
                guessedBranchName = branch.getName();
            }
        }
        assertThat(guessedBranchName, not(isEmptyString()));
        this.branchName = guessedBranchName;

        this.currentBranchSpecList = new ArrayList<>();
        // BranchSpec headSpec = new BranchSpec("HEAD");
        // headBranchSpec.add(headSpec);
        BranchSpec currentBranchSpec = new BranchSpec(guessedBranchName);
        currentBranchSpecList.add(currentBranchSpec);

        this.expectedLogSubstrings = new ArrayList<>();
        this.unexpectedLogSubstrings = new ArrayList<>();
    }

    @Before
    public void setUp() throws IOException, InterruptedException {
        Logger logger = Logger.getLogger(this.getClass().getPackage().getName() + "-" + logCount++);
        handler = new LogHandler();
        handler.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        this.listener = new hudson.util.LogTaskListener(logger, Level.ALL);
        listener.getLogger().println(LOGGING_STARTED);

        clearLogSubstringExpectations();

        this.gitClient = Git.with(listener, env).in(gitDir).using("git").getClient();
        this.gitUtils = new GitUtils(listener, gitClient);
    }

    @After
    public void tearDown() {
        try {
            String messages = StringUtils.join(handler.getMessages(), ";");
            assertTrue("Logging not started: " + messages, handler.containsMessageSubstring(LOGGING_STARTED));
            for (String unexpectedLogSubstring : unexpectedLogSubstrings) {
                assertFalse("Found unexpected '" + unexpectedLogSubstring + "' in " + messages,
                        handler.containsMessageSubstring(unexpectedLogSubstring));
            }
            for (String expectedLogSubstring : expectedLogSubstrings) {
                assertTrue("No expected '" + expectedLogSubstring + "' in " + messages,
                        handler.containsMessageSubstring(expectedLogSubstring));
            }
        } finally {
            handler.close();
        }
    }

    private void addExpectedLogSubstring(String expectedLogSubstring) {
        this.expectedLogSubstrings.add(expectedLogSubstring);
    }

    private void addUnexpectedLogSubstring(String unexpectedLogSubstring) {
        this.unexpectedLogSubstrings.add(unexpectedLogSubstring);
    }

    private void clearLogSubstringExpectations() {
        this.expectedLogSubstrings.clear();
        this.unexpectedLogSubstrings.clear();
    }

    /* The test is unacceptably slow because it calls getAllBranchRevisions */
    // @Test
    public void testGetAllBranchRevisions() throws Exception {
        Collection<Revision> allRevisions = gitUtils.getAllBranchRevisions();
        assertThat(allRevisions, hasItem(headRevision));
        // These assertions will not detect issues if a branch and tag have the same name
        // Expect every tag to be logged by rev-parse
        for (String tag : tagNames) {
            addExpectedLogSubstring(tag);
        }
        // Expect every remote branch name to be logged by rev-parse
        for (Branch branch : branches) {
            addExpectedLogSubstring(branch.getName());
        }
    }

    @Test
    public void testGetMatchingRevisions() throws Exception {
        Collection<Revision> allRevisions = gitUtils.getMatchingRevisions(currentBranchSpecList, env);
        assertThat(allRevisions, hasItem(headRevision));
        // These assertions will fail if a branch and tag have the same name
        // Expect no tag to be logged by rev-parse
        for (String tag : tagNames) {
            addUnexpectedLogSubstring(tag);
        }
        // Expect every remote branch name to be logged by rev-parse
        for (Branch branch : branches) {
            addExpectedLogSubstring(branch.getName());
        }
    }

    /* The test is unacceptably slow because it calls getAllBranchRevisions */
    // @Test
    public void testGetRevisionContainingBranch() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch(branchName);
        assertEquals(headRevision, revision);
    }

    /* The test is unacceptably slow because it calls getAllBranchRevisions */
    @Test
    public void testGetRevisionForSHA1() throws Exception {
        Revision revision = gitUtils.getRevisionForSHA1(headId);
        assertEquals(headRevision, revision);
    }

    /* The test is unacceptably slow because it calls getAllBranchRevisions */
    @Test
    public void testGetRevisionForSHA1PriorRevision() throws Exception {
        Revision revision = gitUtils.getRevisionForSHA1(priorId);
        assertEquals(priorRevision, revision);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, currentBranchSpecList);
        assertEquals(headRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Prior() {
        Revision result = gitUtils.sortBranchesForRevision(priorRevision, currentBranchSpecList);
        assertEquals(priorRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_3args() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, currentBranchSpecList, env);
        assertEquals(headRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_3args_Prior() {
        Revision result = gitUtils.sortBranchesForRevision(priorRevision, currentBranchSpecList, env);
        assertEquals(priorRevision, result);
    }

    @Test
    public void testFilterTipBranches() throws Exception {
        Collection<Revision> multiRevisionList = new ArrayList<>();
        multiRevisionList.add(headRevision);
        multiRevisionList.add(priorRevision);
        Collection<Revision> filteredRevisions = new ArrayList<>();
        filteredRevisions.add(headRevision);
        List<Revision> result = gitUtils.filterTipBranches(multiRevisionList);
        assertThat(result, is(filteredRevisions));
    }

    @Test
    public void testFilterTipBranchesNoRemovals() throws Exception {
        Collection<Revision> headRevisionList = new ArrayList<>();
        headRevisionList.add(headRevision);
        List<Revision> result = gitUtils.filterTipBranches(headRevisionList);
        assertThat(result, is(headRevisionList));
    }

    @Test
    public void testFilterTipBranchesNoRemovalsNonTip() throws Exception {
        Collection<Revision> priorRevisionList = new ArrayList<>();
        priorRevisionList.add(priorRevision);
        List<Revision> result = gitUtils.filterTipBranches(priorRevisionList);
        assertThat(result, is(priorRevisionList));
    }

    @Test
    public void testFixupNames() {
        String[] names = {"origin", "origin2", null, null};
        String[] urls = {
            "https://github.com/jenkinsci/git-plugin",
            "https://github.com/jenkinsci/git-plugin.git",
            "git@github.com:jenkinsci/git-plugin.git",
            "ssh://github.com/jenkinsci/git-plugin.git"
        };
        String[] expected = {"origin", "origin2", "origin1", "origin3"};
        String[] actual = GitUtils.fixupNames(names, urls);
        assertArrayEquals(expected, actual);
    }
}
