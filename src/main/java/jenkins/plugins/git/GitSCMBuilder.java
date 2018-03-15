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

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.scm.SCM;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.mixin.TagSCMHead;
import jenkins.scm.api.trait.SCMBuilder;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RefSpec;

/**
 * The {@link SCMBuilder} base class for {@link AbstractGitSCMSource}.
 *
 * @param <B> the concrete type of {@link GitSCMBuilder} so that subclasses can chain correctly in their
 *            {@link #withHead(SCMHead)} etc methods.
 * @since 3.4.0
 */
public class GitSCMBuilder<B extends GitSCMBuilder<B>> extends SCMBuilder<B, GitSCM> {

    /**
     * The {@link GitRepositoryBrowser} or {@code null} to use the "auto" browser.
     */
    @CheckForNull
    private GitRepositoryBrowser browser;
    /**
     * The {@link GitSCMExtension} instances to apply to the {@link GitSCM}.
     */
    @NonNull
    private final List<GitSCMExtension> extensions = new ArrayList<>();
    /**
     * The {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to the {@link #remote} or
     * {@code null} to let the git client choose between providing its own credentials or connecting anonymously.
     */
    @CheckForNull
    private String credentialsId;
    /**
     * The name of the {@link GitTool} to use or {@code null} to use the default.
     */
    @CheckForNull
    private String gitTool;
    /**
     * The ref specs to apply to the {@link GitSCM}.
     */
    @NonNull
    private List<String> refSpecs = new ArrayList<>();
    /**
     * The name of the remote, defaults to {@link AbstractGitSCMSource#DEFAULT_REMOTE_NAME}.
     */
    @NonNull
    private String remoteName = AbstractGitSCMSource.DEFAULT_REMOTE_NAME;
    /**
     * The remote URL of the git repository.
     */
    @NonNull
    private String remote;
    /**
     * Any additional remotes keyed by their remote name.
     */
    @NonNull
    private final Map<String, AdditionalRemote> additionalRemotes = new TreeMap<>();

    /**
     * Constructor.
     *
     * @param head          The {@link SCMHead} to produce the {@link SCM} for.
     * @param revision      The {@link SCMRevision} to produce the {@link SCM} for or {@code null} to produce the
     *                      {@link SCM} for the head revision.
     * @param remote        The remote URL of the git server.
     * @param credentialsId The {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to
     *                      the {@link #remote} or {@code null} to let the git client choose between providing its own
     *                      credentials or connecting anonymously.
     */
    public GitSCMBuilder(@NonNull SCMHead head, @CheckForNull SCMRevision revision, @NonNull String remote,
                         @CheckForNull String credentialsId) {
        super(GitSCM.class, head, revision);
        this.remote = remote;
        this.credentialsId = credentialsId;
    }

    /**
     * Returns the {@link GitRepositoryBrowser} or {@code null} to use the "auto" browser.
     *
     * @return The {@link GitRepositoryBrowser} or {@code null} to use the "auto" browser.
     */
    @CheckForNull
    public final GitRepositoryBrowser browser() {
        return browser;
    }

    /**
     * Returns the {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to
     * the {@link #remote} or {@code null} to let the git client choose between providing its own
     * credentials or connecting anonymously.
     *
     * @return the {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to
     * the {@link #remote} or {@code null} to let the git client choose between providing its own
     * credentials or connecting anonymously.
     */
    @CheckForNull
    public final String credentialsId() {
        return credentialsId;
    }

    /**
     * Returns the {@link GitSCMExtension} instances to apply to the {@link GitSCM}.
     *
     * @return the {@link GitSCMExtension} instances to apply to the {@link GitSCM}.
     */
    @NonNull
    public final List<GitSCMExtension> extensions() {
        return Collections.unmodifiableList(extensions);
    }

    /**
     * Returns the name of the {@link GitTool} to use or {@code null} to use the default.
     *
     * @return the name of the {@link GitTool} to use or {@code null} to use the default.
     */
    @CheckForNull
    public final String gitTool() {
        return gitTool;
    }

    /**
     * Returns the list of ref specs to use.
     *
     * @return the list of ref specs to use.
     */
    @NonNull
    public final List<String> refSpecs() {
        if (refSpecs.isEmpty()) {
            return Collections.singletonList(AbstractGitSCMSource.REF_SPEC_DEFAULT);
        }
        return Collections.unmodifiableList(refSpecs);
    }

    /**
     * Returns the remote URL of the git repository.
     *
     * @return the remote URL of the git repository.
     */
    @NonNull
    public final String remote() {
        return remote;
    }

    /**
     * Returns the name to give the remote.
     *
     * @return the name to give the remote.
     */
    @NonNull
    public final String remoteName() {
        return remoteName;
    }

    /**
     * Gets the (possibly empty) additional remote names.
     *
     * @return the (possibly empty) additional remote names.
     */
    @NonNull
    public final Set<String> additionalRemoteNames() {
        return Collections.unmodifiableSet(additionalRemotes.keySet());
    }

    /**
     * Gets the remote URL of the git repository for the specified remote name.
     *
     * @param remoteName the additional remote name.
     * @return the remote URL of the named additional remote or {@code null} if the supplied name is not in
     * {@link #additionalRemoteNames()}
     */
    @CheckForNull
    public final String additionalRemote(String remoteName) {
        AdditionalRemote additionalRemote = additionalRemotes.get(remoteName);
        return additionalRemote == null ? null : additionalRemote.remote();
    }

    /**
     * Gets the ref specs to use for the git repository of the specified remote name.
     *
     * @param remoteName the additional remote name.
     * @return the ref specs for the named additional remote or {@code null} if the supplied name is not in
     * {@link #additionalRemoteNames()}
     */
    @CheckForNull
    public final List<String> additionalRemoteRefSpecs(String remoteName) {
        AdditionalRemote additionalRemote = additionalRemotes.get(remoteName);
        return additionalRemote == null ? null : additionalRemote.refSpecs();
    }

    /**
     * Configures the {@link GitRepositoryBrowser} to use.
     *
     * @param browser the {@link GitRepositoryBrowser} or {@code null} to use the default "auto" browser.
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withBrowser(@CheckForNull GitRepositoryBrowser browser) {
        this.browser = browser;
        return (B) this;
    }

    /**
     * Configures the {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to the
     * {@link #remote()}
     *
     * @param credentialsId the {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to
     *                      the {@link #remote()} or {@code null} to let the git client choose between providing its own
     *                      credentials or connecting anonymously.
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withCredentials(@CheckForNull String credentialsId) {
        this.credentialsId = credentialsId;
        return (B) this;
    }

    /**
     * Adds (or redefines) the supplied {@link GitSCMExtension}.
     *
     * @param extension the {@link GitSCMExtension} ({@code null} values are safely ignored).
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withExtension(@CheckForNull GitSCMExtension extension) {
        if (extension != null) {
            // the extensions only allow one of each type.
            for (Iterator<GitSCMExtension> iterator = extensions.iterator(); iterator.hasNext(); ) {
                if (extension.getClass().equals(iterator.next().getClass())) {
                    iterator.remove();
                }
            }
            extensions.add(extension);
        }
        return (B) this;
    }

    /**
     * Adds (or redefines) the supplied {@link GitSCMExtension}s.
     *
     * @param extensions the {@link GitSCMExtension}s.
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final B withExtensions(GitSCMExtension... extensions) {
        return withExtensions(Arrays.asList(extensions));
    }

    /**
     * Adds (or redefines) the supplied {@link GitSCMExtension}s.
     *
     * @param extensions the {@link GitSCMExtension}s.
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withExtensions(@NonNull List<GitSCMExtension> extensions) {
        for (GitSCMExtension extension : extensions) {
            withExtension(extension);
        }
        return (B) this;
    }

    /**
     * Configures the {@link GitTool#getName()} to use.
     *
     * @param gitTool the {@link GitTool#getName()} or {@code null} to use the system default.
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withGitTool(@CheckForNull String gitTool) {
        this.gitTool = gitTool;
        return (B) this;
    }

    /**
     * Adds the specified ref spec. If no ref specs were previously defined then the supplied ref spec will replace
     * {@link AbstractGitSCMSource#REF_SPEC_DEFAULT}. The ref spec is expected to be processed for substitution of
     * {@link AbstractGitSCMSource#REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR} by {@link #remote()} before use.
     *
     * @param refSpec the ref spec template to add.
     * @return {@code this} for method chaining.
     * @see #withoutRefSpecs()
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withRefSpec(@NonNull String refSpec) {
        this.refSpecs.add(refSpec);
        return (B) this;
    }

    /**
     * Adds the specified ref specs. If no ref specs were previously defined then the supplied ref specs will replace
     * {@link AbstractGitSCMSource#REF_SPEC_DEFAULT}. The ref spec is expected to be processed for substitution of
     * {@link AbstractGitSCMSource#REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR} by {@link #remote()} before use.
     *
     * @param refSpecs the ref spec templates to add.
     * @return {@code this} for method chaining.
     * @see #withoutRefSpecs()
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withRefSpecs(@NonNull List<String> refSpecs) {
        this.refSpecs.addAll(refSpecs);
        return (B) this;
    }

    /**
     * Clears the specified ref specs. If no ref specs are subsequently defined then
     * {@link AbstractGitSCMSource#REF_SPEC_DEFAULT} will be used as the ref spec template.
     *
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withoutRefSpecs() {
        this.refSpecs.clear();
        return (B) this;
    }

    /**
     * Replaces the URL of the git repository.
     *
     * @param remote the new URL to use for the git repository.
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withRemote(@NonNull String remote) {
        this.remote = remote;
        return (B) this;
    }

    /**
     * Configures the remote name to use for the git repository.
     *
     * @param remoteName the remote name to use for the git repository ({@code null} or the empty string are
     *                   equivalent to passing {@link AbstractGitSCMSource#DEFAULT_REMOTE_NAME}).
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withRemoteName(@CheckForNull String remoteName) {
        this.remoteName = StringUtils.defaultIfBlank(remoteName, AbstractGitSCMSource.DEFAULT_REMOTE_NAME);
        return (B) this;
    }

    /**
     * Configures an additional remote. It is the responsibility of the caller to ensure that there are no conflicts
     * with the eventual {@link #remote()} name.
     *
     * @param remoteName the name of the additional remote.
     * @param remote     the url of the additional remote.
     * @param refSpecs   the ref specs of the additional remote, if empty will default to
     *                   {@link AbstractGitSCMSource#REF_SPEC_DEFAULT}
     * @return {@code this} for method chaining.
     */
    @NonNull
    public final B withAdditionalRemote(@NonNull String remoteName, @NonNull String remote, String... refSpecs) {
        return withAdditionalRemote(remoteName, remote, Arrays.asList(refSpecs));
    }

    /**
     * Configures an additional remote. It is the responsibility of the caller to ensure that there are no conflicts
     * with the eventual {@link #remote()} name.
     *
     * @param remoteName the name of the additional remote.
     * @param remote     the url of the additional remote.
     * @param refSpecs   the ref specs of the additional remote, if empty will default to
     *                   {@link AbstractGitSCMSource#REF_SPEC_DEFAULT}
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withAdditionalRemote(@NonNull String remoteName, @NonNull String remote, List<String> refSpecs) {
        this.additionalRemotes.put(remoteName, new AdditionalRemote(remoteName, remote, refSpecs));
        return (B) this;
    }

    /**
     * Converts the ref spec templates into {@link RefSpec} instances.
     *
     * @return the list of {@link RefSpec} instances.
     */
    @NonNull
    public final List<RefSpec> asRefSpecs() {
        // de-duplicate effective ref-specs after substitution of placeholder
        Set<String> refSpecs = new LinkedHashSet<>(Math.max(this.refSpecs.size(), 1));
        for (String template : refSpecs()) {
            refSpecs.add(template.replaceAll(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER, remoteName()));
        }
        List<RefSpec> result = new ArrayList<>(refSpecs.size());
        for (String refSpec : refSpecs) {
            result.add(new RefSpec(refSpec));
        }
        return result;
    }

    /**
     * Converts the {@link #asRefSpecs()} into {@link UserRemoteConfig} instances.
     *
     * @return the list of {@link UserRemoteConfig} instances.
     */
    @NonNull
    public final List<UserRemoteConfig> asRemoteConfigs() {
        List<UserRemoteConfig> result = new ArrayList<>(1 + additionalRemotes.size());
        result.add(new UserRemoteConfig(remote(), remoteName(), joinRefSpecs(asRefSpecs()), credentialsId()));
        for (AdditionalRemote r : additionalRemotes.values()) {
            result.add(new UserRemoteConfig(r.remote(), r.remoteName(), joinRefSpecs(r.asRefSpecs()), credentialsId()));
        }
        return result;
    }

    private String joinRefSpecs(List<RefSpec> refSpecs) {
        if (refSpecs.isEmpty()) {
            return "";
        }
        if (refSpecs.size() == 1) {
            return refSpecs.get(0).toString();
        }
        StringBuilder result = new StringBuilder(refSpecs.size() * 50 /*most ref specs are ~50 chars*/);
        boolean first = true;
        for (RefSpec r : refSpecs) {
            if (first) {
                first = false;
            } else {
                result.append(' ');
            }
            result.append(r.toString());
        }
        return result.toString();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GitSCM build() {
        List<GitSCMExtension> extensions = new ArrayList<>(extensions());
        boolean foundClone = false;
        for (GitSCMExtension e: extensions) {
            if (e instanceof CloneOption) {
                foundClone = true;
                break;
            }
        }
        if (!foundClone) {
            // assume honour refspecs unless the clone option is added
            // TODO revisit once we have support for TagSCMHead implemented as may need to check refspec handling then
            extensions.add(new GitSCMSourceDefaults(head() instanceof TagSCMHead));
        }
        SCMRevision revision = revision();
        if (revision instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            // remove any conflicting BuildChooserSetting if present
            for (Iterator<GitSCMExtension> iterator = extensions.iterator(); iterator.hasNext(); ) {
                if (iterator.next() instanceof BuildChooserSetting) {
                    iterator.remove();
                }
            }
            extensions.add(new BuildChooserSetting(new AbstractGitSCMSource.SpecificRevisionBuildChooser(
                    (AbstractGitSCMSource.SCMRevisionImpl) revision)));
        }
        if (head() instanceof GitRefSCMHead) {
            withoutRefSpecs().withRefSpec(((GitRefSCMHead) head()).getRef());
        }
        return new GitSCM(
                asRemoteConfigs(),
                Collections.singletonList(new BranchSpec(head().getName())),
                false, Collections.<SubmoduleConfig>emptyList(),
                browser(), gitTool(),
                extensions);
    }

    /**
     * Internal value class to manage additional remote configuration.
     */
    private static final class AdditionalRemote {
        /**
         * The name of the remote.
         */
        @NonNull
        private final String name;
        /**
         * The url of the remote.
         */
        @NonNull
        private final String url;
        /**
         * The ref spec templates of the remote.
         */
        @NonNull
        private final List<String> refSpecs;

        /**
         * Constructor.
         *
         * @param name     the name of the remote.
         * @param url      the url of the remote.
         * @param refSpecs the ref specs of the remote.
         */
        public AdditionalRemote(@NonNull String name, @NonNull String url, @NonNull List<String> refSpecs) {
            this.name = name;
            this.url = url;
            this.refSpecs = new ArrayList<>(
                    refSpecs.isEmpty()
                            ? Collections.singletonList(AbstractGitSCMSource.REF_SPEC_DEFAULT)
                            : refSpecs
            );
        }

        /**
         * Gets the name of the remote.
         *
         * @return the name of the remote.
         */
        public String remoteName() {
            return name;
        }

        /**
         * Gets the url of the remote.
         *
         * @return the url of the remote.
         */
        public String remote() {
            return url;
        }

        /**
         * Gets the ref specs of the remote.
         *
         * @return the ref specs of the remote.
         */
        public List<String> refSpecs() {
            return Collections.unmodifiableList(refSpecs);
        }

        /**
         * Converts the ref spec templates into {@link RefSpec} instances.
         *
         * @return the list of {@link RefSpec} instances.
         */
        @NonNull
        public final List<RefSpec> asRefSpecs() {
            // de-duplicate effective ref-specs after substitution of placeholder
            Set<String> refSpecs = new LinkedHashSet<>(Math.max(this.refSpecs.size(), 1));
            for (String template : refSpecs()) {
                refSpecs.add(template.replaceAll(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER, remoteName()));
            }
            List<RefSpec> result = new ArrayList<>(refSpecs.size());
            for (String refSpec : refSpecs) {
                result.add(new RefSpec(refSpec));
            }
            return result;
        }
    }

}
