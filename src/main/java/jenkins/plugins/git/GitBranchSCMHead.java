/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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
 *
 */
package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

public class GitBranchSCMHead extends SCMHead implements GitSCMHeadMixin {
    /**
     * Constructor.
     *
     * @param name the name.
     */
    public GitBranchSCMHead(@NonNull String name) {
        super(name);
    }

    @Override
    public String getRef() {
        return Constants.R_HEADS + getName();
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static class SCMHeadMigrationImpl extends SCMHeadMigration<GitSCMSource, SCMHead, AbstractGitSCMSource.SCMRevisionImpl> {

        public SCMHeadMigrationImpl() {
            super(GitSCMSource.class, SCMHead.class, AbstractGitSCMSource.SCMRevisionImpl.class);
        }

        @Override
        public SCMHead migrate(@NonNull GitSCMSource source, @NonNull SCMHead head) {
            return new GitBranchSCMHead(head.getName());
        }

        @Override
        public SCMRevision migrate(@NonNull GitSCMSource source, @NonNull AbstractGitSCMSource.SCMRevisionImpl revision) {
            if (revision.getHead().getClass() == SCMHead.class) {
                return new GitBranchSCMRevision((GitBranchSCMHead) migrate(source, revision.getHead()),
                        revision.getHash());
            }
            return null;
        }

    }
}
