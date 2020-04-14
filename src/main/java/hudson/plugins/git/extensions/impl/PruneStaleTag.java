/*
 * The MIT License
 *
 * Copyright (c) 2020 Nikolas Falco
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
package hudson.plugins.git.extensions.impl;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Prune stale local tags that do not exist on any remote.
 *
 * @author Nikolas Falco
 * @since 4.3.0
 */
public class PruneStaleTag extends GitSCMExtension {

    private static final String TAG_REF = "refs/tags/";
    private final boolean pruneTags;

    /**
     * Control pruning of tags that exist in the local repository but
     * not in any remote repository.  If pruneTags is true, then local
     * tags will be deleted if no corresponding tag exists in at least
     * one of the remote repositories.
     *
     * @param pruneTags if true, tags not found in any remote are deleted from local repository
     */
    @DataBoundConstructor
    public PruneStaleTag(boolean pruneTags) {
        this.pruneTags = pruneTags;
    }

    /**
     * Needed for pipeline syntax generator.
     *
     * @return {@code true} if this extension is enable, {@code false} otherwise.
     */
    public boolean getPruneTags() {
        return pruneTags;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decorateFetchCommand(GitSCM scm,
                                     @CheckForNull Run<?, ?> run,
                                     GitClient git,
                                     TaskListener listener,
                                     FetchCommand cmd) throws IOException, InterruptedException, GitException {

        if (!pruneTags) {
            return;
        }

        listener.getLogger().println("Pruning obsolete local tags that do not exist on remotes");

        // all local tag are marked for removal unless match a tag on any remote
        Map<String, ObjectId> localTags = new HashMap<>();
        git.getTags().forEach(t -> localTags.put(t.getName(), t.getSHA1()));

        if (localTags.isEmpty()) {
            return;
        }

        List<RemoteConfig> remoteRepos = run == null ? scm.getRepositories() : scm.getParamExpandedRepos(run, listener);
        for (RemoteConfig remote : remoteRepos) {
            for (URIish url : remote.getURIs()) {
                Map<String, ObjectId> refs = git.getRemoteReferences(url.toASCIIString(), null, false, true);
                for (Entry<String, ObjectId> ref : refs.entrySet()) {
                    String remoteTagName = ref.getKey();
                    if (remoteTagName.startsWith(TAG_REF)) {
                        remoteTagName = remoteTagName.substring(TAG_REF.length());
                    }
                    ObjectId remoteTagId = ref.getValue();
                    if (localTags.containsKey(remoteTagName) && localTags.get(remoteTagName).equals(remoteTagId)) {
                        localTags.remove(remoteTagName);
                    }
                }
            }
        }

        for (String localTag : localTags.keySet()) {
            git.deleteTag(localTag);
        }
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

        PruneStaleTag that = (PruneStaleTag) o;

        return pruneTags == that.pruneTags;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(pruneTags);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "PruneStaleTag { " + pruneTags + " }";
    }

    @Symbol("pruneTags")
    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        @Override
        public GitSCMExtension newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new PruneStaleTag(true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Prune stale tags";
        }
    }
}
