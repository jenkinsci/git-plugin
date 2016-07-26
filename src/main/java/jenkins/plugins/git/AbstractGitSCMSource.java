
/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc., Stephen Connolly, Amadeus IT Group.
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
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.plugins.git.util.BuildChooserDescriptor;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.DefaultBuildChooser;
import hudson.scm.SCM;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * @author Stephen Connolly
 */
public abstract class AbstractGitSCMSource extends SCMSource {

    /**
     * Keep one lock per cache directory. Lazy populated, but never purge, except on restart.
     */
    private static final ConcurrentMap<String, Lock> cacheLocks = new ConcurrentHashMap<String, Lock>();

    private static final Logger LOGGER = Logger.getLogger(AbstractGitSCMSource.class.getName());

    public AbstractGitSCMSource(String id) {
        super(id);
    }

    public abstract String getCredentialsId();

    public abstract String getRemote();

    public abstract String getIncludes();

    public abstract String getExcludes();

    /**
     * Gets {@link GitRepositoryBrowser} to be used with this SCMSource.
     * @return Repository browser or {@code null} if the default tool should be used.
     * @since 2.5.1
     */
    @CheckForNull
    public GitRepositoryBrowser getBrowser() {
        // Always return null by default
        return null;
    }

    /**
     * Gets Git tool to be used for this SCM Source.
     * @return Git Tool or {@code null} if the default tool should be used.
     * @since 2.5.1
     */
    @CheckForNull
    public String getGitTool() {
        // Always return null by default
        return null;
    }

    /**
     * Gets list of extensions, which should be used with this branch source.
     * @return List of Extensions to be used. May be empty
     * @since 2.5.1
     */
    @NonNull
    public List<GitSCMExtension> getExtensions() {
        // Always return empty list
        return Collections.emptyList();
    }

    public String getRemoteName() {
      return "origin";
    }

    @CheckForNull
    @Override
    protected SCMRevision retrieve(@NonNull SCMHead head, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        String cacheEntry = getCacheEntry();
        Lock cacheLock = getCacheLock(cacheEntry);
        cacheLock.lock();
        try {
            File cacheDir = getCacheDir(cacheEntry);
            Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir);
            GitClient client = git.getClient();
            client.addDefaultCredentials(getCredentials());
            if (!client.hasGitRepo()) {
                listener.getLogger().println("Creating git repository in " + cacheDir);
                client.init();
            }
            String remoteName = getRemoteName();
            listener.getLogger().println("Setting " + remoteName + " to " + getRemote());
            client.setRemoteUrl(remoteName, getRemote());
            listener.getLogger().println("Fetching " + remoteName + "...");
            List<RefSpec> refSpecs = getRefSpecs();
            client.fetch(remoteName, refSpecs.toArray(new RefSpec[refSpecs.size()]));
            // we don't prune remotes here, as we just want one head's revision
            for (Branch b : client.getRemoteBranches()) {
                String branchName = StringUtils.removeStart(b.getName(), remoteName + "/");
                if (branchName.equals(head.getName())) {
                    return new SCMRevisionImpl(head, b.getSHA1String());
                }
            }
            return null;
        } finally {
            cacheLock.unlock();
        }
    }

    private static void _close(@NonNull Object walk) {
        java.lang.reflect.Method closeMethod;
        try {
            closeMethod = walk.getClass().getDeclaredMethod("close");
        } catch (NoSuchMethodException ex) {
            LOGGER.log(Level.SEVERE, "Exception finding walker close method: {0}", ex);
            return;
        } catch (SecurityException ex) {
            LOGGER.log(Level.SEVERE, "Exception finding walker close method: {0}", ex);
            return;
        }
        try {
            closeMethod.invoke(walk);
        } catch (IllegalAccessException ex) {
            LOGGER.log(Level.SEVERE, "Exception calling walker close method: {0}", ex);
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.SEVERE, "Exception calling walker close method: {0}", ex);
        } catch (InvocationTargetException ex) {
            LOGGER.log(Level.SEVERE, "Exception calling walker close method: {0}", ex);
        }
    }

    /**
     * Call release method on walk.  JGit 3 uses release(), JGit 4 uses close() to
     * release resources.
     *
     * This method should be removed once the code depends on git client 2.0.0.
     * @param walk object whose close or release method will be called
     */
    private static void _release(TreeWalk walk) throws IOException {
        if (walk == null) {
            return;
        }
        try {
            walk.release(); // JGit 3
        } catch (NoSuchMethodError noMethod) {
            _close(walk);
        }
    }

    /**
     * Call release method on walk.  JGit 3 uses release(), JGit 4 uses close() to
     * release resources.
     *
     * This method should be removed once the code depends on git client 2.0.0.
     * @param walk object whose close or release method will be called
     */
    private void _release(RevWalk walk) {
        if (walk == null) {
            return;
        }
        try {
            walk.release(); // JGit 3
        } catch (NoSuchMethodError noMethod) {
            _close(walk);
        }
    }

    @NonNull
    @Override
    protected void retrieve(@NonNull final SCMHeadObserver observer,
                            @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        String cacheEntry = getCacheEntry();
        Lock cacheLock = getCacheLock(cacheEntry);
        cacheLock.lock();
        try {
            File cacheDir = getCacheDir(cacheEntry);
            Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir);
            GitClient client = git.getClient();
            client.addDefaultCredentials(getCredentials());
            if (!client.hasGitRepo()) {
                listener.getLogger().println("Creating git repository in " + cacheDir);
                client.init();
            }
            String remoteName = getRemoteName();
            listener.getLogger().println("Setting " + remoteName + " to " + getRemote());
            client.setRemoteUrl(remoteName, getRemote());
            listener.getLogger().println("Fetching " + remoteName + "...");
            List<RefSpec> refSpecs = getRefSpecs();
            client.fetch(remoteName, refSpecs.toArray(new RefSpec[refSpecs.size()]));
            listener.getLogger().println("Pruning stale remotes...");
            final Repository repository = client.getRepository();
            try {
                client.prune(new RemoteConfig(repository.getConfig(), remoteName));
            } catch (UnsupportedOperationException e) {
                e.printStackTrace(listener.error("Could not prune stale remotes"));
            } catch (URISyntaxException e) {
                e.printStackTrace(listener.error("Could not prune stale remotes"));
            }
            listener.getLogger().println("Getting remote branches...");
            SCMSourceCriteria branchCriteria = getCriteria();
            RevWalk walk = new RevWalk(repository);
            try {
                walk.setRetainBody(false);
                for (Branch b : client.getRemoteBranches()) {
                    if (!b.getName().startsWith(remoteName + "/")) {
                      continue;
                    }
                    final String branchName = StringUtils.removeStart(b.getName(), remoteName + "/");
                    listener.getLogger().println("Checking branch " + branchName);
                    if (isExcluded(branchName)){
                      continue;
                    }
                    if (branchCriteria != null) {
                        RevCommit commit = walk.parseCommit(b.getSHA1());
                        final long lastModified = TimeUnit.SECONDS.toMillis(commit.getCommitTime());
                        final RevTree tree = commit.getTree();
                        SCMSourceCriteria.Probe probe = new SCMSourceCriteria.Probe() {
                            @Override
                            public String name() {
                                return branchName;
                            }

                            @Override
                            public long lastModified() {
                                return lastModified;
                            }

                            @Override
                            public boolean exists(@NonNull String path) throws IOException {
                                TreeWalk tw = TreeWalk.forPath(repository, path, tree);
                                try {
                                    return tw != null;
                                } finally {
                                    _release(tw);
                                }
                            }
                        };
                        if (branchCriteria.isHead(probe, listener)) {
                            listener.getLogger().println("Met criteria");
                        } else {
                            listener.getLogger().println("Does not meet criteria");
                            continue;
                        }
                    }
                    SCMHead head = new SCMHead(branchName);
                    SCMRevision hash = new SCMRevisionImpl(head, b.getSHA1String());
                    observer.observe(head, hash);
                    if (!observer.isObserving()) {
                        return;
                    }
                }
            } finally {
                _release(walk);
            }

            listener.getLogger().println("Done.");
        } finally {
            cacheLock.unlock();
        }
    }

    protected String getCacheEntry() {
        return "git-" + Util.getDigestOf(getRemote());
    }

    protected static File getCacheDir(String cacheEntry) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            LOGGER.severe("Jenkins instance is null in AbstractGitSCMSource.getCacheDir");
            return null;
        }
        File cacheDir = new File(new File(jenkins.getRootDir(), "caches"), cacheEntry);
        File parentDir = cacheDir.getParentFile();
        if (!parentDir.isDirectory()) {
            boolean ok = parentDir.mkdirs();
            if (!ok) LOGGER.info("Failed mkdirs of " + parentDir.getPath());
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

    protected StandardUsernameCredentials getCredentials() {
        return CredentialsMatchers
                .firstOrNull(
                        CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, getOwner(),
                                ACL.SYSTEM, URIRequirementBuilder.fromUri(getRemote()).build()),
                        CredentialsMatchers.allOf(CredentialsMatchers.withId(getCredentialsId()),
                                GitClient.CREDENTIALS_MATCHER));
    }

    protected abstract List<RefSpec> getRefSpecs();

    @NonNull
    @Override
    public SCM build(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
        BuildChooser buildChooser = revision instanceof SCMRevisionImpl ? new SpecificRevisionBuildChooser(
                (SCMRevisionImpl) revision) : new DefaultBuildChooser();
        List<GitSCMExtension> extensions = getExtensions();
        return new GitSCM(
                getRemoteConfigs(),
                Collections.singletonList(new BranchSpec(head.getName())),
                false, Collections.<SubmoduleConfig>emptyList(),
                getBrowser(), getGitTool(),
                extensions.isEmpty() ? Collections.<GitSCMExtension>singletonList(new BuildChooserSetting(buildChooser)) : extensions);
    }

    protected List<UserRemoteConfig> getRemoteConfigs() {
        List<RefSpec> refSpecs = getRefSpecs();
        List<UserRemoteConfig> result = new ArrayList<UserRemoteConfig>(refSpecs.size());
        String remote = getRemote();
        for (RefSpec refSpec : refSpecs) {
            result.add(new UserRemoteConfig(remote, getRemoteName(), refSpec.toString(), getCredentialsId()));
        }
        return result;
    }
    
    /**
     * Returns true if the branchName isn't matched by includes or is matched by excludes.
     * 
     * @param branchName
     * @return true if branchName is excluded or is not included
     */
    protected boolean isExcluded (String branchName){
      return !Pattern.matches(getPattern(getIncludes()), branchName) || (Pattern.matches(getPattern(getExcludes()), branchName));
    }
    
    /**
     * Returns the pattern corresponding to the branches containing wildcards. 
     * 
     * @param branchName
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

    /**
     * Our implementation.
     */
    public static class SCMRevisionImpl extends SCMRevision {

        /**
         * The subversion revision.
         */
        private String hash;

        public SCMRevisionImpl(SCMHead head, String hash) {
            super(head);
            this.hash = hash;
        }

        public String getHash() {
            return hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SCMRevisionImpl that = (SCMRevisionImpl) o;

            return StringUtils.equals(hash, that.hash) && getHead().equals(that.getHead());

        }

        @Override
        public int hashCode() {
            return hash != null ? hash.hashCode() : 0;
        }

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

        @Override
        public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch, GitClient git,
                                                          TaskListener listener, BuildData buildData,
                                                          BuildChooserContext context)
                throws GitException, IOException, InterruptedException {
            return Collections.singleton(revision);
        }

        @Override
        public Build prevBuildForChangelog(String branch, @Nullable BuildData data, GitClient git,
                                           BuildChooserContext context) throws IOException, InterruptedException {
            // we have ditched that crazy multiple branch stuff from the regular GIT SCM.
            return data == null ? null : data.lastBuild;
        }

        @Extension
        public static class DescriptorImpl extends BuildChooserDescriptor {

            @Override
            public String getDisplayName() {
                return "Specific revision";
            }

            public boolean isApplicable(java.lang.Class<? extends Item> job) {
                return SCMSourceOwner.class.isAssignableFrom(job);
            }

        }

    }
}
