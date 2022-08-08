package jenkins.plugins.git;

import java.io.IOException;
import java.io.Serializable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.extensions.GitSCMChangelogExtension;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildChooserContext;
import hudson.remoting.Channel;
import org.jenkinsci.plugins.gitclient.ChangelogCommand;
import org.jenkinsci.plugins.gitclient.GitClient;

/**
 * FIXME JavaDoc
 * @author Zhenlei Huang
 */
public class ChangelogToPreviousBuild extends GitSCMChangelogExtension {

    @Override
    public boolean decorateChangelogCommand(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener, ChangelogCommand cmd, Revision revToBuild) throws IOException, InterruptedException, GitException {
        boolean decorated = false;

        for (Branch b : revToBuild.getBranches()) {
            Build lastRevWas = scm.getBuildChooser().prevBuildForChangelog(b.getName(), scm.getBuildData(build.getPreviousBuild()), git, new BuildChooserContextImpl(build.getParent(), build, build.getEnvironment(listener)));
            if (lastRevWas != null && lastRevWas.revision != null && git.isCommitInRepo(lastRevWas.getSHA1())) {
                cmd.includes(revToBuild.getSha1());
                cmd.excludes(lastRevWas.getSHA1());
                decorated = true;
            }
        }

        return decorated;
    }

    // Copy of GitSCM.BuildChooserContextImpl
    /*package*/ static class BuildChooserContextImpl implements BuildChooserContext, Serializable {
        @SuppressFBWarnings(value="SE_BAD_FIELD", justification="known non-serializable field")
        final Job project;
        @SuppressFBWarnings(value="SE_BAD_FIELD", justification="known non-serializable field")
        final Run build;
        final EnvVars environment;

        BuildChooserContextImpl(Job project, Run build, EnvVars environment) {
            this.project = project;
            this.build = build;
            this.environment = environment;
        }

        public <T> T actOnBuild(ContextCallable<Run<?,?>, T> callable) throws IOException, InterruptedException {
            return callable.invoke(build, FilePath.localChannel);
        }

        public <T> T actOnProject(ContextCallable<Job<?,?>, T> callable) throws IOException, InterruptedException {
            return callable.invoke(project, FilePath.localChannel);
        }

        public Run<?, ?> getBuild() {
            return build;
        }

        public EnvVars getEnvironment() {
            return environment;
        }

        private Object writeReplace() {
            return Channel.current().export(BuildChooserContext.class,new BuildChooserContext() {
                public <T> T actOnBuild(ContextCallable<Run<?,?>, T> callable) throws IOException, InterruptedException {
                    return callable.invoke(build,Channel.current());
                }

                public <T> T actOnProject(ContextCallable<Job<?,?>, T> callable) throws IOException, InterruptedException {
                    return callable.invoke(project,Channel.current());
                }

                public Run<?, ?> getBuild() {
                    return build;
                }

                public EnvVars getEnvironment() {
                    return environment;
                }
            });
        }
    }
}
