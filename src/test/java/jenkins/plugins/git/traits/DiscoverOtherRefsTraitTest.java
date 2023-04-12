/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.plugins.git.traits;

import org.junit.Test;

import static org.junit.Assert.*;

public class DiscoverOtherRefsTraitTest {

    @Test
    public void getFullRefSpec() throws Exception {
        DiscoverOtherRefsTrait t = new DiscoverOtherRefsTrait("refs/custom/*");
        assertEquals("+refs/custom/*:refs/remotes/@{remote}/custom/*", t.getFullRefSpec());
    }

    @Test
    public void getNameMapping() throws Exception {
        DiscoverOtherRefsTrait t = new DiscoverOtherRefsTrait("refs/bobby/*");
        assertEquals("bobby-@{1}", t.getNameMapping());
        t = new DiscoverOtherRefsTrait("refs/bobby/*/merge");
        assertEquals("bobby-@{1}", t.getNameMapping());
        t = new DiscoverOtherRefsTrait("refs/*");
        assertEquals("other-@{1}", t.getNameMapping());
        t = new DiscoverOtherRefsTrait("refs/bobby/all");
        assertEquals("other-ref", t.getNameMapping());
    }

}
