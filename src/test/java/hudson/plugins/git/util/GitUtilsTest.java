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

import edu.umd.cs.findbugs.annotations.NonNull;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import org.eclipse.jgit.lib.ObjectId;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * This test was a useful first draft test because it used the commit volume and
 * tag volume of the git plugin repository to quickly show the problem.
 *
 * This test is NOT a reasonable test to include in the final pull request
 * because it is brittle. It assumes the HEAD commit exists on the remote
 * origin. That is a reasonable assumption in many continuous integration
 * environments. That is not a reasonable assumption in a development
 * environment. These tests MUST be replaced.
 *
 * @author Mark Waite
 */
public class GitUtilsTest {

    private final EnvVars env;
    private final File gitDir;
    private final ObjectId remoteHeadId;
    private final ObjectId remotePriorId;
    private final Set<Branch> branches;
    private final Set<String> tagNames;
    private final String branchName;

    private final List<BranchSpec> currentBranchSpecList;
    private final Revision remoteHeadRevision;
    private final Revision remotePriorRevision;

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

        /* Tag names and remote branch names */
        this.tagNames = Collections.unmodifiableSet(oneTimeGitClient.getTagNames(null));
        // assertFalse("No tags in this repository - reduces test strength", this.tagNames.isEmpty());
        Set<Branch> remoteBranches = oneTimeGitClient.getRemoteBranches();
        this.branches = Collections.unmodifiableSet(remoteBranches);
        // assertFalse("No branches in this repository - reduces test strength", this.branches.isEmpty());
        String guessedBranchName = "HEAD"; // In case local branch matches no remote branch
        ObjectId headId = oneTimeGitClient.revParse("HEAD");
        for (Branch branch : this.branches) {
            // Choose one of the remote branch names that matches the current checked out version
            if (headId.equals(branch.getSHA1()) && !branch.getName().endsWith("/HEAD")) {
                guessedBranchName = branch.getName();
            }
        }
        assertThat(guessedBranchName, not(isEmptyString()));
        this.branchName = guessedBranchName;

        /* Assumes the HEAD commit exists on the remote */
        this.remoteHeadId = oneTimeGitClient.revParse(this.branchName);
        this.remoteHeadRevision = new Revision(remoteHeadId);
        this.remotePriorId = oneTimeGitClient.revParse(this.branchName + "^");
        this.remotePriorRevision = new Revision(remotePriorId);

        this.currentBranchSpecList = new ArrayList<>();
        // BranchSpec headSpec = new BranchSpec("HEAD");
        // currentBranchSpecList.add(headSpec);
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

    private void addExpectedLogSubstring(@NonNull String expectedLogSubstring) {
        this.expectedLogSubstrings.add(expectedLogSubstring);
    }

    private void addUnexpectedLogSubstring(@NonNull String unexpectedLogSubstring) {
        this.unexpectedLogSubstrings.add(unexpectedLogSubstring);
    }

    private void clearLogSubstringExpectations() {
        this.expectedLogSubstrings.clear();
        this.unexpectedLogSubstrings.clear();
    }

    private Set<String> getExpectedNames(@NonNull Set<Branch> branches, @NonNull Set<String> tagNames) {
        Set<String> names = new HashSet<>(branches.size() + tagNames.size());
        for (Branch branch : branches) {
            names.add(branch.getName());
        }
        for (String tagName : tagNames) {
            names.add("refs/tags/" + tagName);
        }
        return names;
    }

    private Set<String> getExpectedNames(@NonNull List<BranchSpec> branchSpecList) {
        Set<String> names = new HashSet<>(branchSpecList.size());
        for (BranchSpec branchSpec : branchSpecList) {
            names.add(branchSpec.getName());
        }
        return names;
    }

    private Set<String> getActualNames(@NonNull Collection<Revision> revisions) {
        Set<String> names = new HashSet<>(revisions.size());
        for (Revision revision : revisions) {
            for (Branch branch : revision.getBranches()) {
                names.add(branch.getName());
            }
        }
        return names;
    }

    @Test
    public void testGetAllBranchRevisions() throws Exception {
        Collection<Revision> allRevisions = gitUtils.getAllBranchRevisions();
        assertThat(allRevisions, hasItem(remoteHeadRevision));
        Set<String> expectedNames = getExpectedNames(branches, tagNames);
        Set<String> actualNames = getActualNames(allRevisions);
        assertThat(actualNames, is(expectedNames));
    }

    @Test
    public void testGetMatchingRevisions() throws Exception {
        Collection<Revision> allRevisions = gitUtils.getMatchingRevisions(currentBranchSpecList, env);
        // Collection<Revision> allRevisions = gitUtils.getAllBranchRevisions();
        assertThat(allRevisions, hasItem(remoteHeadRevision));
        Set<String> actualNames = getActualNames(allRevisions);
        Set<String> expectedNames = getExpectedNames(currentBranchSpecList);
        assertThat(actualNames, is(expectedNames));
    }

    @Test
    public void testGetMatchingRevisions_Duplicates() throws Exception {
        List<BranchSpec> duplicateBranchSpecList = new ArrayList<>();
        duplicateBranchSpecList.addAll(currentBranchSpecList);
        duplicateBranchSpecList.addAll(currentBranchSpecList);
        Collection<Revision> allRevisions = gitUtils.getMatchingRevisions(duplicateBranchSpecList, env);
        // Collection<Revision> allRevisions = gitUtils.getAllBranchRevisions();
        assertThat(allRevisions, hasItem(remoteHeadRevision));
        Set<String> actualNames = getActualNames(allRevisions);
        Set<String> expectedNames = getExpectedNames(currentBranchSpecList);
        assertThat(actualNames, is(expectedNames));
    }

    @Test
    public void testGetRevisionContainingBranch() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch(branchName);
        assertEquals(remoteHeadRevision, revision);
    }

    @Test
    public void testGetRevisionContainingBranchInvalidName() throws Exception {
        Revision revision = gitUtils.getRevisionContainingBranch(branchName + "-invalid-name");
        assertThat(revision, is(nullValue()));
    }

    @Test
    public void testGetRevisionForSHA1() throws Exception {
        Revision revision = gitUtils.getRevisionForSHA1(remoteHeadId);
        assertEquals(remoteHeadRevision, revision);
    }

    @Test
    public void testGetRevisionForSHA1PriorRevision() throws Exception {
        Revision revision = gitUtils.getRevisionForSHA1(remotePriorId);
        assertEquals(remotePriorRevision, revision);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List() {
        Revision result = gitUtils.sortBranchesForRevision(remoteHeadRevision, currentBranchSpecList);
        assertEquals(remoteHeadRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Prior() {
        Revision result = gitUtils.sortBranchesForRevision(remotePriorRevision, currentBranchSpecList);
        assertEquals(remotePriorRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_3args() {
        Revision result = gitUtils.sortBranchesForRevision(remoteHeadRevision, currentBranchSpecList, env);
        assertEquals(remoteHeadRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_3args_Prior() {
        Revision result = gitUtils.sortBranchesForRevision(remotePriorRevision, currentBranchSpecList, env);
        assertEquals(remotePriorRevision, result);
    }

    @Test
    public void testFilterTipBranches() throws Exception {
        Collection<Revision> multiRevisionList = new ArrayList<>();
        multiRevisionList.add(remoteHeadRevision);
        multiRevisionList.add(remotePriorRevision);
        Collection<Revision> filteredRevisions = new ArrayList<>();
        filteredRevisions.add(remoteHeadRevision);
        List<Revision> result = gitUtils.filterTipBranches(multiRevisionList);
        assertThat(result, is(filteredRevisions));
    }

    @Test
    public void testFilterTipBranchesNoRemovals() throws Exception {
        Collection<Revision> headRevisionList = new ArrayList<>();
        headRevisionList.add(remoteHeadRevision);
        List<Revision> result = gitUtils.filterTipBranches(headRevisionList);
        assertThat(result, is(headRevisionList));
    }

    @Test
    public void testFilterTipBranchesNoRemovalsNonTip() throws Exception {
        Collection<Revision> priorRevisionList = new ArrayList<>();
        priorRevisionList.add(remotePriorRevision);
        List<Revision> result = gitUtils.filterTipBranches(priorRevisionList);
        assertThat(result, is(priorRevisionList));
    }

    @Test
    public void testFixupNames() {
        String[] names = {"origin", "origin2", null, "", null};
        String[] urls = {
            "git://github.com/jenkinsci/git-plugin.git",
            "git@github.com:jenkinsci/git-plugin.git",
            "https://github.com/jenkinsci/git-plugin",
            "https://github.com/jenkinsci/git-plugin.git",
            "ssh://github.com/jenkinsci/git-plugin.git"
        };
        String[] expected = {"origin", "origin2", "origin1", "origin3", "origin4"};
        String[] actual = GitUtils.fixupNames(names, urls);
        assertArrayEquals(expected, actual);
    }
}
