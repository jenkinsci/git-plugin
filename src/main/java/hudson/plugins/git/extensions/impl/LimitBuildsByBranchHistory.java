package hudson.plugins.git.extensions.impl;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.FakeGitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.DefaultBuildChooser;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Keeps the builds-by-branch history in {@link BuildData} limited to the branches
 * that match the branch specifiers of the SCM.
 *
 * <p>Without this extension every build copies the history of every branch the job has
 * ever built, so build records grow without bound (JENKINS-19022). With it, a job that builds
 * one branch per build (for example a parameterized branch specifier) keeps one entry.
 *
 * <p>Only the default and ancestry build choosers are affected. Other build choosers, such as
 * the inverse build chooser, may rely on branches outside the branch specifiers, so the history
 * is left unchanged for them.
 *
 * @since TODO
 */
public class LimitBuildsByBranchHistory extends FakeGitSCMExtension {

    @DataBoundConstructor
    public LimitBuildsByBranchHistory() {
    }

    /**
     * Removes the entries of {@code buildData} whose branch matches none of the branch specifiers of {@code scm}.
     *
     * @param scm the SCM being checked out
     * @param buildData build data copied from the previous build
     * @param env environment used to expand the branch specifiers
     * @param listener build listener
     * @return number of removed entries
     */
    public int prune(GitSCM scm, BuildData buildData, EnvVars env, TaskListener listener) {
        BuildChooser chooser = scm.getBuildChooser();
        if (!(chooser instanceof DefaultBuildChooser)) {
            listener.getLogger().println("Keeping builds-by-branch history for build chooser " + chooser.getClass().getName());
            return 0;
        }
        List<String> remotes = new ArrayList<>();
        for (RemoteConfig remote : scm.getRepositories()) {
            remotes.add(remote.getName());
        }
        int before = buildData.buildsByBranchName.size();
        buildData.buildsByBranchName.keySet().removeIf(name -> !matchesAny(name, scm.getBranches(), remotes, env));
        int removed = before - buildData.buildsByBranchName.size();
        if (removed > 0) {
            listener.getLogger().println("Removed " + removed + " builds-by-branch entries that match no branch specifier");
        }
        return removed;
    }

    /**
     * History keys are remote tracking names, either {@code remote/branch} or {@code refs/remotes/remote/branch},
     * while branch specifiers may name the branch in any form the build chooser accepts, such as
     * {@code refs/heads/branch} or {@code branch}. Try the key in each of those forms.
     */
    static boolean matchesAny(String name, List<BranchSpec> branches, List<String> remotes, EnvVars env) {
        List<String> forms = new ArrayList<>();
        forms.add(name);
        String shortName = name.startsWith("refs/remotes/") ? name.substring("refs/remotes/".length()) : name;
        forms.add(shortName);
        forms.add("remotes/" + shortName);
        forms.add("refs/remotes/" + shortName);
        for (String remote : remotes) {
            if (shortName.startsWith(remote + "/")) {
                String branch = shortName.substring(remote.length() + 1);
                forms.add(branch);
                forms.add("refs/heads/" + branch);
            }
        }
        for (BranchSpec spec : branches) {
            for (String form : forms) {
                if (spec.matches(form, env)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return LimitBuildsByBranchHistory.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "LimitBuildsByBranchHistory{}";
    }

    @Extension
    @Symbol("limitBuildsByBranchHistory")
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Keep build history only for branches matching the branch specifier";
        }
    }
}
