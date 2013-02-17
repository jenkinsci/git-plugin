package hudson.plugins.git.client;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitException;
import hudson.util.StreamTaskListener;
import junit.framework.TestCase;
import org.eclipse.jgit.lib.ObjectId;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public abstract class GitAPITestCase extends TestCase {

    public final TemporaryDirectoryAllocator temporaryDirectoryAllocator = new TemporaryDirectoryAllocator();
    
    protected hudson.EnvVars env = new hudson.EnvVars();
    protected TaskListener listener = StreamTaskListener.fromStderr();
    protected File repo;
    private IGitAPI git;

    @Override
    protected void setUp() throws Exception {
        repo = temporaryDirectoryAllocator.allocate();
        git = setupGitAPI();
    }

    protected abstract IGitAPI setupGitAPI();

    @Override
    protected void tearDown() throws Exception {
        temporaryDirectoryAllocator.dispose();
    }

    public void test_initialize_repository() throws Exception {
        git.init();
        String status = launchCommand("git status");
        assertTrue("unexpected status " + status, status.contains("On branch master"));
    }

    public void test_detect_commit_in_repo() throws Exception {
        launchCommand("git init");
        (new File(repo, "file1")).createNewFile();
        launchCommand("git add file1");
        launchCommand("git commit -m 'commit1'");
        String sha1 = launchCommand("git rev-parse HEAD").substring(0,40);
        assertTrue("HEAD commit not found", git.isCommitInRepo(ObjectId.fromString(sha1)));
        // this MAY fail if commit has this exact sha1, but please admit this would be unlucky
        assertFalse(git.isCommitInRepo(ObjectId.fromString("1111111111111111111111111111111111111111")));
    }

    public void test_getRemoteURL() throws Exception {
        launchCommand("git init");
        launchCommand("git remote add origin git@github.com:jenkinsci/git-plugin.git");
        launchCommand("git ndeloof add origin git@github.com:ndeloof/git-plugin.git");
        String remoteUrl = git.getRemoteUrl("origin");
        assertEquals("unexepected remote URL " + remoteUrl, "git@github.com:jenkinsci/git-plugin.git", remoteUrl);
    }

    public void test_setRemoteURL() throws Exception {
        launchCommand("git init");
        launchCommand("git remote add origin git@github.com:jenkinsci/git-plugin.git");
        git.setRemoteUrl("origin", "git@github.com:ndeloof/git-plugin.git");
        String remotes = launchCommand("git remote -v");
        assertTrue("remote URL has not been updated", remotes.contains("origin\tgit@github.com:ndeloof/git-plugin.git"));
    }

    public void test_clean() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        (new File(repo, "file1")).createNewFile();
        git.clean();
        assertFalse(new File(repo, "file1").exists());
        String status = launchCommand("git status");
        assertTrue("unexpected status " + status, status.contains("working directory clean"));
    }

    public void test_fecth() throws Exception {
        launchCommand("git init");
        launchCommand("git remote add origin " + System.getProperty("user.dir")); // local git-plugin clone
        git.fetch("origin", null);
        assertTrue(launchCommand("git rev-list --max-count=1 45a5d1a0c6857670ea2bec30d632604e02af4195")
                .contains("45a5d1a0c6857670ea2bec30d632604e02af4195"));
    }

    public void test_create_branch() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        git.branch("test");
        String branches = launchCommand("git branch -l");
        assertTrue("master branch not listed", branches.contains("master"));
        assertTrue("test branch not listed", branches.contains("test"));
    }

    public void test_list_branches() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        launchCommand("git branch test");
        launchCommand("git branch another");
        Set<Branch> branches = git.getBranches();
        Collection names = Collections2.transform(branches, new Function<Branch, String>() {
            public String apply(Branch branch) {
                return branch.getName();
            }
        });
        assertEquals(3, branches.size());
        assertTrue("master branch not listed", names.contains("master"));
        assertTrue("test branch not listed", names.contains("test"));
        assertTrue("another branch not listed", names.contains("another"));
    }

    public void test_list_remote_branches() throws Exception {
        File remote = temporaryDirectoryAllocator.allocate();
        launchCommand(remote, "git init");
        launchCommand(remote, "git commit --allow-empty -m init");
        launchCommand(remote, "git branch test");
        launchCommand(remote, "git branch another");

        launchCommand("git init");
        launchCommand("git remote add origin " + remote.getAbsolutePath());
        launchCommand("git fetch origin");
        Set<Branch> branches = git.getRemoteBranches();
        Collection names = Collections2.transform(branches, new Function<Branch, String>() {
            public String apply(Branch branch) {
                return branch.getName();
            }
        });
        assertEquals(3, branches.size());
        assertTrue("origin/master branch not listed", names.contains("origin/master"));
        assertTrue("origin/test branch not listed", names.contains("origin/test"));
        assertTrue("origin/another branch not listed", names.contains("origin/another"));
    }

    public void test_list_branches_containing_ref() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        launchCommand("git branch test");
        launchCommand("git branch another");
        Set<Branch> branches = git.getBranches();
        Collection names = Collections2.transform(branches, new Function<Branch, String>() {
            public String apply(Branch branch) {
                return branch.getName();
            }
        });
        assertEquals(3, branches.size());
        assertTrue(names.contains("master"));
        assertTrue(names.contains("test"));
        assertTrue(names.contains("another"));
    }

    public void test_delete_branch() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        launchCommand("git branch test");
        git.deleteBranch("test");
        String branches = launchCommand("git branch -l");
        assertFalse("deleted test branch still present", branches.contains("test"));
    }

    public void test_create_tag() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        git.tag("test", "this is a tag");
        assertTrue("test tag not created", launchCommand("git tag").contains("test"));
        String message = launchCommand("git tag -l -n1");
        assertTrue("unexpected test tag message : " + message, message.contains("this is a tag"));
    }

    public void test_delete_tag() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        launchCommand("git tag test");
        launchCommand("git tag another");
        git.deleteTag("test");
        String tags = launchCommand("git tag");
        assertFalse("deleted test tag still present", tags.contains("test"));
        assertTrue("expected tag not listed", tags.contains("another"));
    }

    public void test_list_tags_with_filter() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        launchCommand("git tag test");
        launchCommand("git tag another_test");
        launchCommand("git tag yet_another");
        Set<String> tags = git.getTagNames("*test");
        assertTrue("expected tag not listed", tags.contains("test"));
        assertTrue("expected tag not listed", tags.contains("another_test"));
        assertFalse("unexpected tag listed", tags.contains("yet_another"));
    }

    public void test_tag_exists() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        launchCommand("git tag test");
        assertTrue(git.tagExists("test"));
        assertFalse(git.tagExists("unknown"));
    }

    public void test_get_HEAD_revision() throws Exception {
        // TODO replace with an embedded JGit server so that test run offline ?
        String sha1 = launchCommand("git ls-remote --heads git@github.com:jenkinsci/git-plugin.git refs/heads/master").substring(0,40);
        assertEquals(sha1, git.getHeadRev("git@github.com:jenkinsci/git-plugin.git", "master").name());
    }

    public void test_revparse_sha1_HEAD_or_tag() throws Exception {
        launchCommand("git init");
        launchCommand("git commit --allow-empty -m init");
        (new File(repo, "file1")).createNewFile();
        launchCommand("git add file1");
        launchCommand("git commit -m 'commit1'");
        launchCommand("git tag test");
        String sha1 = launchCommand("git rev-parse HEAD").substring(0,40);
        assertEquals(sha1, git.revParse(sha1).name());
        assertEquals(sha1, git.revParse("HEAD").name());
        assertEquals(sha1, git.revParse("test").name());
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
        return launchCommand(repo, args);
    }

    private String launchCommand(File workdir, String args) throws IOException, InterruptedException {
        return doLaunchCommand(workdir, args.split(" "));
    }

    private String doLaunchCommand(File workdir, String ... args) throws IOException, InterruptedException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Launcher.LocalLauncher(listener).launch().pwd(workdir).cmds(args).
                envs(env).stdout(out).join();
        String s = out.toString();
        System.out.println(s);
        return s;
    }
}
