package jenkins.plugins.git;

import hudson.Functions;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.io.Serial;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

/**
 * Disables git hooks. This can get remotely executed on agents.
 */
class DisableHooks implements RepositoryCallback<Object> {
    @Serial
    private static final long serialVersionUID = 1L;

    static final String DISABLED_WIN = "NUL:";
    static final String DISABLED_NIX = "/dev/null";

    @Override
    public Object invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
        final String VAL = Functions.isWindows() ? DISABLED_WIN : DISABLED_NIX;
        final StoredConfig repoConfig = repo.getConfig();
        repoConfig.setString("core", null, "hooksPath", VAL);
        repoConfig.save();
        return null;
    }
}
