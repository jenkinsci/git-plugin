/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.io.IOException;
import java.util.List;
import jenkins.scm.api.mixin.TagSCMHead;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.CloneCommand;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * Used to reset the default clone behaviour for {@link GitSCM} instances created by {@link GitSCMBuilder}.
 * Does not have a descriptor as we do not expect this extension to be user-visible.
 * With this extension, we anticipate:
 * <ul>
 * <li>tags will not be cloned or fetched</li>
 * <li>refspecs will be honoured on clone</li>
 * </ul>
 *
 * @since 3.4.0
 */
public class GitSCMSourceDefaults extends GitSCMExtension {

    /**
     * Determines whether tags should be fetched... only relevant if we implement support for {@link TagSCMHead}.
     */
    private final boolean includeTags;

    /**
     * Constructor.
     *
     * @param includeTags {@code true} to request fetching tags.
     */
    public GitSCMSourceDefaults(boolean includeTags) {
        this.includeTags = includeTags;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GitSCMSourceDefaults that = (GitSCMSourceDefaults) o;

        return includeTags == that.includeTags;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return (includeTags ? 1 : 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "GitSCMSourceDefaults{" +
                "includeTags=" + includeTags +
                '}';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateCloneCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener,
                                     CloneCommand cmd) throws IOException, InterruptedException, GitException {
        listener.getLogger()
                .printf("Cloning with configured refspecs honoured and %s tags%n", includeTags ? "with" : "without");
        RemoteConfig rc = scm.getRepositories().get(0);
        List<RefSpec> refspecs = rc.getFetchRefSpecs();
        cmd.refspecs(refspecs);
        cmd.tags(includeTags);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateFetchCommand(GitSCM scm, GitClient git, TaskListener listener, FetchCommand cmd)
            throws IOException, InterruptedException, GitException {
        listener.getLogger()
                .printf("Fetching %s tags%n", includeTags ? "with" : "without");
        cmd.tags(includeTags);
    }
}
