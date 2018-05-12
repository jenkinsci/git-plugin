/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.model.Item;
import java.util.ArrayList;
import java.util.List;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Mark Waite
 */
public class GitBranchSpecifierColumnTest {

    public GitBranchSpecifierColumnTest() {
    }

    @Test
    public void testGetBranchSpecifierNull() {
        Item item = null;
        GitBranchSpecifierColumn branchSpecifierColumn = new GitBranchSpecifierColumn();
        List<String> result = branchSpecifierColumn.getBranchSpecifier(item);
        assertThat(result, is(emptyCollectionOf(String.class)));
    }

    @Test
    public void testBreakOutString() {
        List<String> branches = new ArrayList<>();
        final String MASTER_BRANCH = "master";
        branches.add(MASTER_BRANCH);
        String DEVELOP_BRANCH = "develop";
        branches.add(DEVELOP_BRANCH);
        GitBranchSpecifierColumn branchSpecifier = new GitBranchSpecifierColumn();
        String result = branchSpecifier.breakOutString(branches);
        assertEquals(MASTER_BRANCH + ", " + DEVELOP_BRANCH, result);
    }

    @Test
    public void testBreakOutStringEmpty() {
        List<String> branches = new ArrayList<>();
        GitBranchSpecifierColumn branchSpecifier = new GitBranchSpecifierColumn();
        String result = branchSpecifier.breakOutString(branches);
        assertEquals("", result);
    }

    @Test
    public void testBreakOutStringNull() {
        List<String> branches = null;
        GitBranchSpecifierColumn branchSpecifier = new GitBranchSpecifierColumn();
        String result = branchSpecifier.breakOutString(branches);
        assertEquals(null, result);
    }
}
