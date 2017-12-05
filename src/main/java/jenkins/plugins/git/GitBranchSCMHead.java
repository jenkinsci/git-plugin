/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadMigration;
import jenkins.scm.api.SCMRevision;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Represents a Git Branch in {@code refs/origin/} (but uses
 * {@link AbstractGitSCMSource#REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR} so that it correctly handles when the remote
 * name is customized.)
 *
 * @since 3.7.0
 */
public class GitBranchSCMHead extends SCMHead implements BranchSpecSCMHead {

    /**
     * Constructor.
     *
     * @param name the name.
     */
    public GitBranchSCMHead(@NonNull String name) {
        super(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBranchSpec() {
        return Constants.R_REFS + AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR + "/" + getName();
    }

    /**
     * Migrate legacy bare {@link SCMHead} usage to {@link GitBranchSCMHead}.
     *
     * @since 3.7.0
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static class FixBareSCMHead
            extends SCMHeadMigration<GitSCMSource, SCMHead, AbstractGitSCMSource.SCMRevisionImpl> {

        /**
         * Constructor.
         */
        public FixBareSCMHead() {
            super(GitSCMSource.class, SCMHead.class, AbstractGitSCMSource.SCMRevisionImpl.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SCMHead migrate(@NonNull GitSCMSource source, @NonNull SCMHead head) {
            return new GitBranchSCMHead(head.getName());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SCMRevision migrate(@NonNull GitSCMSource source,
                                   @NonNull AbstractGitSCMSource.SCMRevisionImpl revision) {
            return new GitBranchSCMRevision(new GitBranchSCMHead(revision.getHead().getName()), revision.getHash());
        }
    }
}
