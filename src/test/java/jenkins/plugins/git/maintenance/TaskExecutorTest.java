package jenkins.plugins.git.maintenance;

import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.git.*;
import hudson.util.StreamTaskListener;
import jenkins.model.GlobalConfiguration;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMFileSystem;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class TaskExecutorTest extends AbstractGitRepository {

    @ClassRule
    public static JenkinsRule rule = new JenkinsRule();

    private Task gitTask;


    public TaskExecutorTest(TaskType taskType){
            this.gitTask = new Task(taskType);
            this.gitTask.setCronSyntax("* * * * *");
            this.gitTask.setIsTaskConfigured(true);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteMaintenanceTasks(){

        List<Object[]> maintenanceTasks = new ArrayList<>();

        maintenanceTasks.add(new Object[]{TaskType.PREFETCH});
        maintenanceTasks.add(new Object[]{TaskType.GC});
        maintenanceTasks.add(new Object[]{TaskType.LOOSE_OBJECTS});
        maintenanceTasks.add(new Object[]{TaskType.INCREMENTAL_REPACK});
        maintenanceTasks.add(new Object[]{TaskType.COMMIT_GRAPH});
        return maintenanceTasks;
    }

    @Test
    public void testGitClient(){
       // Get directory of a single cache.
        TaskExecutor taskExecutor = new TestTaskExecutorHelper(gitTask,testGitDir);
        assertTrue(taskExecutor.getCaches().size() > 0);
        GitMaintenanceSCM.Cache cache = taskExecutor.getCaches().get(0);
        File cacheFile = cache.getCacheFile();
        assertNotNull(taskExecutor.getGitClient(cacheFile));
        assertThat(taskExecutor.getGitClient(cacheFile),instanceOf(GitClient.class));
    }

    @Test
    public void testNullFileInGetGitClient() {
        GitClient client = new TestTaskExecutorHelper(gitTask,null).getGitClient(null);
        assertNull(client);
    }

    @Test
    public void testGetCaches(){
        TaskExecutor taskExecutor = new TestTaskExecutorHelper(gitTask,testGitDir);
        assertNotNull(taskExecutor.getCaches());
    }

    @Test
    public void testExecuteGitMaintenance() throws Exception {

        // This will create a pack file, incremental repack will then start to work.
        repo.git("repack");

        TaskExecutor taskExecutor = new TestTaskExecutorHelper(gitTask,testGitDir);
        GitMaintenanceSCM.Cache cache = taskExecutor.getCaches().get(0);
        File cacheFile = cache.getCacheFile();
        GitClient client = taskExecutor.getGitClient(cacheFile);
        boolean isExecuted = taskExecutor.executeGitMaintenance(client,gitTask.getTaskType());

        // based on the underlying git version it will work.
        // If git version < 2.30, tests may fail.
        assertThat(isExecuted,is(true));
    }

    @Test
    public void testRunnable() throws Exception {

        // This will create a pack file, incremental repack will then start to work.
        repo.git("repack");

        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
        config.setIsGitMaintenanceRunning(true);
        config.setCronSyntax(gitTask.getTaskType(),"* * * * *");
        config.setIsTaskConfigured(gitTask.getTaskType(),true);

        TaskScheduler scheduler = new TaskScheduler();
        scheduler.scheduleTasks();
    }
//
    // Todo Need a way to test termination of execution thread.

//    @Test
//    public void testTerminateThread(){
//
//    }

    class TestTaskExecutorHelper extends TaskExecutor {

        File testGitDir;
        public TestTaskExecutorHelper(Task maintenanceTask,File testGitDir) {
            super(maintenanceTask);
            this.testGitDir = testGitDir;
        }

        List<GitMaintenanceSCM.Cache> getCaches(){
            List<GitMaintenanceSCM.Cache> caches = new ArrayList<>();
            caches.add(new GitMaintenanceSCM.Cache(testGitDir,new ReentrantLock()));
            return caches;
        }
    }

    static List<Integer> getGitVersion(){

        final TaskListener procListener = StreamTaskListener.fromStderr();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new Launcher.LocalLauncher(procListener).launch().cmds("git", "--version").stdout(out).join();
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException("Couldn't fetch git maintenance command");
        }
        String versionOutput = "";
        try {
            versionOutput = out.toString(StandardCharsets.UTF_8.toString()).trim();
        } catch (UnsupportedEncodingException ue) {
            throw new RuntimeException("Unsupported encoding version");
        }
        final String[] fields = versionOutput.split(" ")[2].replaceAll("msysgit.", "").replaceAll("windows.", "").split("\\.");

        // Eg: [2, 31, 4]
        // 0th index is Major Version.
        // 1st index is Minor Version.
        // 2nd index is Patch Version.
        return Arrays.stream(fields).map(Integer::parseInt).collect(Collectors.toList());
    }
}
