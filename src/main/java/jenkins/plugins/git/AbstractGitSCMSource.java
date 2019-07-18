/*
 * The MIT License
 *
 * Copyright (c) 2013-2017, CloudBees, Inc., Stephen Connolly, Amadeus IT Group.
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

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsNameProvider;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.RestrictedSince;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.GitUtils;
import hudson.scm.SCM;
import hudson.security.ACL;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import jenkins.plugins.git.traits.GitBrowserSCMSourceTrait;
import jenkins.plugins.git.traits.GitSCMExtensionTrait;
import jenkins.plugins.git.traits.GitToolSCMSourceTrait;
import jenkins.plugins.git.traits.RefSpecsSCMSourceTrait;
import jenkins.plugins.git.traits.RemoteNameSCMSourceTrait;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadCategory;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceEvent;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.trait.SCMSourceRequest;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMTrait;
import jenkins.scm.impl.trait.WildcardSCMHeadFilterTrait;
import jenkins.scm.impl.trait.WildcardSCMSourceFilterTrait;
import net.jcip.annotations.GuardedBy;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jenkinsci.plugins.gitclient.FetchCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;

/**
 * Base class for {@link SCMSource} implementations that produce {@link GitSCM} implementations.
 *
 * @since 2.0
 */
public abstract class AbstractGitSCMSource extends SCMSource {

    /**
     * The default remote name to use when configuring the ref specs to use with fetch operations.
     *
     * @since 3.4.0
     */
    public static final String DEFAULT_REMOTE_NAME = "origin";
    /**
     * The placeholder to use in ref spec templates in order to correctly ensure that the ref spec remote name
     * matches the remote name.
     * <p>
     * The template uses {@code @{...}} as that is an illegal sequence in a remote name
     *
     * @see <a href="https://github.com/git/git/blob/027a3b943b444a3e3a76f9a89803fc10245b858f/refs.c#L61-L68">git
     * source code rules on ref spec names</a>
     * @since 3.4.0
     */
    public static final String REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR = "@{remote}";
    /**
     * The regex for {@link #REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR}.
     *
     * @since 3.4.0
     */
    public static final String REF_SPEC_REMOTE_NAME_PLACEHOLDER = "(?i)"+Pattern.quote(REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR);
    /**
     * The default ref spec template.
     *
     * @since 3.4.0
     */
    public static final String REF_SPEC_DEFAULT =
            "+refs/heads/*:refs/remotes/" + REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR + "/*";

    /**
     * Keep one lock per cache directory. Lazy populated, but never purge, except on restart.
     */
    private static final ConcurrentMap<String, Lock> cacheLocks = new ConcurrentHashMap<>();

    private static final Logger LOGGER = Logger.getLogger(AbstractGitSCMSource.class.getName());

    public AbstractGitSCMSource() {
    }
    
    @Deprecated
    public AbstractGitSCMSource(String id) {
        setId(id);
    }

    @CheckForNull
    public abstract String getCredentialsId();

    /**
     * @return Git remote URL
     */
    public abstract String getRemote();

    /**
     * @deprecated use {@link WildcardSCMSourceFilterTrait}
     * @return the includes.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("3.4.0")
    public String getIncludes() {
        WildcardSCMHeadFilterTrait trait = SCMTrait.find(getTraits(), WildcardSCMHeadFilterTrait.class);
        return trait != null ? trait.getIncludes() : "*";
    }

    /**
     * @return the excludes.
     * @deprecated use {@link WildcardSCMSourceFilterTrait}
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("3.4.0")
    public String getExcludes() {
        WildcardSCMHeadFilterTrait trait = SCMTrait.find(getTraits(), WildcardSCMHeadFilterTrait.class);
        return trait != null ? trait.getExcludes() : "";
    }

    /**
     * Gets {@link GitRepositoryBrowser} to be used with this SCMSource.
     * @return Repository browser or {@code null} if the default tool should be used.
     * @since 2.5.1
     * @deprecated use {@link GitBrowserSCMSourceTrait}
     */
    @CheckForNull
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("3.4.0")
    public GitRepositoryBrowser getBrowser() {
        GitBrowserSCMSourceTrait trait = SCMTrait.find(getTraits(), GitBrowserSCMSourceTrait.class);
        return trait != null ? trait.getBrowser() : null;
    }

    /**
     * Gets Git tool to be used for this SCM Source.
     * @return Git Tool or {@code null} if the default tool should be used.
     * @since 2.5.1
     * @deprecated use {@link GitToolSCMSourceTrait}
     */
    @CheckForNull
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("3.4.0")
    public String getGitTool() {
        GitToolSCMSourceTrait trait = SCMTrait.find(getTraits(), GitToolSCMSourceTrait.class);
        return trait != null ? trait.getGitTool() : null;
    }

    /**
     * Gets list of extensions, which should be used with this branch source.
     * @return List of Extensions to be used. May be empty
     * @since 2.5.1
     * @deprecated use corresponding {@link GitSCMExtensionTrait} (and if there isn't one then likely the
     * {@link GitSCMExtension} is not appropriate to use in the context of a {@link SCMSource})
     */
    @NonNull
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("3.4.0")
    public List<GitSCMExtension> getExtensions() {
        List<GitSCMExtension> extensions = new ArrayList<>();
        for (SCMSourceTrait t : getTraits()) {
            if (t instanceof GitSCMExtensionTrait) {
                extensions.add(((GitSCMExtensionTrait) t).getExtension());
            }
        }
        return Collections.unmodifiableList(extensions);
    }

    /**
     * Returns the {@link SCMSourceTrait} instances for this {@link AbstractGitSCMSource}.
     * @return the {@link SCMSourceTrait} instances
     * @since 3.4.0
     */
    @NonNull
    public List<SCMSourceTrait> getTraits() {
        // Always return empty list (we expect subclasses to override)
        return Collections.emptyList();
    }

    /**
     * @deprecated use {@link RemoteNameSCMSourceTrait}
     * @return the remote name.
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("3.4.0")
    public String getRemoteName() {
        RemoteNameSCMSourceTrait trait = SCMTrait.find(getTraits(), RemoteNameSCMSourceTrait.class);
        return trait != null ? trait.getRemoteName() : DEFAULT_REMOTE_NAME;
    }

    /**
     * Resolves the {@link GitTool}.
     * @return the {@link GitTool}.
     * @deprecated use {@link #resolveGitTool(String)}.
     */
    @CheckForNull
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("3.4.0")
    protected GitTool resolveGitTool() {
        return resolveGitTool(getGitTool());
    }

    /**
     * Resolves the {@link GitTool}.
     * @param gitTool the {@link GitTool#getName()} to resolve.
     * @return the {@link GitTool}
     * @since 3.4.0
     * @deprecated Use {@link #resolveGitTool(String, TaskListener)} instead
     */
    @CheckForNull
    @Deprecated
    protected GitTool resolveGitTool(String gitTool) {
        return resolveGitTool(gitTool, TaskListener.NULL);
    }

    protected GitTool resolveGitTool(String gitTool, TaskListener listener) {
        final Jenkins jenkins = Jenkins.getInstance();
        return GitUtils.resolveGitTool(gitTool, jenkins, null, TaskListener.NULL);
    }

    private interface Retriever<T> {
        default T run(GitClient client, String remoteName) throws IOException, InterruptedException {
            throw new AbstractMethodError("Not implemented");
        }
    }

    private interface Retriever2<T> extends Retriever<T> {
        T run(GitClient client, String remoteName, FetchCommand fetch) throws IOException, InterruptedException;
    }

    @NonNull
    private <T, C extends GitSCMSourceContext<C, R>, R extends GitSCMSourceRequest> T doRetrieve(Retriever<T> retriever,
                                                                                                 @NonNull C context,
                                                                                                 @NonNull TaskListener listener,
                                                                                                 boolean prune)
            throws IOException, InterruptedException {
        return doRetrieve(retriever, context, listener, prune, false);
    }

    @NonNull
    private <T, C extends GitSCMSourceContext<C, R>, R extends GitSCMSourceRequest> T doRetrieve(Retriever<T> retriever,
                                                                                                 @NonNull C context,
                                                                                                 @NonNull TaskListener listener,
                                                                                                 boolean prune, boolean delayFetch)
            throws IOException, InterruptedException {
        String cacheEntry = getCacheEntry();
        Lock cacheLock = getCacheLock(cacheEntry);
        cacheLock.lock();
        try {
            File cacheDir = getCacheDir(cacheEntry);
            Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir);
            GitTool tool = resolveGitTool(context.gitTool(), listener);
            if (tool != null) {
                git.using(tool.getGitExe());
            }
            GitClient client = git.getClient();
            client.addDefaultCredentials(getCredentials());
            if (!client.hasGitRepo()) {
                listener.getLogger().println("Creating git repository in " + cacheDir);
                client.init();
            }
            String remoteName = context.remoteName();
            listener.getLogger().println("Setting " + remoteName + " to " + getRemote());
            client.setRemoteUrl(remoteName, getRemote());
            listener.getLogger().println((prune ? "Fetching & pruning " : "Fetching ") + remoteName + "...");
            FetchCommand fetch = client.fetch_();
            fetch = fetch.prune(prune);

            URIish remoteURI = null;
            try {
                remoteURI = new URIish(remoteName);
            } catch (URISyntaxException ex) {
                listener.getLogger().println("URI syntax exception for '" + remoteName + "' " + ex);
            }
            final FetchCommand fetchCommand = fetch.from(remoteURI, context.asRefSpecs());
            if (!delayFetch) {
                fetchCommand.execute();
            } else if (retriever instanceof Retriever2) {
                return ((Retriever2<T>)retriever).run(client, remoteName, fetchCommand);
            }
            return retriever.run(client, remoteName);
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    @Override
    protected SCMRevision retrieve(@NonNull final SCMHead head, @NonNull final TaskListener listener)
            throws IOException, InterruptedException {
        GitSCMSourceContext context = new GitSCMSourceContext<>(null, SCMHeadObserver.none()).withTraits(getTraits());
        GitSCMTelescope telescope = GitSCMTelescope.of(this);
        if (telescope != null) {
            String remote = getRemote();
            StandardUsernameCredentials credentials = getCredentials();
            telescope.validate(remote, credentials);
            return telescope.getRevision(remote, credentials, head);
        }
        //TODO write test using GitRefSCMHead
        return doRetrieve(new Retriever<SCMRevision>() {
                              @Override
                              public SCMRevision run(GitClient client, String remoteName) throws IOException, InterruptedException {
                                  if (head instanceof GitTagSCMHead) {
                                      try {
                                          ObjectId objectId = client.revParse(Constants.R_TAGS + head.getName());
                                          return new GitTagSCMRevision((GitTagSCMHead) head, objectId.name());
                                      } catch (GitException e) {
                                          // tag does not exist
                                          return null;
                                      }
                                  } else if (head instanceof GitBranchSCMHead) {
                                      for (Branch b : client.getRemoteBranches()) {
                                          String branchName = StringUtils.removeStart(b.getName(), remoteName + "/");
                                          if (branchName.equals(head.getName())) {
                                              return new GitBranchSCMRevision((GitBranchSCMHead)head, b.getSHA1String());
                                          }
                                      }
                                  } else if (head instanceof GitRefSCMHead) {
                                      try {
                                          ObjectId objectId = client.revParse(((GitRefSCMHead) head).getRef());
                                          return new GitRefSCMRevision((GitRefSCMHead)head, objectId.name());
                                      } catch (GitException e) {
                                          // ref could not be found
                                          return null;
                                      }
                                  } else {
                                      //Entering default/legacy git retrieve code path
                                      for (Branch b : client.getRemoteBranches()) {
                                          String branchName = StringUtils.removeStart(b.getName(), remoteName + "/");
                                          if (branchName.equals(head.getName())) {
                                              return new SCMRevisionImpl(head, b.getSHA1String());
                                          }
                                      }
                                  }
                                  return null;
                              }
                          },
                context,
                listener, /* we don't prune remotes here, as we just want one head's revision */false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressFBWarnings(value="SE_BAD_FIELD", justification="Known non-serializable this")
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria,
                            @NonNull SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            @NonNull final TaskListener listener)
            throws IOException, InterruptedException {
        final GitSCMSourceContext context =
                new GitSCMSourceContext<>(criteria, observer).withTraits(getTraits());
        final GitSCMTelescope telescope = GitSCMTelescope.of(this);
        if (telescope != null) {
            final String remote = getRemote();
            final StandardUsernameCredentials credentials = getCredentials();
            telescope.validate(remote, credentials);
            Set<GitSCMTelescope.ReferenceType> referenceTypes = new HashSet<>();
            if (context.wantBranches()) {
                referenceTypes.add(GitSCMTelescope.ReferenceType.HEAD);
            }
            if (context.wantTags()) {
                referenceTypes.add(GitSCMTelescope.ReferenceType.TAG);
            }
            //TODO JENKINS-51134 DiscoverOtherRefsTrait
            if (!referenceTypes.isEmpty()) {
                try (GitSCMSourceRequest request = context.newRequest(AbstractGitSCMSource.this, listener)) {
                    listener.getLogger().println("Listing remote references...");
                    Iterable<SCMRevision> revisions = telescope.getRevisions(remote, credentials, referenceTypes);
                    if (context.wantBranches()) {
                        listener.getLogger().println("Checking branches...");
                        int count = 0;
                        for (final SCMRevision revision : revisions) {
                            if (!(revision instanceof SCMRevisionImpl) || (revision instanceof GitTagSCMRevision)) {
                                continue;
                            }
                            count++;
                            if (request.process(revision.getHead(),
                                    new SCMSourceRequest.RevisionLambda<SCMHead, SCMRevisionImpl>() {
                                        @NonNull
                                        @Override
                                        public SCMRevisionImpl create(@NonNull SCMHead head)
                                                throws IOException, InterruptedException {
                                            listener.getLogger()
                                                    .println("  Checking branch " + revision.getHead().getName());
                                            return (SCMRevisionImpl) revision;
                                        }
                                    },
                                    new SCMSourceRequest.ProbeLambda<SCMHead, SCMRevisionImpl>() {
                                        @NonNull
                                        @Override
                                        public SCMSourceCriteria.Probe create(@NonNull SCMHead head,
                                                                              @NonNull SCMRevisionImpl revision)
                                                throws IOException, InterruptedException {
                                            return new TelescopingSCMProbe(telescope, remote, credentials, revision);
                                        }
                                    }, new SCMSourceRequest.Witness() {
                                        @Override
                                        public void record(@NonNull SCMHead head, SCMRevision revision,
                                                           boolean isMatch) {
                                            if (isMatch) {
                                                listener.getLogger().println("    Met criteria");
                                            } else {
                                                listener.getLogger().println("    Does not meet criteria");
                                            }
                                        }
                                    }
                            )) {
                                listener.getLogger().format("Processed %d branches (query complete)%n", count);
                                return;
                            }
                        }
                        listener.getLogger().format("Processed %d branches%n", count);
                    }
                    if (context.wantTags()) {
                        listener.getLogger().println("Checking tags...");
                        int count = 0;
                        for (final SCMRevision revision : revisions) {
                            if (!(revision instanceof GitTagSCMRevision)) {
                                continue;
                            }
                            count++;
                            if (request.process((GitTagSCMHead) revision.getHead(),
                                    new SCMSourceRequest.RevisionLambda<GitTagSCMHead, GitTagSCMRevision>() {
                                        @NonNull
                                        @Override
                                        public GitTagSCMRevision create(@NonNull GitTagSCMHead head)
                                                throws IOException, InterruptedException {
                                            listener.getLogger()
                                                    .println("  Checking tag " + revision.getHead().getName());
                                            return (GitTagSCMRevision) revision;
                                        }
                                    },
                                    new SCMSourceRequest.ProbeLambda<GitTagSCMHead, GitTagSCMRevision>() {
                                        @NonNull
                                        @Override
                                        public SCMSourceCriteria.Probe create(@NonNull final GitTagSCMHead head,
                                                                              @NonNull GitTagSCMRevision revision)
                                                throws IOException, InterruptedException {
                                            return new TelescopingSCMProbe(telescope, remote, credentials, revision);
                                        }
                                    }, new SCMSourceRequest.Witness() {
                                        @Override
                                        public void record(@NonNull SCMHead head, SCMRevision revision,
                                                           boolean isMatch) {
                                            if (isMatch) {
                                                listener.getLogger().println("    Met criteria");
                                            } else {
                                                listener.getLogger().println("    Does not meet criteria");
                                            }
                                        }
                                    }
                            )) {
                                listener.getLogger().format("Processed %d tags (query complete)%n", count);
                                return;
                            }
                        }
                        listener.getLogger().format("Processed %d tags%n", count);
                    }
                }
                return;
            }
        }
        doRetrieve(new Retriever2<Void>() {
            @Override
            public Void run(GitClient client, String remoteName, FetchCommand fetch) throws IOException, InterruptedException {
                final Map<String, ObjectId> remoteReferences;
                if (context.wantBranches() || context.wantTags() || context.wantOtherRefs()) {
                    listener.getLogger().println("Listing remote references...");
                    boolean headsOnly = !context.wantOtherRefs() && context.wantBranches();
                    boolean tagsOnly = !context.wantOtherRefs() && context.wantTags();
                    remoteReferences = client.getRemoteReferences(
                            client.getRemoteUrl(remoteName), null, headsOnly, tagsOnly
                    );
                } else {
                    remoteReferences = Collections.emptyMap();
                }
                fetch.execute();
                final Repository repository = client.getRepository();
                try (RevWalk walk = new RevWalk(repository);
                     GitSCMSourceRequest request = context.newRequest(AbstractGitSCMSource.this, listener)) {

                    if (context.wantBranches()) {
                        discoverBranches(repository, walk, request, remoteReferences);
                    }
                    if (context.wantTags()) {
                        discoverTags(repository, walk, request, remoteReferences);
                    }
                    if (context.wantOtherRefs()) {
                        discoverOtherRefs(repository, walk, request, remoteReferences,
                                (Collection<GitSCMSourceContext.RefNameMapping>)context.getRefNameMappings());
                    }
                }
                return null;
            }

            private void discoverOtherRefs(final Repository repository,
                                           final RevWalk walk, GitSCMSourceRequest request,
                                           Map<String, ObjectId> remoteReferences,
                                           Collection<GitSCMSourceContext.RefNameMapping> wantedRefs)
                    throws IOException, InterruptedException {
                listener.getLogger().println("Checking other refs...");
                walk.setRetainBody(false);
                int count = 0;
                for (final Map.Entry<String, ObjectId> ref : remoteReferences.entrySet()) {
                    if (ref.getKey().startsWith(Constants.R_HEADS) || ref.getKey().startsWith(Constants.R_TAGS)) {
                        continue;
                    }
                    for (GitSCMSourceContext.RefNameMapping otherRef : wantedRefs) {
                        if (!otherRef.matches(ref.getKey())) {
                            continue;
                        }
                        final String refName = otherRef.getName(ref.getKey());
                        if (refName == null) {
                            listener.getLogger().println("  Possible badly configured name mapping (" + otherRef.getName() + ") (for " + ref.getKey() + ") ignoring.");
                            continue;
                        }
                        count++;
                        if (request.process(new GitRefSCMHead(refName, ref.getKey()),
                                new SCMSourceRequest.IntermediateLambda<ObjectId>() {
                                    @Nullable
                                    @Override
                                    public ObjectId create() throws IOException, InterruptedException {
                                        listener.getLogger().println("  Checking ref " + refName + " (" + ref.getKey() + ")");
                                        return ref.getValue();
                                    }
                                },
                                new SCMSourceRequest.ProbeLambda<GitRefSCMHead, ObjectId>() {
                                    @NonNull
                                    @Override
                                    public SCMSourceCriteria.Probe create(@NonNull GitRefSCMHead head,
                                                                          @Nullable ObjectId revisionInfo)
                                            throws IOException, InterruptedException {
                                        RevCommit commit = walk.parseCommit(revisionInfo);
                                        final long lastModified = TimeUnit.SECONDS.toMillis(commit.getCommitTime());
                                        final RevTree tree = commit.getTree();
                                        return new TreeWalkingSCMProbe(refName, lastModified, repository, tree);
                                    }
                                }, new SCMSourceRequest.LazyRevisionLambda<GitRefSCMHead, SCMRevision, ObjectId>() {
                                    @NonNull
                                    @Override
                                    public SCMRevision create(@NonNull GitRefSCMHead head, @Nullable ObjectId intermediate)
                                            throws IOException, InterruptedException {
                                        return new GitRefSCMRevision(head, ref.getValue().name());
                                    }
                                }, new SCMSourceRequest.Witness() {
                                    @Override
                                    public void record(@NonNull SCMHead head, SCMRevision revision, boolean isMatch) {
                                        if (isMatch) {
                                            listener.getLogger().println("    Met criteria");
                                        } else {
                                            listener.getLogger().println("    Does not meet criteria");
                                        }
                                    }
                                }
                        )) {
                            listener.getLogger().format("Processed %d refs (query complete)%n", count);
                            return;
                        }
                        break;
                    }
                }
                listener.getLogger().format("Processed %d refs%n", count);

            }

            private void discoverBranches(final Repository repository,
                                          final RevWalk walk, GitSCMSourceRequest request,
                                          Map<String, ObjectId> remoteReferences)
                    throws IOException, InterruptedException {
                listener.getLogger().println("Checking branches...");
                walk.setRetainBody(false);
                int count = 0;
                for (final Map.Entry<String, ObjectId> ref : remoteReferences.entrySet()) {
                    if (!ref.getKey().startsWith(Constants.R_HEADS)) {
                        continue;
                    }
                    count++;
                    final String branchName = StringUtils.removeStart(ref.getKey(), Constants.R_HEADS);
                    if (request.process(new GitBranchSCMHead(branchName),
                            new SCMSourceRequest.IntermediateLambda<ObjectId>() {
                                @Nullable
                                @Override
                                public ObjectId create() throws IOException, InterruptedException {
                                    listener.getLogger().println("  Checking branch " + branchName);
                                    return ref.getValue();
                                }
                            },
                            new SCMSourceRequest.ProbeLambda<GitBranchSCMHead, ObjectId>() {
                                @NonNull
                                @Override
                                public SCMSourceCriteria.Probe create(@NonNull GitBranchSCMHead head,
                                                                      @Nullable ObjectId revisionInfo)
                                        throws IOException, InterruptedException {
                                    RevCommit commit = walk.parseCommit(revisionInfo);
                                    final long lastModified = TimeUnit.SECONDS.toMillis(commit.getCommitTime());
                                    final RevTree tree = commit.getTree();
                                    return new TreeWalkingSCMProbe(branchName, lastModified, repository, tree);
                                }
                            }, new SCMSourceRequest.LazyRevisionLambda<GitBranchSCMHead, SCMRevision, ObjectId>() {
                                @NonNull
                                @Override
                                public SCMRevision create(@NonNull GitBranchSCMHead head, @Nullable ObjectId intermediate)
                                        throws IOException, InterruptedException {
                                    return new GitBranchSCMRevision(head, ref.getValue().name());
                                }
                            }, new SCMSourceRequest.Witness() {
                                @Override
                                public void record(@NonNull SCMHead head, SCMRevision revision, boolean isMatch) {
                                    if (isMatch) {
                                        listener.getLogger().println("    Met criteria");
                                    } else {
                                        listener.getLogger().println("    Does not meet criteria");
                                    }
                                }
                            }
                    )) {
                        listener.getLogger().format("Processed %d branches (query complete)%n", count);
                        return;
                    }
                }
                listener.getLogger().format("Processed %d branches%n", count);
            }

            private void discoverTags(final Repository repository,
                                          final RevWalk walk, GitSCMSourceRequest request,
                                          Map<String, ObjectId> remoteReferences)
                    throws IOException, InterruptedException {
                listener.getLogger().println("Checking tags...");
                walk.setRetainBody(false);
                int count = 0;
                for (final Map.Entry<String, ObjectId> ref : remoteReferences.entrySet()) {
                    if (!ref.getKey().startsWith(Constants.R_TAGS)) {
                        continue;
                    }
                    count++;
                    final String tagName = StringUtils.removeStart(ref.getKey(), Constants.R_TAGS);
                    RevCommit commit = walk.parseCommit(ref.getValue());
                    final long lastModified = TimeUnit.SECONDS.toMillis(commit.getCommitTime());
                    if (request.process(new GitTagSCMHead(tagName, lastModified),
                            new SCMSourceRequest.IntermediateLambda<ObjectId>() {
                                @Nullable
                                @Override
                                public ObjectId create() throws IOException, InterruptedException {
                                    listener.getLogger().println("  Checking tag " + tagName);
                                    return ref.getValue();
                                }
                            },
                            new SCMSourceRequest.ProbeLambda<GitTagSCMHead, ObjectId>() {
                                @NonNull
                                @Override
                                public SCMSourceCriteria.Probe create(@NonNull GitTagSCMHead head,
                                                                      @Nullable ObjectId revisionInfo)
                                        throws IOException, InterruptedException {
                                    RevCommit commit = walk.parseCommit(revisionInfo);
                                    final long lastModified = TimeUnit.SECONDS.toMillis(commit.getCommitTime());
                                    final RevTree tree = commit.getTree();
                                    return new TreeWalkingSCMProbe(tagName, lastModified, repository, tree);
                                }
                            }, new SCMSourceRequest.LazyRevisionLambda<GitTagSCMHead, GitTagSCMRevision, ObjectId>() {
                                @NonNull
                                @Override
                                public GitTagSCMRevision create(@NonNull GitTagSCMHead head, @Nullable ObjectId intermediate)
                                        throws IOException, InterruptedException {
                                    return new GitTagSCMRevision(head, ref.getValue().name());
                                }
                            }, new SCMSourceRequest.Witness() {
                                @Override
                                public void record(@NonNull SCMHead head, SCMRevision revision, boolean isMatch) {
                                    if (isMatch) {
                                        listener.getLogger().println("    Met criteria");
                                    } else {
                                        listener.getLogger().println("    Does not meet criteria");
                                    }
                                }
                            }
                    )) {
                        listener.getLogger().format("Processed %d tags (query complete)%n", count);
                        return;
                    }
                }
                listener.getLogger().format("Processed %d tags%n", count);
            }
        }, context, listener, true, true);
    }

    /**
     * {@inheritDoc}
     */
    @CheckForNull
    @Override
    protected SCMRevision retrieve(@NonNull final String revision, @NonNull final TaskListener listener, @CheckForNull Item retrieveContext) throws IOException, InterruptedException {

        final GitSCMSourceContext context =
                new GitSCMSourceContext<>(null, SCMHeadObserver.none()).withTraits(getTraits());
        final GitSCMTelescope telescope = GitSCMTelescope.of(this);
        if (telescope != null) {
            final String remote = getRemote();
            final StandardUsernameCredentials credentials = getCredentials(retrieveContext);
            telescope.validate(remote, credentials);
            SCMRevision result = telescope.getRevision(remote, credentials, revision);
            if (result != null) {
                return result;
            }
            result = telescope.getRevision(remote, credentials, Constants.R_HEADS + revision);
            if (result != null) {
                return result;
            }
            result = telescope.getRevision(remote, credentials, Constants.R_TAGS + revision);
            if (result != null) {
                return result;
            }
            return null;
        }
        // first we need to figure out what the revision is. There are six possibilities:
        // 1.  A branch name (if we have that we can return quickly)
        // 2.  A tag name (if we have that we will need to fetch the tag to resolve the tag date)
        // 3.  A short/full revision hash that is the head revision of a branch (if we have that we can return quickly)
        // 4.  A remote refspec for example pull-requests/1/from
        // 5.  A short/full revision hash of a non default ref (non branch or tag but somewhere else under refs/)
        // 6.  A short revision hash that is the head revision of a branch (if we have that we can return quickly)
        // 7.  A short/full revision hash for a tag (we'll need to fetch the tag to resolve the tag date)
        // 8.  A short/full revision hash that is not the head revision of a branch (we'll need to fetch everything to
        // try and resolve the hash from the history of one of the heads)
        Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars));
        GitTool tool = resolveGitTool(context.gitTool(), listener);
        if (tool != null) {
            git.using(tool.getGitExe());
        }
        final GitClient client = git.getClient();
        client.addDefaultCredentials(getCredentials(retrieveContext));
        listener.getLogger().printf("Attempting to resolve %s from remote references...%n", revision);
        boolean headsOnly = !context.wantOtherRefs() && context.wantBranches();
        boolean tagsOnly = !context.wantOtherRefs() && context.wantTags();
        Map<String, ObjectId> remoteReferences = client.getRemoteReferences(
                getRemote(), null, headsOnly, tagsOnly
        );
        String tagName = null;
        Set<String> shortNameMatches = new TreeSet<>();
        String shortHashMatch = null;
        Set<String> fullTagMatches = new TreeSet<>();
        Set<String> fullHashMatches = new TreeSet<>();
        String fullHashMatch = null;
        GitRefSCMRevision candidateOtherRef = null;
        for (Map.Entry<String,ObjectId> entry: remoteReferences.entrySet()) {
            String name = entry.getKey();
            String rev = entry.getValue().name();
            if ("HEAD".equals(name)) {
                //Skip HEAD as it should only appear during testing, not for standard bare repos iirc
                continue;
            }
            if (name.equals(Constants.R_HEADS + revision)) {
                listener.getLogger().printf("Found match: %s revision %s%n", name, rev);
                // WIN!
                return new GitBranchSCMRevision(new GitBranchSCMHead(revision), rev);
            }
            if (name.equals(Constants.R_TAGS+revision)) {
                listener.getLogger().printf("Found match: %s revision %s%n", name, rev);
                // WIN but not the good kind
                tagName = revision;
                context.wantBranches(false);
                context.wantTags(true);
                context.withoutRefSpecs();
                break;
            }
            if (name.startsWith(Constants.R_HEADS) && revision.equalsIgnoreCase(rev)) {
                listener.getLogger().printf("Found match: %s revision %s%n", name, rev);
                // WIN!
                return new GitBranchSCMRevision(new GitBranchSCMHead(StringUtils.removeStart(name, Constants.R_HEADS)), rev);
            }
            if (name.startsWith(Constants.R_TAGS) && revision.equalsIgnoreCase(rev)) {
                listener.getLogger().printf("Candidate match: %s revision %s%n", name, rev);
                // WIN but let's see if a branch also matches as that would save a fetch
                fullTagMatches.add(name);
                continue;
            }
            if((Constants.R_REFS + revision.toLowerCase(Locale.ENGLISH)).equals(name.toLowerCase(Locale.ENGLISH))) {
                fullHashMatches.add(name);
                if (fullHashMatch == null) {
                    fullHashMatch = rev;
                }
                continue;
            }
            if (rev.toLowerCase(Locale.ENGLISH).equals(revision.toLowerCase(Locale.ENGLISH))) {
                fullHashMatches.add(name);
                if (fullHashMatch == null) {
                    fullHashMatch = rev;
                }
                //Since it was a full match then the shortMatch below will also match, so just skip it
                continue;
            }
            for (GitSCMSourceContext.RefNameMapping o : (Collection<GitSCMSourceContext.RefNameMapping>)context.getRefNameMappings()) {
                if (o.matches(revision, name, rev)) {
                    candidateOtherRef = new GitRefSCMRevision(new GitRefSCMHead(revision, name), rev);
                    break;
                }
            }
            if (rev.toLowerCase(Locale.ENGLISH).startsWith(revision.toLowerCase(Locale.ENGLISH))) {
                shortNameMatches.add(name);
                if (shortHashMatch == null) {
                    listener.getLogger().printf("Candidate partial match: %s revision %s%n", name, rev);
                    shortHashMatch = rev;
                } else {
                    listener.getLogger().printf("Candidate partial match: %s revision %s%n", name, rev);
                    listener.getLogger().printf("Cannot resolve ambiguous short revision %s%n", revision);
                    return null;
                }
            }
        }
        if (!fullTagMatches.isEmpty()) {
            // we just want a tag so we can do a minimal fetch
            String name = StringUtils.removeStart(fullTagMatches.iterator().next(), Constants.R_TAGS);
            listener.getLogger().printf("Selected match: %s revision %s%n", name, shortHashMatch);
            tagName = name;
            context.wantBranches(false);
            context.wantTags(true);
            context.withoutRefSpecs();
        }
        if (fullHashMatch != null) {
            //since this would have been skipped if this was a head or a tag we can just return whatever
            return new GitRefSCMRevision(new GitRefSCMHead(fullHashMatch, fullHashMatches.iterator().next()), fullHashMatch);
        }
        if (shortHashMatch != null) {
            // woot this seems unambiguous
            for (String name: shortNameMatches) {
                if (name.startsWith(Constants.R_HEADS)) {
                    listener.getLogger().printf("Selected match: %s revision %s%n", name, shortHashMatch);
                    // WIN it's also a branch
                    return new GitBranchSCMRevision(new GitBranchSCMHead(StringUtils.removeStart(name, Constants.R_HEADS)),
                            shortHashMatch);
                } else if (name.startsWith(Constants.R_TAGS)) {
                    tagName = StringUtils.removeStart(name, Constants.R_TAGS);
                    context.wantBranches(false);
                    context.wantTags(true);
                    context.withoutRefSpecs();
                }
            }
            if (tagName != null) {
                listener.getLogger().printf("Selected match: %s revision %s%n", tagName, shortHashMatch);
            } else {
                return new GitRefSCMRevision(new GitRefSCMHead(shortHashMatch, shortNameMatches.iterator().next()), shortHashMatch);
            }
        }
        if (candidateOtherRef != null) {
            return candidateOtherRef;
        }
        //if PruneStaleBranches it should take affect on the following retrievals
        boolean pruneRefs = context.pruneRefs();
        if (tagName != null) {
            listener.getLogger().println(
                    "Resolving tag commit... (remote references may be a lightweight tag or an annotated tag)");
            final String tagRef = Constants.R_TAGS+tagName;
            return doRetrieve(new Retriever<SCMRevision>() {
                                  @Override
                                  public SCMRevision run(GitClient client, String remoteName) throws IOException,
                                          InterruptedException {
                                      final Repository repository = client.getRepository();
                                      try (RevWalk walk = new RevWalk(repository)) {
                                          ObjectId ref = client.revParse(tagRef);
                                          RevCommit commit = walk.parseCommit(ref);
                                          long lastModified = TimeUnit.SECONDS.toMillis(commit.getCommitTime());
                                          listener.getLogger().printf("Resolved tag %s revision %s%n", revision,
                                                  ref.getName());
                                          return new GitTagSCMRevision(new GitTagSCMHead(revision, lastModified),
                                                  ref.name());
                                      }
                                  }
                              },
                    context,
                    listener, pruneRefs);
        }
        // Pokémon!... Got to catch them all
        listener.getLogger().printf("Could not find %s in remote references. "
                        + "Pulling heads to local for deep search...%n", revision);
        context.wantTags(true);
        context.wantBranches(true);

        return doRetrieve(new Retriever<SCMRevision>() {
                              @Override
                              public SCMRevision run(GitClient client, String remoteName) throws IOException, InterruptedException {
                                  ObjectId objectId;
                                  String hash;
                                  try {
                                      objectId = client.revParse(revision);
                                      if (objectId == null) {
                                          //just to be safe
                                          listener.error("Could not resolve %s", revision);
                                          return null;

                                      }
                                      hash = objectId.name();
                                      String candidatePrefix = Constants.R_REMOTES.substring(Constants.R_REFS.length())
                                              + context.remoteName() + "/";
                                      String name = null;
                                      for (Branch b: client.getBranchesContaining(hash, true)) {
                                          if (b.getName().startsWith(candidatePrefix)) {
                                              name = b.getName().substring(candidatePrefix.length());
                                              break;
                                          }
                                      }
                                      if (name == null) {
                                          listener.getLogger().printf("Could not find a branch containing commit %s%n",
                                                  hash);
                                          return null;
                                      }
                                      listener.getLogger()
                                              .printf("Selected match: %s revision %s%n", name, hash);
                                      return new GitBranchSCMRevision(new GitBranchSCMHead(name), hash);
                                  } catch (GitException x) {
                                      x.printStackTrace(listener.error("Could not resolve %s", revision));
                                      return null;
                                  }
                              }
                          },
                context,
                listener, pruneRefs);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected Set<String> retrieveRevisions(@NonNull final TaskListener listener, @CheckForNull Item retrieveContext) throws IOException, InterruptedException {

        final GitSCMSourceContext context =
                new GitSCMSourceContext<>(null, SCMHeadObserver.none()).withTraits(getTraits());
        final GitSCMTelescope telescope = GitSCMTelescope.of(this);
        if (telescope != null) {
            final String remote = getRemote();
            final StandardUsernameCredentials credentials = getCredentials(retrieveContext);
            telescope.validate(remote, credentials);
            Set<GitSCMTelescope.ReferenceType> referenceTypes = new HashSet<>();
            if (context.wantBranches()) {
                referenceTypes.add(GitSCMTelescope.ReferenceType.HEAD);
            }
            if (context.wantTags()) {
                referenceTypes.add(GitSCMTelescope.ReferenceType.TAG);
            }
            Set<String> result = new HashSet<>();
            for (SCMRevision r : telescope.getRevisions(remote, credentials, referenceTypes)) {
                if (r instanceof GitTagSCMRevision && context.wantTags()) {
                    result.add(r.getHead().getName());
                } else if (!(r instanceof GitTagSCMRevision) && context.wantBranches()) {
                    result.add(r.getHead().getName());
                }
            }
            return result;
        }
        Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars));
        GitTool tool = resolveGitTool(context.gitTool(), listener);
        if (tool != null) {
            git.using(tool.getGitExe());
        }
        GitClient client = git.getClient();
        client.addDefaultCredentials(getCredentials(retrieveContext));
        Set<String> revisions = new HashSet<>();
        if (context.wantBranches() || context.wantTags() || context.wantOtherRefs()) {
            listener.getLogger().println("Listing remote references...");
            boolean headsOnly = !context.wantOtherRefs() && context.wantBranches();
            boolean tagsOnly = !context.wantOtherRefs() && context.wantTags();
            Map<String, ObjectId> remoteReferences = client.getRemoteReferences(
                    getRemote(), null, headsOnly, tagsOnly
            );
            for (String name : remoteReferences.keySet()) {
                if (context.wantBranches()) {
                    if (name.startsWith(Constants.R_HEADS)) {
                        revisions.add(StringUtils.removeStart(name, Constants.R_HEADS));
                    }
                }
                if (context.wantTags()) {
                    if (name.startsWith(Constants.R_TAGS)) {
                        revisions.add(StringUtils.removeStart(name, Constants.R_TAGS));
                    }
                }
                if (context.wantOtherRefs() && (!name.startsWith(Constants.R_HEADS) || !name.startsWith(Constants.R_TAGS))) {
                    for (GitSCMSourceContext.RefNameMapping o : (Collection<GitSCMSourceContext.RefNameMapping>)context.getRefNameMappings()) {
                        if (o.matches(name)) {
                            final String revName = o.getName(name);
                            if (revName != null) {
                                revisions.add(revName);
                                break;
                            }
                        }
                    }
                }
            }
        }
        return revisions;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected List<Action> retrieveActions(@CheckForNull SCMSourceEvent event, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        final GitSCMTelescope telescope = GitSCMTelescope.of(this);
        if (telescope != null) {
            final String remote = getRemote();
            final StandardUsernameCredentials credentials = getCredentials();
            telescope.validate(remote, credentials);
            String target = telescope.getDefaultTarget(remote, credentials);
            if (target.startsWith(Constants.R_HEADS)) {
                // shorten standard names
                target = target.substring(Constants.R_HEADS.length());
            }
            List<Action> result = new ArrayList<>();
            if (StringUtils.isNotBlank(target)) {
                result.add(new GitRemoteHeadRefAction(getRemote(), target));
            }
            return result;
        }
        final GitSCMSourceContext context =
                new GitSCMSourceContext<>(null, SCMHeadObserver.none()).withTraits(getTraits());
        Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars));
        GitTool tool = resolveGitTool(context.gitTool(), listener);
        if (tool != null) {
            git.using(tool.getGitExe());
        }
        GitClient client = git.getClient();
        client.addDefaultCredentials(getCredentials());
        Map<String, String> symrefs = client.getRemoteSymbolicReferences(getRemote(), null);
        if (symrefs.containsKey(Constants.HEAD)) {
            // Hurrah! The Server is Git 1.8.5 or newer and our client has symref reporting
            String target = symrefs.get(Constants.HEAD);
            if (target.startsWith(Constants.R_HEADS)) {
                // shorten standard names
                target = target.substring(Constants.R_HEADS.length());
            }
            List<Action> result = new ArrayList<>();
            if (StringUtils.isNotBlank(target)) {
                result.add(new GitRemoteHeadRefAction(getRemote(), target));
            }
            return result;
        }
        // Ok, now we do it the old-school way... see what ref has the same hash as HEAD
        // I think we will still need to keep this code path even if JGit implements
        // https://bugs.eclipse.org/bugs/show_bug.cgi?id=514052 as there is always the potential that
        // the remote server is Git 1.8.4 or earlier, or that the local CLI git implementation is
        // older than git 2.8.0 (CentOS 6, CentOS 7, Debian 7, Debian 8, Ubuntu 14, and
        // Ubuntu 16)
        Map<String, ObjectId> remoteReferences = client.getRemoteReferences(getRemote(), null, false, false);
        if (remoteReferences.containsKey(Constants.HEAD)) {
            ObjectId head = remoteReferences.get(Constants.HEAD);
            Set<String> names = new TreeSet<>();
            for (Map.Entry<String, ObjectId> entry : remoteReferences.entrySet()) {
                if (entry.getKey().equals(Constants.HEAD)) continue;
                if (head.equals(entry.getValue())) {
                    names.add(entry.getKey());
                }
            }
            // if there is one and only one match, that's the winner
            if (names.size() == 1) {
                String target = names.iterator().next();
                if (target.startsWith(Constants.R_HEADS)) {
                    // shorten standard names
                    target = target.substring(Constants.R_HEADS.length());
                }
                List<Action> result = new ArrayList<>();
                if (StringUtils.isNotBlank(target)) {
                    result.add(new GitRemoteHeadRefAction(getRemote(), target));
                }
                return result;
            }
            // if there are multiple matches, prefer `master`
            if (names.contains(Constants.R_HEADS + Constants.MASTER)) {
                List<Action> result = new ArrayList<>();
                result.add(new GitRemoteHeadRefAction(getRemote(), Constants.MASTER));
                return result;
            }
        }
        // Give up, there's no way to get the primary branch
        return new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    protected List<Action> retrieveActions(@NonNull SCMHead head, @CheckForNull SCMHeadEvent event,
                                           @NonNull TaskListener listener) throws IOException, InterruptedException {
        SCMSourceOwner owner = getOwner();
        if (owner instanceof Actionable) {
            for (GitRemoteHeadRefAction a: ((Actionable) owner).getActions(GitRemoteHeadRefAction.class)) {
                if (getRemote().equals(a.getRemote())) {
                    if (head.getName().equals(a.getName())) {
                        return Collections.<Action>singletonList(new PrimaryInstanceMetadataAction());
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isCategoryEnabled(@NonNull SCMHeadCategory category) {
        if (super.isCategoryEnabled(category)) {
            for (SCMSourceTrait trait : getTraits()) {
                if (trait.isCategoryEnabled(category)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected String getCacheEntry() {
        return getCacheEntry(getRemote());
    }

    @SuppressFBWarnings(
            value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
            justification = "AbstractGitSCMSourceRetrieveHeadsTest mocking calls this with null Jenkins.getInstance()")
    protected static File getCacheDir(String cacheEntry) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return null;
        }
        File cacheDir = new File(new File(jenkins.getRootDir(), "caches"), cacheEntry);
        if (!cacheDir.isDirectory()) {
            boolean ok = cacheDir.mkdirs();
            if (!ok) {
                LOGGER.log(Level.WARNING, "Failed mkdirs of {0}", cacheDir);
            }
        }
        return cacheDir;
    }

    protected static Lock getCacheLock(String cacheEntry) {
        Lock cacheLock;
        while (null == (cacheLock = cacheLocks.get(cacheEntry))) {
            cacheLocks.putIfAbsent(cacheEntry, new ReentrantLock());
        }
        return cacheLock;
    }

    @CheckForNull
    protected StandardUsernameCredentials getCredentials() {
        return getCredentials(getOwner());
    }

    @CheckForNull
    private StandardUsernameCredentials getCredentials(@CheckForNull Item context) {
        String credentialsId = getCredentialsId();
        if (credentialsId == null) {
            return null;
        }
        return CredentialsMatchers
                .firstOrNull(
                        CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, context,
                                ACL.SYSTEM, URIRequirementBuilder.fromUri(getRemote()).build()),
                        CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId),
                                GitClient.CREDENTIALS_MATCHER));
    }

    /**
     * @return the ref specs.
     * @deprecated use {@link RefSpecsSCMSourceTrait}
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    @RestrictedSince("3.4.0")
    protected List<RefSpec> getRefSpecs() {
        return Collections.emptyList();
    }

    /**
     * Instantiates a new {@link GitSCMBuilder}.
     * Subclasses should override this method if they want to use a custom {@link GitSCMBuilder} or if they need
     * to pre-decorate the builder.
     *
     * @param head     the {@link SCMHead}.
     * @param revision the {@link SCMRevision}.
     * @return the {@link GitSCMBuilder}
     * @see #decorate(GitSCMBuilder) for post-decoration.
     */
    protected GitSCMBuilder<?> newBuilder(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
        return new GitSCMBuilder<>(head, revision, getRemote(), getCredentialsId());
    }

    /**
     * Performs final decoration of the {@link GitSCMBuilder}. This method is called by
     * {@link #build(SCMHead, SCMRevision)} immediately prior to returning {@link GitSCMBuilder#build()}.
     * Subclasses should override this method if they need to overrule builder behaviours defined by traits.
     *
     * @param builder the builder to decorate.
     */
    protected void decorate(GitSCMBuilder<?> builder) {
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SCM build(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
        GitSCMBuilder<?> builder = newBuilder(head, revision);
        if (MethodUtils.isOverridden(AbstractGitSCMSource.class, getClass(), "getExtensions")) {
            builder.withExtensions(getExtensions());
        }
        if (MethodUtils.isOverridden(AbstractGitSCMSource.class, getClass(), "getBrowser")) {
            builder.withBrowser(getBrowser());
        }
        if (MethodUtils.isOverridden(AbstractGitSCMSource.class, getClass(), "getGitTool")) {
            builder.withGitTool(getGitTool());
        }
        if (MethodUtils.isOverridden(AbstractGitSCMSource.class, getClass(), "getRefSpecs")) {
            List<String> specs = new ArrayList<>();
            for (RefSpec spec: getRefSpecs()) {
                specs.add(spec.toString());
            }
            builder.withoutRefSpecs().withRefSpecs(specs);
        }
        builder.withTraits(getTraits());
        decorate(builder);
        return builder.build();
    }

    /**
     * @return the {@link UserRemoteConfig} instances.
     * @deprecated use {@link GitSCMBuilder#asRemoteConfigs()}
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("3.4.0")
    protected List<UserRemoteConfig> getRemoteConfigs() {
        List<RefSpec> refSpecs = getRefSpecs();
        List<UserRemoteConfig> result = new ArrayList<>(refSpecs.size());
        String remote = getRemote();
        for (RefSpec refSpec : refSpecs) {
            result.add(new UserRemoteConfig(remote, getRemoteName(), refSpec.toString(), getCredentialsId()));
        }
        return result;
    }
    
    /**
     * Returns true if the branchName isn't matched by includes or is matched by excludes.
     * 
     * @param branchName name of branch to be tested
     * @return true if branchName is excluded or is not included
     * @deprecated use {@link WildcardSCMSourceFilterTrait}
     */
    @Deprecated
    @Restricted(DoNotUse.class)
    @RestrictedSince("3.4.0")
    protected boolean isExcluded (String branchName){
      return !Pattern.matches(getPattern(getIncludes()), branchName) || (Pattern.matches(getPattern(getExcludes()), branchName));
    }
    
    /**
     * Returns the pattern corresponding to the branches containing wildcards. 
     * 
     * @param branches branch names to evaluate
     * @return pattern corresponding to the branches containing wildcards
     */
    private String getPattern(String branches){
      StringBuilder quotedBranches = new StringBuilder();
      for (String wildcard : branches.split(" ")){
        StringBuilder quotedBranch = new StringBuilder();
        for(String branch : wildcard.split("(?=[*])|(?<=[*])")){
          if (branch.equals("*")) {
            quotedBranch.append(".*");
          } else if (!branch.isEmpty()) {
            quotedBranch.append(Pattern.quote(branch));
          }
        }
        if (quotedBranches.length()>0) {
          quotedBranches.append("|");
        }
        quotedBranches.append(quotedBranch);
      }
      return quotedBranches.toString();
    }

    /*package*/ static String getCacheEntry(String remote) {
        return "git-" + Util.getDigestOf(remote);
    }

    /**
     * Our implementation.
     */
    public static class SCMRevisionImpl extends SCMRevision {

        /**
         * The subversion revision.
         */
        private final String hash;

        public SCMRevisionImpl(SCMHead head, String hash) {
            super(head);
            this.hash = hash;
        }

        @Exported
        public String getHash() {
            return hash;
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

            SCMRevisionImpl that = (SCMRevisionImpl) o;

            return Objects.equals(hash, that.hash)
                    && Objects.equals(getHead(), that.getHead());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(hash, getHead());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return hash;
        }

    }

    public static class SpecificRevisionBuildChooser extends BuildChooser {

        private final Revision revision;

        public SpecificRevisionBuildChooser(SCMRevisionImpl revision) {
            ObjectId sha1 = ObjectId.fromString(revision.getHash());
            String name = revision.getHead().getName();
            this.revision = new Revision(sha1, Collections.singleton(new Branch(name, sha1)));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch, GitClient git,
                                                          TaskListener listener, BuildData buildData,
                                                          BuildChooserContext context)
                throws GitException, IOException, InterruptedException {
            return Collections.singleton(revision);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Build prevBuildForChangelog(String branch, @Nullable BuildData data, GitClient git,
                                           BuildChooserContext context) throws IOException, InterruptedException {
            // we have ditched that crazy multiple branch stuff from the regular GIT SCM.
            return data == null ? null : data.lastBuild;
        }

    }

    /**
     * A {@link SCMProbe} that uses a local cache of the repository.
     *
     * @since 3.6.1
     */
    @SuppressFBWarnings(value = { "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE",
                                  "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
                                  "NP_LOAD_OF_KNOWN_NULL_VALUE"
                                },
                        justification = "Java 11 generated code causes redundant nullcheck")
    private static class TreeWalkingSCMProbe extends SCMProbe {
        private final String name;
        private final long lastModified;
        private final Repository repository;
        private final RevTree tree;

        public TreeWalkingSCMProbe(String name, long lastModified, Repository repository, RevTree tree) {
            this.name = name;
            this.lastModified = lastModified;
            this.repository = repository;
            this.tree = tree;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() throws IOException {
            // no-op
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String name() {
            return name;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long lastModified() {
            return lastModified;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public SCMProbeStat stat(@NonNull String path) throws IOException {
            try (TreeWalk tw = TreeWalk.forPath(repository, path, tree)) {
                if (tw == null) {
                    return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                }
                FileMode fileMode = tw.getFileMode(0);
                if (fileMode == FileMode.MISSING) {
                    return SCMProbeStat.fromType(SCMFile.Type.NONEXISTENT);
                }
                if (fileMode == FileMode.EXECUTABLE_FILE) {
                    return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                }
                if (fileMode == FileMode.REGULAR_FILE) {
                    return SCMProbeStat.fromType(SCMFile.Type.REGULAR_FILE);
                }
                if (fileMode == FileMode.SYMLINK) {
                    return SCMProbeStat.fromType(SCMFile.Type.LINK);
                }
                if (fileMode == FileMode.TREE) {
                    return SCMProbeStat.fromType(SCMFile.Type.DIRECTORY);
                }
                return SCMProbeStat.fromType(SCMFile.Type.OTHER);
            }
        }
    }

    /**
     * A {@link SCMProbe} that uses a {@link GitSCMTelescope}.
     *
     * @since 3.6.1
     */
    private static class TelescopingSCMProbe extends SCMProbe {
        /**
         * Our telescope.
         */
        @NonNull
        private final GitSCMTelescope telescope;
        /**
         * The repository URL.
         */
        @NonNull
        private final String remote;
        /**
         * The credentials to use.
         */
        @CheckForNull
        private final StandardCredentials credentials;
        /**
         * The revision this probe operates on.
         */
        @NonNull
        private final SCMRevision revision;
        /**
         * The filesystem (lazy init).
         */
        @GuardedBy("this")
        @CheckForNull
        private SCMFileSystem fileSystem;
        /**
         * The last modified timestamp (lazy init).
         */
        @GuardedBy("this")
        @CheckForNull
        private Long lastModified;

        /**
         * Constructor.
         * @param telescope the telescope.
         * @param remote the repository URL
         * @param credentials the credentials to use.
         * @param revision the revision to probe.
         */
        public TelescopingSCMProbe(GitSCMTelescope telescope, String remote, StandardCredentials credentials,
                                   SCMRevision revision) {
            this.telescope = telescope;
            this.remote = remote;
            this.credentials = credentials;
            this.revision = revision;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public SCMProbeStat stat(@NonNull String path) throws IOException {
            try {
                SCMFileSystem fileSystem;
                synchronized (this) {
                    if (this.fileSystem == null) {
                        this.fileSystem = telescope.build(remote, credentials, revision.getHead(), revision);
                    }
                    fileSystem = this.fileSystem;
                }
                if (fileSystem == null) {
                    throw new IOException("Cannot connect to " + remote + " as "
                            + (credentials == null ? "anonymous" : CredentialsNameProvider.name(credentials)));
                }
                return SCMProbeStat.fromType(fileSystem.child(path).getType());
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized void close() throws IOException {
            if (fileSystem != null) {
                fileSystem.close();
            }
            fileSystem = null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String name() {
            return revision.getHead().getName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long lastModified() {
            synchronized (this) {
                if (lastModified != null) {
                    return lastModified;
                }
            }
            long lastModified;
            try {
                lastModified = telescope.getTimestamp(remote, credentials, revision.getHead());
            } catch (IOException | InterruptedException e) {
                return -1L;
            }
            synchronized (this) {
                this.lastModified = lastModified;
            }
            return lastModified;
        }
    }
}
