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
import hudson.scm.SCM;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.trait.SCMBuilder;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RefSpec;

/**
 * The {@link SCMBuilder} base class for {@link AbstractGitSCMSource}.
 *
 * @param <B> the concrete type of {@link GitSCMBuilder} so that subclasses can chain correctly in their
 * {@link #withHead(SCMHead)} etc methods.
 */
public class GitSCMBuilder<B extends GitSCMBuilder<B>> extends SCMBuilder<B, GitSCM> {

    /**
     * The {@link GitSCMExtension} instances to apply to the {@link GitSCM}.
     */
    @NonNull
    private final List<GitSCMExtension> extensions = new ArrayList<>();
    /**
     * The ref specs to apply to the {@link GitSCM}.
     */
    @NonNull
    private List<String> refSpecs = new ArrayList<>();
    /**
     * The {@link GitRepositoryBrowser} or {@code null} to use the "auto" browser.
     */
    @CheckForNull
    private GitRepositoryBrowser browser;
    /**
     * The name of the {@link GitTool} to use or {@code null} to use the default.
     */
    @CheckForNull
    private String gitTool;
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
     * The {@link IdCredentials#getId()} of the {@link Credentials} to use when connecting to the {@link #remote} or
     * {@code null} to let the git client choose between providing its own credentials or connecting anonymously.
     */
    @CheckForNull
    private String credentialsId;

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
    @SuppressWarnings("unchecked")
    @NonNull
    public final B withExtensions(GitSCMExtension... extensions) {
        for (GitSCMExtension extension : extensions) {
            withExtension(extension);
        }
        return (B) this;
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
    public final B withRemote(String remote) {
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
     * Converts the ref spec templates into {@link RefSpec} instances.
     *
     * @return the list of {@link RefSpec} instances.
     */
    @NonNull
    public final List<RefSpec> asRefSpecs() {
        List<RefSpec> result = new ArrayList<>(Math.max(refSpecs.size(), 1));
        for (String template: refSpecs()){
            result.add(new RefSpec(
                    template.replaceAll(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER, remoteName())
            ));
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
        List<RefSpec> refSpecs = asRefSpecs();
        List<UserRemoteConfig> result = new ArrayList<>(refSpecs.size());
        String remote = remote();
        for (RefSpec refSpec : refSpecs) {
            result.add(new UserRemoteConfig(remote, remoteName(), refSpec.toString(), credentialsId()));
        }
        return result;

    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public GitSCM build() {
        List<GitSCMExtension> extensions = new ArrayList<>(extensions());
        if (revision() instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            // remove any conflicting BuildChooserSetting if present
            for (Iterator<GitSCMExtension> iterator = extensions.iterator(); iterator.hasNext(); ) {
                if (iterator.next() instanceof BuildChooserSetting) {
                    iterator.remove();
                }
            }
            extensions.add(new BuildChooserSetting(new AbstractGitSCMSource.SpecificRevisionBuildChooser(
                    (AbstractGitSCMSource.SCMRevisionImpl) revision())));
        }
        return new GitSCM(
                asRemoteConfigs(),
                Collections.singletonList(new BranchSpec(head().getName())),
                false, Collections.<SubmoduleConfig>emptyList(),
                browser(), gitTool(),
                extensions);
    }
}
