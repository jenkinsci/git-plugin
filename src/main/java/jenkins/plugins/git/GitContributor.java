package jenkins.plugins.git;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;

@Extension
public class GitContributor extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(@Nonnull final Run run,
            @Nonnull final EnvVars envs,
            @Nonnull final TaskListener listener)
    throws IOException, InterruptedException {
        final BuildData buildData = run.getAction(BuildData.class);
        if (buildData != null) {
            String[] urls = buildData.getRemoteUrls().toArray(new String[0]);
            if (urls.length > 0) {
                GitSCM scm = new GitSCM(urls[0]);
                if (scm != null) {
                    scm.buildEnvironment(run, envs);
                }
            }
        }
    }
}
