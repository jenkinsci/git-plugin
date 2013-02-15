package hudson.plugins.git.client;

import hudson.Launcher;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import org.codehaus.plexus.util.StringOutputStream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class GitAPITest extends HudsonTestCase {

    private hudson.EnvVars env = new hudson.EnvVars();
    private TaskListener listener = StreamTaskListener.fromStderr();
    private File repo;
    private IGitAPI git;

    @Override
    protected void setUp() throws Exception {
        repo = createTmpDir();
        git = new CliGitAPIImpl("git", repo, listener, env);
    }

    @Override
    protected void tearDown() throws Exception {
        Util.deleteRecursive(repo);
    }

    public void test_initialize_repository() throws Exception {
        git.init();
        assertStringContains(launchCommand("git status"),
                "On branch master");
    }

    public void test_detect_commit_in_repo() throws Exception {
        launchCommand("git init");
        launchCommand("touch file1");
        launchCommand("git add file1");
        launchCommand("git commit -m 'commit1'");
        String sha1 = launchCommand("git rev-parse HEAD").substring(0,40);
        assertTrue(git.isCommitInRepo(ObjectId.fromString(sha1)));
        // this MAY fail if commit has this exact sha1, but please admit this would be unlucky
        assertFalse(git.isCommitInRepo(ObjectId.fromString("1111111111111111111111111111111111111111")));
    }

    public void test_getRemoteURL() throws Exception {
        launchCommand("git init");
        launchCommand("git remote add origin git@github.com:jenkinsci/git-plugin.git");
        launchCommand("git ndeloof add origin git@github.com:ndeloof/git-plugin.git");
        assertEquals("git@github.com:jenkinsci/git-plugin.git", git.getRemoteUrl("origin"));
    }

    public void test_setRemoteURL() throws Exception {
        launchCommand("git init");
        launchCommand("git remote add origin git@github.com:jenkinsci/git-plugin.git");
        git.setRemoteUrl("ndeloof", "git@github.com:ndeloof/git-plugin.git");
        String remotes = launchCommand("git remote -v");
        assertStringContains(remotes, "origin\tgit@github.com:jenkinsci/git-plugin.git");
        assertStringContains(remotes, "ndeloof\tgit@github.com:ndeloof/git-plugin.git");
    }

    public void test_clean() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m 'init'");
        launchCommand("touch file1");
        git.clean();
        assertFalse(new File(repo, "file1").exists());
        assertStringContains(launchCommand("git status"),
                "nothing to commit, working directory clean");
    }

    public void test_fecth() throws Exception {
        launchCommand("git init");
        launchCommand("git remote add origin " + System.getProperty("user.dir")); // local git-plugin clone
        git.fetch("origin", null);
        assertStringContains(launchCommand("git rev-list --max-count=1 45a5d1a0c6857670ea2bec30d632604e02af4195"),
                "45a5d1a0c6857670ea2bec30d632604e02af4195");
    }

    public void test_branch() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m 'init'");
        git.branch("test");
        String branches = launchCommand("git branch -l");
        assertStringContains(branches,"master");
        assertStringContains(branches,"test");
    }

    public void test_remove_branch() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m 'init'");
        launchCommand("git branch test");
        git.deleteBranch("test");
        String branches = launchCommand("git branch -l");
        assertStringContains(branches,"master");
        assertFalse(branches.contains("test"));
    }


    public void test_hasGitRepo_without_git_directory() throws Exception
    {
        assertFalse("Empty directory has a Git repo", git.hasGitRepo());
    }

    public void test_hasGitRepo_with_invalid_git_repo() throws Exception
    {
        /* Create an empty directory named .git - "corrupt" git repo */
        new File(repo, ".git").mkdir();
        assertFalse("Invalid Git repo reported as valid", git.hasGitRepo());
    }

    public void test_hasGitRepo_with_valid_git_repo() throws Exception {
        launchCommand("git init");
        assertTrue("Valid Git repo reported as invalid", git.hasGitRepo());
    }

    private String launchCommand(String args) throws IOException, InterruptedException {
        return launchCommand(repo, args.split(" "));
    }

    private String launchCommand(File workdir, String ... args) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Launcher.LocalLauncher(listener).launch().pwd(workdir).cmds(args).
                envs(env).stdout(out).join();
        String s = out.toString();
        System.out.println(s);
        return s;
    }
}
