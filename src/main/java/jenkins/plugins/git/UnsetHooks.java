package jenkins.plugins.git;

import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.io.Serial;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

/**
 * Unsets git hooks. This can get remotely executed on agents.
 */
class UnsetHooks implements RepositoryCallback<Object> {
    private static final Logger LOGGER = Logger.getLogger(UnsetHooks.class.getName());

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Object invoke(Repository repo, VirtualChannel channel) throws IOException, InterruptedException {
        final StoredConfig repoConfig = repo.getConfig();
        final String val = repoConfig.getString("core", null, "hooksPath");
        if (val != null && !val.isEmpty() && !DisableHooks.DISABLED_NIX.equals(val) && !DisableHooks.DISABLED_WIN.equals(val)) {
            LOGGER.warning(() -> "core.hooksPath explicitly set to %s and will be left intact on %s.".formatted(val, repo.getDirectory()));
        } else {
            repoConfig.unset("core", null, "hooksPath");
            repoConfig.save();
        }
        return null;
    }
}
