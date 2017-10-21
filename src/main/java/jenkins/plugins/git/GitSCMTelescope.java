/*
 * The MIT License
 *
 * Copyright (c) 2017 Stephen Connolly
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

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCM;
import hudson.security.ACL;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.mixin.TagSCMHead;
import org.eclipse.jgit.lib.Constants;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * An implementation of this extension point allows {@link AbstractGitSCMSource} to examine a repository from a distance
 * without requiring a local checkout.
 *
 * @since TODO
 */
public abstract class GitSCMTelescope extends SCMFileSystem.Builder {

    /**
     * Returns the {@link GitSCMTelescope} to use for the specified {@link GitSCM} or {@code null} if none match.
     * @param source the {@link GitSCM}.
     * @return the {@link GitSCMTelescope} to use for the specified {@link GitSCM} or {@code null}
     */
    @CheckForNull
    public static GitSCMTelescope of(@NonNull GitSCM source) {
        for (SCMFileSystem.Builder b : ExtensionList.lookup(SCMFileSystem.Builder.class)) {
            if (b instanceof GitSCMTelescope && b.supports(source)) {
                return (GitSCMTelescope) b;
            }
            if (b instanceof GitSCMFileSystem.BuilderImpl) {
                // telescopes must come before the fallback GitSCMFileSystem.BuilderImpl otherwise they would
                // not prevent a local checkout
                break;
            }
        }
        return null;
    }

    /**
     * Returns the {@link GitSCMTelescope} to use for the specified {@link AbstractGitSCMSource} or {@code null} if
     * none match.
     *
     * @param source the {@link AbstractGitSCMSource}.
     * @return the {@link GitSCMTelescope} to use for the specified {@link AbstractGitSCMSource} or {@code null}
     */
    @CheckForNull
    public static GitSCMTelescope of(@NonNull AbstractGitSCMSource source) {
        for (SCMFileSystem.Builder b : ExtensionList.lookup(SCMFileSystem.Builder.class)) {
            if (b instanceof GitSCMTelescope && b.supports(source)) {
                return (GitSCMTelescope) b;
            }
            if (GitSCMFileSystem.BuilderImpl.class.equals(b.getClass())) {
                // telescopes must come before the fallback GitSCMFileSystem.BuilderImpl otherwise they would
                // not prevent a local checkout
                break;
            }
        }
        return null;
    }

    /**
     * Checks if this {@link SCMFileSystem.Builder} supports the repository at the supplied remote URL.
     * <strong>NOTE:</strong> returning {@code true} mandates that {@link #build(Item, SCM, SCMRevision)} and
     * {@link #build(SCMSource, SCMHead, SCMRevision)} must return non-{@code null} when they are configured
     * with the corresponding repository URL.
     *
     * @param remote the repository URL.
     * @return {@code true} if and only if the remote URL is supported by this {@link GitSCMTelescope}.
     */
    public abstract boolean supports(@NonNull String remote);

    /**
     * Checks if the supplied credentials are valid against the specified repository URL.
     *
     * @param remote      the repository URL.
     * @param credentials the credentials or {@code null} to validate anonymous connection.
     * @throws IOException          if the operation failed due to an IO error or invalid credentials.
     * @throws InterruptedException if the operation was interrupted.
     */
    public abstract void validate(@NonNull String remote, @CheckForNull StandardCredentials credentials)
            throws IOException, InterruptedException;

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean supports(@NonNull SCM source) {
        if (source instanceof GitSCM) {
            // we only support the GitSCM if the branch is completely unambiguous
            GitSCM git = (GitSCM) source;
            List<UserRemoteConfig> configs = git.getUserRemoteConfigs();
            List<BranchSpec> branches = git.getBranches();
            return configs.size() == 1
                    && supports(configs.get(0).getUrl())
                    && branches.size() == 1
                    && !branches.get(0).getName().contains("*");
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean supports(@NonNull SCMSource source) {
        return source instanceof AbstractGitSCMSource && source.getOwner() != null && supports(
                ((AbstractGitSCMSource) source).getRemote());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final SCMFileSystem build(@NonNull SCMSource source, @NonNull SCMHead head, @CheckForNull SCMRevision rev)
            throws IOException, InterruptedException {
        SCMSourceOwner owner = source.getOwner();
        if (source instanceof AbstractGitSCMSource && owner != null && supports(
                ((AbstractGitSCMSource) source).getRemote())) {
            AbstractGitSCMSource git = (AbstractGitSCMSource) source;
            String remote = git.getRemote();
            StandardUsernameCredentials credentials = git.getCredentials();
            validate(remote, credentials);
            return build(remote, credentials, head, rev);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final SCMFileSystem build(@NonNull Item owner, @NonNull SCM scm, SCMRevision rev)
            throws IOException, InterruptedException {
        if (scm instanceof GitSCM) {
            // we only support the GitSCM if the branch is completely unambiguous
            GitSCM git = (GitSCM) scm;
            List<UserRemoteConfig> configs = git.getUserRemoteConfigs();
            List<BranchSpec> branches = git.getBranches();
            if (configs.size() == 1 && supports(configs.get(0).getUrl())
                    && branches.size() == 1 && !branches.get(0).getName().contains("*")) {
                UserRemoteConfig config = configs.get(0);
                StandardCredentials credentials;
                String credentialsId = config.getCredentialsId();
                String remote = config.getUrl();
                if (credentialsId != null) {
                    List<StandardUsernameCredentials> urlCredentials = CredentialsProvider
                            .lookupCredentials(StandardUsernameCredentials.class, owner,
                                    owner instanceof Queue.Task
                                            ? Tasks.getAuthenticationOf((Queue.Task) owner)
                                            : ACL.SYSTEM, URIRequirementBuilder.fromUri(remote).build());
                    credentials = CredentialsMatchers.firstOrNull(
                            urlCredentials,
                            CredentialsMatchers
                                    .allOf(CredentialsMatchers.withId(credentialsId), GitClient.CREDENTIALS_MATCHER)
                    );
                } else {
                    credentials = null;
                }
                validate(remote, credentials);
                SCMHead head;
                if (rev == null) {
                    String name = branches.get(0).getName();
                    if (name.startsWith(Constants.R_TAGS)) {
                        head = new GitTagSCMHead(
                                name.substring(Constants.R_TAGS.length()),
                                getTimestamp(remote, credentials, name)
                        );
                    } else if (name.startsWith(Constants.R_HEADS)) {
                        head = new SCMHead(name.substring(Constants.R_HEADS.length()));
                    } else {
                        if (name.startsWith(config.getName() + "/")) {
                            head = new SCMHead(name.substring(config.getName().length() + 1));
                        } else {
                            head = new SCMHead(name);
                        }
                    }
                } else {
                    head = rev.getHead();
                }
                return build(remote, credentials, head, rev);
            }
        }
        return null;
    }

    /**
     * Given a {@link SCM} this should try to build a corresponding {@link SCMFileSystem} instance that
     * reflects the content at the specified {@link SCMRevision}. If the {@link SCM} is supported but not
     * for a fixed revision, best effort is acceptable as the most capable {@link SCMFileSystem} will be returned
     * to the caller.
     *
     * @param remote      the repository URL
     * @param credentials the credentials or {@code null} for an anonymous connection.
     * @param head        the specified {@link SCMHead}
     * @param rev         the specified {@link SCMRevision}.
     * @return the corresponding {@link SCMFileSystem} or {@code null} if this builder cannot create a {@link
     * SCMFileSystem} for the specified repository URL.
     * @throws IOException          if the attempt to create a {@link SCMFileSystem} failed due to an IO error
     *                              (such as the remote system being unavailable)
     * @throws InterruptedException if the attempt to create a {@link SCMFileSystem} was interrupted.
     */
    @CheckForNull
    protected abstract SCMFileSystem build(@NonNull String remote, @CheckForNull StandardCredentials credentials,
                                           @NonNull SCMHead head, @CheckForNull SCMRevision rev)
            throws IOException, InterruptedException;

    /**
     * Retrives the timestamp of the specified reference or object hash.
     *
     * @param remote      the repository URL.
     * @param credentials the credentials or {@code null} for an anonymous connection.
     * @param refOrHash   the reference or hash.
     * @return the timestamp.
     * @throws IOException          if the operation failed due to an IO error.
     * @throws InterruptedException if the operation was interrupted.
     */
    public abstract long getTimestamp(@NonNull String remote, @CheckForNull StandardCredentials credentials,
                                      @NonNull String refOrHash) throws IOException, InterruptedException;

    /**
     * Retrives the current revision of the specified reference or object hash.
     *
     * @param remote      the repository URL.
     * @param credentials the credentials or {@code null} for an anonymous connection.
     * @param refOrHash   the reference or hash.
     * @return the revision or {@code null} if the reference or hash does not exist.
     * @throws IOException          if the operation failed due to an IO error.
     * @throws InterruptedException if the operation was interrupted.
     */
    @CheckForNull
    public abstract SCMRevision getRevision(@NonNull String remote,
                                            @CheckForNull StandardCredentials credentials,
                                            @NonNull String refOrHash)
            throws IOException, InterruptedException;

    /**
     * Retrives the timestamp of the specified reference or object hash.
     *
     * @param remote      the repository URL.
     * @param credentials the credentials or {@code null} for an anonymous connection.
     * @param head        the head.
     * @return the timestamp.
     * @throws IOException          if the operation failed due to an IO error.
     * @throws InterruptedException if the operation was interrupted.
     */
    public long getTimestamp(@NonNull String remote, @CheckForNull StandardCredentials credentials,
                             @NonNull SCMHead head) throws IOException, InterruptedException {
        if ((head instanceof TagSCMHead)) {
            return getTimestamp(remote, credentials, Constants.R_TAGS + head.getName());
        } else {
            return getTimestamp(remote, credentials, Constants.R_HEADS + head.getName());
        }
    }

    /**
     * Retrives the current revision of the specified head.
     *
     * @param remote      the repository URL.
     * @param credentials the credentials or {@code null} for an anonymous connection.
     * @param head        the head.
     * @return the revision or {@code null} if the head does not exist.
     * @throws IOException          if the operation failed due to an IO error.
     * @throws InterruptedException if the operation was interrupted.
     */
    @CheckForNull
    public SCMRevision getRevision(@NonNull String remote,
                                   @CheckForNull StandardCredentials credentials,
                                   @NonNull SCMHead head)
            throws IOException, InterruptedException {
        if ((head instanceof TagSCMHead)) {
            return getRevision(remote, credentials, Constants.R_TAGS + head.getName());
        } else {
            return getRevision(remote, credentials, Constants.R_HEADS + head.getName());
        }
    }

    /**
     * Retrives the current revisions of the specified repository.
     *
     * @param remote      the repository URL.
     * @param credentials the credentials or {@code null} for an anonymous connection.
     * @return the revisions.
     * @throws IOException          if the operation failed due to an IO error.
     * @throws InterruptedException if the operation was interrupted.
     */
    public final Iterable<SCMRevision> getRevisions(@NonNull String remote,
                                                    @CheckForNull StandardCredentials credentials)
            throws IOException, InterruptedException {
        return getRevisions(remote, credentials, EnumSet.allOf(ReferenceType.class));
    }

    /**
     * Retrives the current revisions of the specified repository.
     *
     * @param remote         the repository URL.
     * @param credentials    the credentials or {@code null} for an anonymous connection.
     * @param referenceTypes the types of reference to retrieve revisions of.
     * @return the revisions.
     * @throws IOException          if the operation failed due to an IO error.
     * @throws InterruptedException if the operation was interrupted.
     */
    public abstract Iterable<SCMRevision> getRevisions(@NonNull String remote,
                                                       @CheckForNull StandardCredentials credentials,
                                                       @NonNull Set<ReferenceType> referenceTypes)
            throws IOException, InterruptedException;

    /**
     * Retrives the default target of the specified repository.
     *
     * @param remote      the repository URL.
     * @param credentials the credentials or {@code null} for an anonymous connection.
     * @return the default target of the repository.
     * @throws IOException          if the operation failed due to an IO error.
     * @throws InterruptedException if the operation was interrupted.
     */
    public abstract String getDefaultTarget(@NonNull String remote,
                                            @CheckForNull StandardCredentials credentials)
            throws IOException, InterruptedException;

    /**
     * The potential types of reference supported by a {@link GitSCMTelescope}.
     */
    enum ReferenceType {
        /**
         * A regular reference.
         */
        HEAD,
        /**
         * A tag reference.
         */
        TAG;
    }
}
