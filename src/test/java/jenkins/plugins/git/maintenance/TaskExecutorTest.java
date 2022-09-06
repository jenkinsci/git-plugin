package jenkins.plugins.git.maintenance;

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import jenkins.model.GlobalConfiguration;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMFileSystem;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class TaskExecutorTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();

    private static TaskExecutor taskExecutor;
    private TaskType taskType;

    public TaskExecutorTest(TaskType taskType) {
            MaintenanceTaskConfiguration config = new MaintenanceTaskConfiguration();
            config.setIsTaskConfigured(taskType, true);
            config.setCronSyntax(taskType, "* * * * *");
            List<Task> tasks = config.getMaintenanceTasks().stream().filter(Task::getIsTaskConfigured).collect(Collectors.toList());
            Task configuredTask = tasks.get(0);
            taskExecutor = new TaskExecutor(configuredTask);
            this.taskType = taskType;
    }

    @BeforeClass
    public static void setUp() throws Exception{

        sampleRepo1.init();
        sampleRepo1.git("checkout", "-b", "bug/JENKINS-42817");
        sampleRepo1.write("file", "modified");
        sampleRepo1.git("commit", "--all", "--message=dev");

        // Create caches on Jenkins controller.
        SCMFileSystem.of(j.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo1.toString(), null), Collections.singletonList(new BranchSpec("*/bug/JENKINS-42817")), null, null, Collections.emptyList()));

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
        System.out.println(taskExecutor.getCaches());
        assertTrue(taskExecutor.getCaches().size() > 0);
        GitMaintenanceSCM.Cache cache = taskExecutor.getCaches().get(0);
        File cacheFile = cache.getCacheFile();

        assertNotNull(taskExecutor.getGitClient(cacheFile));
        assertThat(taskExecutor.getGitClient(cacheFile),instanceOf(GitClient.class));
    }

    @Test
    public void testNullFileInGetGitClient() {
        GitClient client = taskExecutor.getGitClient(null);
        assertNull(client);
    }

    @Test
    public void testGetCaches(){
        assertNotNull(taskExecutor.getCaches());
    }


    // Test doesn't returns any result
    @Test
    public void testExecuteGitMaintenance() throws InterruptedException {
        GitMaintenanceSCM.Cache cache = taskExecutor.getCaches().get(0);
        File cacheFile = cache.getCacheFile();
        GitClient client = taskExecutor.getGitClient(cacheFile);
        taskExecutor.executeGitMaintenance(client,taskType);
    }

    // Test doesn't returns any result
    @Test
    public void testRunnable() {
        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
        config.setIsGitMaintenanceRunning(true);
        config.setCronSyntax(taskType,"* * * * *");
        config.setIsTaskConfigured(taskType,true);

        TaskScheduler scheduler = new TaskScheduler();
        scheduler.scheduleTasks();
    }

    // Todo Need a way to test termination of execution thread.

//    @Test
//    public void testTerminateThread(){
//
//    }
}
