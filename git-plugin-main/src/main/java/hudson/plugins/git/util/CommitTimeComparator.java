/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.plugins.git.Revision;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Comparator;

/**
 * Compares {@link Revision} by their timestamps.
 * 
 * @author Kohsuke Kawaguchi
 */
@SuppressFBWarnings(value="SE_COMPARATOR_SHOULD_BE_SERIALIZABLE", justification="Known non-serializable field critical part of class")
public class CommitTimeComparator implements Comparator<Revision> {
    private final RevWalk walk;

    public CommitTimeComparator(Repository r) {
        walk = new RevWalk(r);
    }

    public int compare(Revision lhs, Revision rhs) {
        return compare(time(lhs),time(rhs));
    }

    private int time(Revision r) {
        // parseCommit caches parsed Commit objects, so this is reasonably efficient.
        try {
            return walk.parseCommit(r.getSha1()).getCommitTime();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse "+r.getSha1(),e);
        }
    }

    private int compare(int lhs, int rhs) {
        if (lhs<rhs)    return -1;
        if (lhs>rhs)    return 1;
        return 0;
    }
}
