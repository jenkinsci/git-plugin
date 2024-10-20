/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.UserRemoteConfig;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.security.ACL;
import hudson.util.LogTaskListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.ChangelogCommand;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * Base implementation of {@link SCMFileSystem}.
 *
 * @since 3.0.2
 */
public class GitSCMFileSystem extends SCMFileSystem {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(GitSCMFileSystem.class.getName());

    private final String cacheEntry;
    private final TaskListener listener;
    private final String remote;
    private final String head;
    private final GitClient client;
    private final ObjectId commitId;

    /**
     * Constructor.
     *
     * @param client the client
     * @param remote the remote GIT URL
     * @param head   identifier for the head commit to be referenced
     * @param rev    the revision.
     * @throws IOException on I/O error
     * @throws InterruptedException on thread interruption
     */
    protected GitSCMFileSystem(GitClient client, String remote, final String head, @CheckForNull
            AbstractGitSCMSource.SCMRevisionImpl rev) throws IOException, InterruptedException {
        super(rev);
        this.remote = remote;
        this.head = head;
        cacheEntry = AbstractGitSCMSource.getCacheEntry(remote);
        listener = new LogTaskListener(LOGGER, Level.FINER);
        this.client = client;
        commitId = rev == null ? invoke((Repository repository) -> repository.findRef(head).getObjectId()) : ObjectId.fromString(rev.getHash());
    }

    @Override
    public AbstractGitSCMSource.SCMRevisionImpl getRevision() {
        return (AbstractGitSCMSource.SCMRevisionImpl) super.getRevision();
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        return invoke((Repository repository) -> {
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(commitId);
                return TimeUnit.SECONDS.toMillis(commit.getCommitTime());
            }
        });
    }

    @NonNull
    @Override
    public SCMFile getRoot() {
        return new GitSCMFile(this);
    }

    /*package*/ ObjectId getCommitId() {
        return commitId;
    }

    /**
     * Called with an {@link FSFunction} callback with a singleton repository
     * cache lock.
     *
     * An example usage might be:
     *
     * <pre>{@code
     *      return fs.invoke(new GitSCMFileSystem.FSFunction<byte[]>() {
     *          public byte[] invoke(Repository repository) throws IOException, InterruptedException {
     *              Git activeRepo = getClonedRepository(repository);
     *              File repoDir = activeRepo.getRepository().getDirectory().getParentFile();
     *              System.out.println("Repo cloned to: " + repoDir.getCanonicalPath());
     *              try {
     *                  File f = new File(repoDir, filePath);
     *                  if (f.canRead()) {
     *                      return IOUtils.toByteArray(new FileInputStream(f));
     *                  }
     *                  return null;
     *              } finally {
     *                  FileUtils.deleteDirectory(repoDir);
     *              }
     *          }
     *      });
     * }</pre>
     *
     * @param <V> return type
     * @param function callback executed with a locked repository
     * @return whatever you return from the provided function
     * @throws IOException if there is an I/O error
     * @throws InterruptedException if interrupted
     */
    public <V> V invoke(final FSFunction<V> function) throws IOException, InterruptedException {
        Lock cacheLock = AbstractGitSCMSource.getCacheLock(cacheEntry);
        cacheLock.lock();
        try {
            File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry);
            if (cacheDir == null || !cacheDir.isDirectory()) {
                throw new IOException("Closed");
            }
            return client.withRepository((Repository repository, VirtualChannel virtualChannel) -> function.invoke(repository));
        } catch (GitException x) {
            throw new IOException(x);
        } finally {
            cacheLock.unlock();
        }
    }

    @Override
    public boolean changesSince(@CheckForNull SCMRevision revision, @NonNull OutputStream changeLogStream)
            throws UnsupportedOperationException, IOException, InterruptedException {
        AbstractGitSCMSource.SCMRevisionImpl rev = getRevision();
        if (Objects.equals(rev, revision)) {
            // special case where somebody is asking one of two stupid questions:
            // 1. what has changed between the latest and the latest
            // 2. what has changed between the current revision and the current revision
            return false;
        }
        Lock cacheLock = AbstractGitSCMSource.getCacheLock(cacheEntry);
        cacheLock.lock();
        try {
            File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry);
            if (cacheDir == null || !cacheDir.isDirectory()) {
                throw new IOException("Closed");
            }
            boolean executed = false;
            ChangelogCommand changelog = client.changelog();
            try (Writer out = new OutputStreamWriter(changeLogStream, StandardCharsets.UTF_8)) {
                changelog.includes(commitId);
                ObjectId fromCommitId;
                if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl impl) {
                    fromCommitId = ObjectId.fromString(impl.getHash());
                    changelog.excludes(fromCommitId);
                } else {
                    fromCommitId = null;
                }
                changelog.to(out).max(GitSCM.MAX_CHANGELOG).execute();
                executed = true;
                return !commitId.equals(fromCommitId);
            } finally {
                if (!executed) {
                    changelog.abort();
                }
                changeLogStream.close();
            }
        } catch (GitException ge) {
            throw new IOException("Unable to retrieve changes", ge);
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Simple callback that is used with
     * {@link #invoke(jenkins.plugins.git.GitSCMFileSystem.FSFunction)}
     * in order to provide a locked view of the Git repository
     * @param <V> the return type
     */
    public interface FSFunction<V> {
        /**
         * Called with a lock on the repository in order to perform some
         * operations that might result in changes and necessary re-indexing
         * @param repository the bare git repository
         * @return value to return from {@link #invoke(jenkins.plugins.git.GitSCMFileSystem.FSFunction)}
         * @throws IOException if there is an I/O error
         * @throws InterruptedException if interrupted
         */
        V invoke(Repository repository) throws IOException, InterruptedException;
    }

    @Extension(ordinal = Short.MIN_VALUE)
    public static class BuilderImpl extends SCMFileSystem.Builder {

        @Override
        public boolean supports(SCM source) {
            return source instanceof GitSCM gscm
                    && gscm.getUserRemoteConfigs().size() == 1
                    && gscm.getBranches().size() == 1
                    && !gscm.getBranches().get(0).getName().equals("*") // JENKINS-57587
                    && (
                        gscm.getBranches().get(0).getName().matches(
                            "^((\\Q" + Constants.R_HEADS + "\\E.*)|([^/]+)|(\\*/[^/*]+(/[^/*]+)*))$"
                        )
                        || gscm.getBranches().get(0).getName().matches(
                            "^((\\Q" + Constants.R_TAGS + "\\E.*)|([^/]+)|(\\*/[^/*]+(/[^/*]+)*))$"
                        )
                    );
            // we only support where the branch spec is obvious and not a wildcard
        }

        @Override
        public boolean supports(SCMSource source) {
            return source instanceof AbstractGitSCMSource;
        }

        @Override
        public boolean supportsDescriptor(SCMDescriptor descriptor) {
            return descriptor instanceof GitSCM.DescriptorImpl;
        }

        @Override
        public boolean supportsDescriptor(SCMSourceDescriptor descriptor) {
            return AbstractGitSCMSource.class.isAssignableFrom(descriptor.clazz);
        }

        static class HeadNameResult {
            final String headName;
            final String prefix;

            private HeadNameResult(String headName, String prefix) {
                this.headName = headName;
                this.prefix = prefix;
            }

            static HeadNameResult calculate(@NonNull BranchSpec branchSpec,
                                            @CheckForNull SCMRevision rev,
                                            @CheckForNull EnvVars env) {
                String branchSpecExpandedName = branchSpec.getName();
                if (env != null) {
                    branchSpecExpandedName = env.expand(branchSpecExpandedName);
                }

                String prefix = Constants.R_HEADS;
                if (branchSpecExpandedName.startsWith(Constants.R_TAGS)) {
                    prefix = Constants.R_TAGS;
                }

                String headName;
                if (rev != null && env != null) {
                    headName = env.expand(rev.getHead().getName());
                } else {
                    if (branchSpecExpandedName.startsWith(prefix)) {
                        headName = branchSpecExpandedName.substring(prefix.length());
                    } else if (branchSpecExpandedName.startsWith("*/")) {
                        headName = branchSpecExpandedName.substring(2);
                    } else {
                        headName = branchSpecExpandedName;
                    }
                }
                return new HeadNameResult(headName, prefix);
            }
        }

        @Override
        public SCMFileSystem build(@NonNull Item owner, @NonNull SCM scm, @CheckForNull SCMRevision rev)
                throws IOException, InterruptedException {
            return build(owner, scm, rev, null);
        }

        @Override
        public SCMFileSystem build(@NonNull Item owner, @NonNull SCM scm, @CheckForNull SCMRevision rev,
                                   @CheckForNull Run<?,?> _build)
                throws IOException, InterruptedException {
            if (rev != null && !(rev instanceof AbstractGitSCMSource.SCMRevisionImpl)) {
                return null;
            }
            if (!(scm instanceof GitSCM)) {
                return null; // Spotbugs warns about unchecked cast without this check
            }
            GitSCM gitSCM = (GitSCM) scm;
            UserRemoteConfig config = gitSCM.getUserRemoteConfigs().get(0);
            BranchSpec branchSpec = gitSCM.getBranches().get(0);
            String remote = config.getUrl();
            TaskListener listener = new LogTaskListener(LOGGER, Level.FINE);
            if (remote == null) {
                listener.getLogger().println("Git remote url is null");
                return null;
            }

            EnvVars env = null;
            if (_build != null) {
                env = _build.getEnvironment(listener);
            }

            String cacheEntry = AbstractGitSCMSource.getCacheEntry(remote);
            Lock cacheLock = AbstractGitSCMSource.getCacheLock(cacheEntry);
            cacheLock.lock();
            try {
                File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry);
                Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir);
                GitTool tool = gitSCM.resolveGitTool(listener);
                if (tool != null) {
                    git.using(tool.getGitExe());
                }
                GitClient client = git.getClient();
                String credentialsId = config.getCredentialsId();
                if (credentialsId != null) {
                    StandardCredentials credential = CredentialsMatchers.firstOrNull(
                            CredentialsProvider.lookupCredentialsInItem(
                                StandardUsernameCredentials.class,
                                owner,
                                ACL.SYSTEM2,
                                URIRequirementBuilder.fromUri(remote).build()
                            ),
                            CredentialsMatchers.allOf(
                                CredentialsMatchers.withId(credentialsId),
                                GitClient.CREDENTIALS_MATCHER
                            )
                        );
                    client.addDefaultCredentials(credential);
                    CredentialsProvider.track(owner, credential);
                }

                if (!client.hasGitRepo(false)) {
                    listener.getLogger().println("Creating git repository in " + cacheDir);
                    client.init();
                }
                GitHooksConfiguration.configure(client, GitHooksConfiguration.get().isAllowedOnController());
                String remoteName = StringUtils.defaultIfBlank(config.getName(), Constants.DEFAULT_REMOTE_NAME);
                listener.getLogger().println("Setting " + remoteName + " to " + remote);
                client.setRemoteUrl(remoteName, remote);
                listener.getLogger().println("Fetching & pruning " + remoteName + "...");
                URIish remoteURI = null;
                try {
                    remoteURI = new URIish(remoteName);
                } catch (URISyntaxException ex) {
                    listener.getLogger().println("URI syntax exception for '" + remoteName + "' " + ex);
                }

                HeadNameResult headNameResult = HeadNameResult.calculate(branchSpec, rev, env);

                client.fetch_().prune(true).from(remoteURI, Collections.singletonList(new RefSpec(
                        "+" + headNameResult.prefix + headNameResult.headName + ":" + Constants.R_REMOTES + remoteName + "/"
                                + headNameResult.headName))).execute();

                listener.getLogger().println("Done.");
                return new GitSCMFileSystem(client, remote, Constants.R_REMOTES + remoteName + "/" + headNameResult.headName, (AbstractGitSCMSource.SCMRevisionImpl) rev);
            } catch (GitException x) {
                throw new IOException(x);
            } finally {
                cacheLock.unlock();
            }
        }

        @Override
        public SCMFileSystem build(@NonNull SCMSource source, @NonNull SCMHead head, @CheckForNull SCMRevision rev)
                throws IOException, InterruptedException {
            if (rev != null && !(rev instanceof AbstractGitSCMSource.SCMRevisionImpl)) {
                return null;
            }
            TaskListener listener = new LogTaskListener(LOGGER, Level.FINE);
            AbstractGitSCMSource gitSCMSource = (AbstractGitSCMSource) source;
            GitSCMBuilder<?> builder = gitSCMSource.newBuilder(head, rev);
            String cacheEntry = gitSCMSource.getCacheEntry();
            Lock cacheLock = AbstractGitSCMSource.getCacheLock(cacheEntry);
            cacheLock.lock();
            try {
                File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry);
                Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir);
                GitTool tool = gitSCMSource.resolveGitTool(builder.gitTool(), listener);
                if (tool != null) {
                    git.using(tool.getGitExe());
                }
                GitClient client = git.getClient();
                client.addDefaultCredentials(gitSCMSource.getCredentials());
                if (!client.hasGitRepo(false)) {
                    listener.getLogger().println("Creating git repository in " + cacheDir);
                    client.init();
                }
                GitHooksConfiguration.configure(client, GitHooksConfiguration.get().isAllowedOnController());
                String remoteName = builder.remoteName();
                listener.getLogger().println("Setting " + remoteName + " to " + gitSCMSource.getRemote());
                client.setRemoteUrl(remoteName, gitSCMSource.getRemote());
                listener.getLogger().println("Fetching & pruning " + remoteName + "...");
                URIish remoteURI = null;
                try {
                    remoteURI = new URIish(remoteName);
                } catch (URISyntaxException ex) {
                    listener.getLogger().println("URI syntax exception for '" + remoteName + "' " + ex);
                }
                client.fetch_().prune(true).from(remoteURI, builder.asRefSpecs()).execute();
                listener.getLogger().println("Done.");
                return new GitSCMFileSystem(client, gitSCMSource.getRemote(), Constants.R_REMOTES+remoteName+"/"+head.getName(),
                        (AbstractGitSCMSource.SCMRevisionImpl) rev);
            } catch (GitException x) {
                throw new IOException(x);
            } finally {
                cacheLock.unlock();
            }
        }
    }
}
