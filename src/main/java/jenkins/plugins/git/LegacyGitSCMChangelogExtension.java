package jenkins.plugins.git;

import java.io.IOException;

import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMChangelogExtension;
import hudson.plugins.git.extensions.impl.ChangelogToBranch;
import org.jenkinsci.plugins.gitclient.ChangelogCommand;
import org.jenkinsci.plugins.gitclient.GitClient;


/**
 * FIXME JavaDoc
 * Legacy changelog impl.
 */
public class LegacyGitSCMChangelogExtension extends GitSCMChangelogExtension {


    @Override
    public boolean decorateChangelogCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, ChangelogCommand cmd, Revision revToBuild) throws IOException, InterruptedException, GitException {
        boolean exclusion = false;

        // TODO Refactor ChangelogToBranch
        ChangelogToBranch changelogToBranch = scm.getExtensions().get(ChangelogToBranch.class);
        if (changelogToBranch != null) {
            listener.getLogger().println("Using 'Changelog to branch' strategy.");
            cmd.includes(revToBuild.getSha1());
            cmd.excludes(changelogToBranch.getOptions().getRef());
            exclusion = true;
        } else {
            exclusion = new ChangelogToPreviousBuild().decorateChangelogCommand(scm, build, git, listener, cmd, revToBuild);
        }

        if (!exclusion) {
            // this is the first time we are building this branch, so there's no base line to compare against.
            // if we force the changelog, it'll contain all the changes in the repo, which is not what we want.
            listener.getLogger().println("First time build. Skipping changelog.");
        }

        return exclusion;
    }
}
