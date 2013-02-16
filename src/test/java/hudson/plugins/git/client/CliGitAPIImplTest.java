package hudson.plugins.git.client;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class CliGitAPIImplTest extends GitAPITestCase {
    @Override
    protected IGitAPI setupGitAPI() {
        return new CliGitAPIImpl("git", repo, listener, env);
    }
}
