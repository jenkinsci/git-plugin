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
package hudson.plugins.git;

import hudson.model.Run;
import hudson.scm.RepositoryBrowser;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import org.junit.Test;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;

public class GitChangeSetListTest {

    private final GitChangeSetList emptyChangeSetList;
    private GitChangeSetList changeSetList;
    private GitChangeSet changeSet;

    public GitChangeSetListTest() {
        RepositoryBrowser<?> browser = null;
        Run build = null;
        emptyChangeSetList = new GitChangeSetList(build, browser, new ArrayList<>(), null);
    }

    @Before
    public void createGitChangeSetList() {
        RepositoryBrowser<?> browser = null;
        Run build = null;
        List<GitChangeSet> logs = new ArrayList<>();
        List<String> changeSetText = new ArrayList<>();
        changeSet = new GitChangeSet(changeSetText, true);
        assertTrue(logs.add(changeSet));
        assertThat(changeSet.getParent(), is(nullValue()));
        changeSetList = new GitChangeSetList(build, browser, logs, null);
        assertThat(changeSet.getParent(), is(changeSetList));
    }

    @Test
    public void testIsEmptySet() {
        assertFalse(changeSetList.isEmptySet());
    }

    @Test
    public void testIsEmptySetReallyEmpty() {
        assertTrue(emptyChangeSetList.isEmptySet());
    }

    @Test
    public void testIterator() {
        Iterator<GitChangeSet> iterator = changeSetList.iterator();
        GitChangeSet firstChangeSet = iterator.next();
        assertThat(firstChangeSet, is(changeSet));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testIteratorReallyE() {
        Iterator<GitChangeSet> iterator = emptyChangeSetList.iterator();
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testGetLogs() {
        List<GitChangeSet> result = changeSetList.getLogs();
        assertThat(result, contains(changeSet));
    }

    @Test
    public void testGetLogsReallyEmpty() {
        List<GitChangeSet> result = emptyChangeSetList.getLogs();
        assertThat(result, is(empty()));
    }

    @Test
    public void testGetKind() {
        assertThat(changeSetList.getKind(), is("git"));
    }
}
