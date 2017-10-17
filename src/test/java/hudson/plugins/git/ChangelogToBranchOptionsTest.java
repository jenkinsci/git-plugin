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

import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.*;

public class ChangelogToBranchOptionsTest {

    private final ChangelogToBranchOptions options;
    private final String compareRemote;
    private final String compareTarget;

    public ChangelogToBranchOptionsTest() {
        compareRemote = "origin";
        compareTarget = "feature/new-thing";
        options = new ChangelogToBranchOptions(compareRemote, compareTarget);
    }

    @Test
    public void testGetCompareRemote() {
        assertThat(options.getCompareRemote(), is(compareRemote));
    }

    @Test
    public void testGetCompareTarget() {
        assertThat(options.getCompareTarget(), is(compareTarget));
    }

    @Test
    public void testGetRef() {
        assertThat(options.getRef(), is(compareRemote + "/" + compareTarget));
    }

    @Test
    public void testAlternateConstructor() {
        ChangelogToBranchOptions newOptions = new ChangelogToBranchOptions(options);
        assertThat(newOptions.getCompareRemote(), is(options.getCompareRemote()));
        assertThat(newOptions.getCompareTarget(), is(options.getCompareTarget()));
        assertThat(newOptions, is(not(options))); // Does not implement equals
    }
}
