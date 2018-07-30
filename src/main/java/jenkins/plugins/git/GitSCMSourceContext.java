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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
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
     * {@code true} if the {@link GitSCMSourceRequest} will need information about branches.
     */
    private boolean wantBranches;
    /**
     * {@code true} if the {@link GitSCMSourceRequest} will need information about tags.
     */
    private boolean wantTags;
    /**
     * A list of other references to discover and search
     */
    private Set<RefNameMapping> refNameMappings;
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
     * Returns {@code true} if the {@link GitSCMSourceRequest} will need information about branches.
     *
     * @return {@code true} if the {@link GitSCMSourceRequest} will need information about branches.
     */
    public final boolean wantBranches() {
        return wantBranches;
    }

    /**
     * Returns {@code true} if the {@link GitSCMSourceRequest} will need information about tags.
     *
     * @return {@code true} if the {@link GitSCMSourceRequest} will need information about tags.
     */
    public final boolean wantTags() {
        return wantTags;
    }

    /**
     * Returns {@code true} if the {@link GitSCMSourceRequest} will need information about other refs.
     *
     * @return {@code true} if the {@link GitSCMSourceRequest} will need information about other refs.
     */
    public final boolean wantOtherRefs() {
        return refNameMappings != null && !refNameMappings.isEmpty();
    }

    @NonNull
    public Collection<RefNameMapping> getRefNameMappings() {
        if (refNameMappings == null) {
            return Collections.emptySet();
        } else {
            return Collections.unmodifiableSet(refNameMappings);
        }
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
     * Adds a requirement for branch details to any {@link GitSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as is (makes
     *                simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public C wantBranches(boolean include) {
        wantBranches = wantBranches || include;
        return (C) this;
    }

    /**
     * Adds a requirement for tag details to any {@link GitSCMSourceRequest} for this context.
     *
     * @param include {@code true} to add the requirement or {@code false} to leave the requirement as is (makes
     *                simpler with method chaining)
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public C wantTags(boolean include) {
        wantTags = wantTags || include;
        return (C) this;
    }

    /**
     * Adds a requirement for details of additional refs to any {@link GitSCMSourceRequest} for this context.
     *
     * @param other The specification for that other ref
     * @return {@code this} for method chaining.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public C wantOtherRef(RefNameMapping other) {
        if (refNameMappings == null) {
            refNameMappings = new TreeSet<>();
        }
        refNameMappings.add(other);
        return (C) this;
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
     * {@link AbstractGitSCMSource#REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR} by {@link AbstractGitSCMSource#getRemote()}
     * before use.
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
        if (wantOtherRefs() && wantBranches()) {
            //If wantOtherRefs() there will be a refspec in the list not added manually by a user
            //So if also wantBranches() we need to add the default respec for branches so we actually fetch them
            result.add(new RefSpec("+" + Constants.R_HEADS + "*:" + Constants.R_REMOTES + remoteName() + "/*"));
        }
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

    public static final class RefNameMapping implements Comparable<RefNameMapping> {
        private final String ref;
        private final String name;
        private transient Pattern refPattern;

        public RefNameMapping(@NonNull String ref, @NonNull String name) {
            this.ref = ref;
            this.name = name;
        }

        @NonNull
        public String getRef() {
            return ref;
        }

        @NonNull
        public String getName() {
            return name;
        }

        Pattern refAsPattern() {
            if (refPattern == null) {
                refPattern = Pattern.compile(Constants.R_REFS + ref.replace("*", "(.+)"));
            }
            return refPattern;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RefNameMapping that = (RefNameMapping) o;

            if (!ref.equals(that.ref)) return false;
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            int result = ref.hashCode();
            result = 31 * result + name.hashCode();
            return result;
        }

        @Override
        public int compareTo(RefNameMapping o) {
            return Integer.compare(this.hashCode(), o != null ? o.hashCode() : 0);
        }

        public boolean matches(String revision, String remoteName, String remoteRev) {
            final Matcher matcher = refAsPattern().matcher(remoteName);
            if (matcher.matches()) {
                //TODO support multiple capture groups?
                if (matcher.groupCount() > 0) { //Group 0 apparently not in this count according to javadoc
                    String resolvedName = name.replace("@{1}", matcher.group(1));
                    return resolvedName.equals(revision);
                } else {
                    return name.equals(revision);
                }
            }
            return false;
        }

        public boolean matches(String remoteName) {
            final Matcher matcher = refAsPattern().matcher(remoteName);
            return matcher.matches();
        }

        public String getName(String remoteName) {
            final Matcher matcher = refAsPattern().matcher(remoteName);
            if (matcher.matches()) {
                if (matcher.groupCount() > 0) { //Group 0 apparently not in this count according to javadoc
                    return name.replace("@{1}", matcher.group(1));
                } else if (!name.contains("@{1}")) {
                    return name;
                }
            }
            return null;
        }
    }

}
