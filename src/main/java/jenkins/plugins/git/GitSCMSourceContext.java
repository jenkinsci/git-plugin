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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RefSpec;

/**
 * The {@link SCMSourceContext} for a {@link AbstractGitSCMSource}.
 *
 * @param <C> the type of {@link GitSCMSourceContext} so that the {@link #withTrait(SCMSourceTrait)} etc methods can
 *            be chained easily by subclasses.
 * @param <R> the type of {@link GitSCMSourceRequest} produced by {@link #newRequest(SCMSource, TaskListener)}.
 * @since 3.4.0
 */
public class GitSCMSourceContext<C extends GitSCMSourceContext<C, R>, R extends GitSCMSourceRequest>
        extends SCMSourceContext<C, R> {

    /**
     * The name of the {@link GitTool} to use or {@code null} to use the default.
     */
    @CheckForNull
    private String gitTool;
    /**
     * Should push notifications be ignored.
     */
    private boolean ignoreOnPushNotifications;
    /**
     * The ref specs to apply to the {@link GitSCM}.
     */
    @NonNull
    private List<String> refSpecs = new ArrayList<>();
    /**
     * The remote name.
     */
    @NonNull
    private String remoteName = AbstractGitSCMSource.DEFAULT_REMOTE_NAME;

    /**
     * Constructor.
     *
     * @param criteria (optional) criteria.
     * @param observer the {@link SCMHeadObserver}.
     */
    public GitSCMSourceContext(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer) {
        super(criteria, observer);
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
     * Returns {@code true} if push notifications should be ignored.
     *
     * @return {@code true} if push notifications should be ignored.
     */
    public final boolean ignoreOnPushNotifications() {
        return ignoreOnPushNotifications;
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
     * Returns the name to give the remote.
     *
     * @return the name to give the remote.
     */
    @NonNull
    public final String remoteName() {
        return remoteName;
    }

    /**
     * Configures the {@link GitTool#getName()} to use.
     *
     * @param gitTool the {@link GitTool#getName()} or {@code null} to use the system default.
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final C withGitTool(String gitTool) {
        this.gitTool = gitTool;
        return (C) this;
    }

    /**
     * Configures whether push notifications should be ignored.
     *
     * @param ignoreOnPushNotifications {@code true} to ignore push notifications.
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final C withIgnoreOnPushNotifications(boolean ignoreOnPushNotifications) {
        this.ignoreOnPushNotifications = ignoreOnPushNotifications;
        return (C) this;
    }

    /**
     * Adds the specified ref spec. If no ref specs were previously defined then the supplied ref spec will replace
     * {@link AbstractGitSCMSource#REF_SPEC_DEFAULT}. The ref spec is expected to be processed for substitution of
     * {@link AbstractGitSCMSource#REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR} by {@link AbstractGitSCMSource#getRemote()}
     * before use.
     *
     * @param refSpec the ref spec template to add.
     * @return {@code this} for method chaining.
     * @see #withoutRefSpecs()
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final C withRefSpec(@NonNull String refSpec) {
        this.refSpecs.add(refSpec);
        return (C) this;
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
    public final C withRefSpecs(List<String> refSpecs) {
        this.refSpecs.addAll(refSpecs);
        return (C) this;
    }

    /**
     * Clears the specified ref specs. If no ref specs are subsequently defined then
     * {@link AbstractGitSCMSource#REF_SPEC_DEFAULT} will be used as the ref spec template.
     *
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final C withoutRefSpecs() {
        this.refSpecs.clear();
        return (C) this;
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
    public final C withRemoteName(String remoteName) {
        this.remoteName = StringUtils.defaultIfBlank(remoteName, AbstractGitSCMSource.DEFAULT_REMOTE_NAME);
        return (C) this;
    }

    /**
     * Converts the ref spec templates into {@link RefSpec} instances.
     *
     * @return the list of {@link RefSpec} instances.
     */
    @NonNull
    public final List<RefSpec> asRefSpecs() {
        List<RefSpec> result = new ArrayList<>(Math.max(refSpecs.size(), 1));
        for (String template : refSpecs()) {
            result.add(new RefSpec(
                    template.replaceAll(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER, remoteName())
            ));
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public R newRequest(@NonNull SCMSource source, TaskListener listener) {
        return (R) new GitSCMSourceRequest(source, this, listener);
    }

}
