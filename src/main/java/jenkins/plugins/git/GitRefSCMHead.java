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
import jenkins.scm.api.SCMHead;
import org.eclipse.jgit.lib.Constants;

/**
 * Represents a git reference outside of either {@link Constants#R_HEADS} or {@link Constants#R_TAGS}.
 *
 * @since 3.7.0
 */
public class GitRefSCMHead extends SCMHead implements BranchSpecSCMHead {

    /**
     * The reference, will have {@link AbstractGitSCMSource#REF_SPEC_REMOTE_NAME_PLACEHOLDER} substitution
     * applied.
     */
    @NonNull
    private final String branchSpec;

    /**
     * Constructor.
     *
     * @param name the name.
     * @param branchSpec  the branchSpec name, will have {@link AbstractGitSCMSource#REF_SPEC_REMOTE_NAME_PLACEHOLDER} substitution
     *             applied.
     */
    public GitRefSCMHead(@NonNull String name, @NonNull String branchSpec) {
        super(name);
        this.branchSpec = branchSpec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBranchSpec() {
        return branchSpec;
    }
}
