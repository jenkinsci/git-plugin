package hudson.plugins.git.extensions.impl;

import hudson.AbortException;
import hudson.Extension;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.Branch;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.GitUtils;
import hudson.plugins.git.util.MergeRecord;
import org.eclipse.jgit.lib.ObjectId;
import org.jenkinsci.plugins.gitclient.CheckoutCommand;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static hudson.model.Result.FAILURE;
import hudson.model.Run;
import hudson.model.TaskListener;
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
    public Revision decorateRevisionToBuild(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, Revision marked, Revision rev) throws IOException, InterruptedException {
        String remoteBranchRef = GitSCM.getParameterString(options.getRef(), build.getEnvironment(listener));

        // if the branch we are merging is already at the commit being built, the entire merge becomes no-op
        // so there's nothing to do
        if (rev.containsBranchName(remoteBranchRef))
            return rev;

        // Only merge if there's a branch to merge that isn't us..
        listener.getLogger().println("Merging " + rev + " to " + remoteBranchRef + ", " + scm.getUserMergeOptions().toString());

        // checkout origin/blah
        ObjectId target = git.revParse(remoteBranchRef);

        String paramLocalBranch = scm.getParamLocalBranch(build, listener);
        CheckoutCommand checkoutCommand = git.checkout().branch(paramLocalBranch).ref(remoteBranchRef).deleteBranchIfExist(true);
        for (GitSCMExtension ext : scm.getExtensions())
            ext.decorateCheckoutCommand(scm, build, git, listener, checkoutCommand);
        checkoutCommand.execute();

        try {
            MergeCommand cmd = git.merge().setRevisionToMerge(rev.getSha1());
            for (GitSCMExtension ext : scm.getExtensions())
                ext.decorateMergeCommand(scm, build, git, listener, cmd);
            cmd.execute();
        } catch (GitException ex) {
            // merge conflict. First, avoid leaving any conflict markers in the working tree
            // by checking out some known clean state. We don't really mind what commit this is,
            // since the next build is going to pick its own commit to build, but 'rev' is as good any.
            checkoutCommand = git.checkout().branch(paramLocalBranch).ref(rev.getSha1String()).deleteBranchIfExist(true);
            for (GitSCMExtension ext : scm.getExtensions())
                ext.decorateCheckoutCommand(scm, build, git, listener, checkoutCommand);
            checkoutCommand.execute();
            // record the fact that we've tried building 'rev' and it failed, or else
            // BuildChooser in future builds will pick up this same 'rev' again and we'll see the exact same merge failure
            // all over again.
            BuildData bd = scm.getBuildData(build);
            if(bd != null){
                bd.saveBuild(new Build(marked,rev, build.getNumber(), FAILURE));
            } else {
                listener.getLogger().println("Was not possible to get build data");
            }
            throw new AbortException("Branch not suitable for integration as it does not merge cleanly: " + ex.getMessage());
        }

        build.addAction(new MergeRecord(remoteBranchRef,target.getName()));

        Revision mergeRevision = new GitUtils(listener,git).getRevisionForSHA1(git.revParse(HEAD));
        mergeRevision.getBranches().add(new Branch(remoteBranchRef, target));
        return mergeRevision;
    }

    @Override
    public void decorateMergeCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, MergeCommand cmd) throws IOException, InterruptedException, GitException {
        if (scm.getUserMergeOptions().getMergeStrategy() != null)
            cmd.setStrategy(scm.getUserMergeOptions().getMergeStrategy());
        cmd.setGitPluginFastForwardMode(scm.getUserMergeOptions().getFastForwardMode());
    }

    @Override
    public GitClientType getRequiredClient() {
        return GitClientType.GITCLI;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Merge before build";
        }
    }
}
