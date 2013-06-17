package hudson.plugins.git.extensions.impl;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.GitUtils;
import hudson.plugins.git.util.MergeRecord;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static hudson.model.Result.FAILURE;
import static org.eclipse.jgit.lib.Constants.HEAD;

/**
 * Speculatively merge the selected commit with another branch before the build to answer the "what happens
 * if I were to integrate this feature branch back to the master?" question.
 *
 * @author Nigel Magney
 * @author Nicolas Deloof
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class PreBuildMerge extends GitSCMExtension {
    private UserMergeOptions options;

    @DataBoundConstructor
    public PreBuildMerge(UserMergeOptions options) {
        if (options==null)  throw new IllegalStateException();
        this.options = options;
    }

    public UserMergeOptions getOptions() {
        return options;
    }

    @Override
    public Revision decorateRevisionToBuild(GitSCM scm, AbstractBuild<?, ?> build, GitClient git, BuildListener listener, Revision rev) throws IOException {
        String remoteBranchName = GitSCM.getParameterString(options.getMergeTarget(), build);

        // if the branch we are merging is already at the commit being built, the entire merge becomes no-op
        // so there's nothing to do
        if (rev.containsBranchName(remoteBranchName))
            return rev;

        // Only merge if there's a branch to merge that isn't us..
        listener.getLogger().println("Merging " + rev + " onto " + remoteBranchName);

        // checkout origin/blah
        ObjectId target = git.revParse(remoteBranchName);

        String paramLocalBranch = scm.getParamLocalBranch(build);
        git.checkoutBranch(paramLocalBranch, remoteBranchName);

        try {
            git.merge(rev.getSha1());
        } catch (GitException ex) {
            // merge conflict. First, avoid leaving any conflict markers in the working tree
            // by checking out some known clean state. We don't really mind what commit this is,
            // since the next build is going to pick its own commit to build, but 'rev' is as good any.
            git.checkoutBranch(paramLocalBranch, rev.getSha1String());

            // record the fact that we've tried building 'rev' and it failed, or else
            // BuildChooser in future builds will pick up this same 'rev' again and we'll see the exact same merge failure
            // all over again.
            scm.getBuildData(build).saveBuild(new Build(rev, build.getNumber(), FAILURE));
            throw new AbortException("Branch not suitable for integration as it does not merge cleanly");
        }

        build.addAction(new MergeRecord(remoteBranchName,target.getName()));

        return new GitUtils(listener,git).getRevisionForSHA1(git.revParse(HEAD));
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Merge before build";
        }
    }
}
