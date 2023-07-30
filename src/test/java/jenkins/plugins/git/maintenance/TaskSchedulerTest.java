package jenkins.plugins.git.maintenance;

import jenkins.model.GlobalConfiguration;
import jenkins.plugins.git.GitSampleRepoRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TaskSchedulerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();

    private TaskScheduler taskScheduler;
    private MaintenanceTaskConfiguration config;

    @Before
    public void setUp() throws Exception {
        taskScheduler = new TaskScheduler();
        config = new MaintenanceTaskConfiguration();
    }

    // Tested all the internal functions of this method
    @Test
    public void testScheduleTasks() {
        config.setIsGitMaintenanceRunning(true);
        config.setCronSyntax(TaskType.PREFETCH,"* * * * *");
        config.setIsTaskConfigured(TaskType.PREFETCH,true);
        taskScheduler.scheduleTasks();
    }

    @Test
    public void testCheckIsTaskInQueue(){
        config.setCronSyntax(TaskType.PREFETCH,"* * * * *");
        config.setIsTaskConfigured(TaskType.PREFETCH,true);

        config.setCronSyntax(TaskType.COMMIT_GRAPH,"* * * * *");
        config.setIsTaskConfigured(TaskType.COMMIT_GRAPH,true);

        List<Task> maintenanceTasks = config.getMaintenanceTasks();
        taskScheduler.addTasksToQueue(maintenanceTasks);

        List<Task> configuredTask = config.getMaintenanceTasks().stream().filter(Task::getIsTaskConfigured).collect(Collectors.toList());

        configuredTask.forEach(task -> assertTrue(taskScheduler.checkIsTaskInQueue(task)));
    }

    @Test
    public void testAddTasksToQueue() {
        // Adding Maintenance tasks configuration;
        config.setCronSyntax(TaskType.PREFETCH,"* * * * *");
        config.setIsTaskConfigured(TaskType.PREFETCH,true);
        config.setCronSyntax(TaskType.LOOSE_OBJECTS,"* * * * *");
        config.setIsTaskConfigured(TaskType.LOOSE_OBJECTS,true);

        config.setCronSyntax(TaskType.GC,"5 1 1 1 1");
        config.setIsTaskConfigured(TaskType.GC,true);
        config.setCronSyntax(TaskType.COMMIT_GRAPH,"H * * * *");
        List<Task> maintenanceTasks = config.getMaintenanceTasks();

        int length = 2;

        taskScheduler.addTasksToQueue(maintenanceTasks);
        assertThat(taskScheduler.getMaintenanceQueue().size(),is(length));
    }

    @Test
    public void testInvalidAddTasksToQueue() {
        config.setCronSyntax(TaskType.PREFETCH,"*****");
        config.setIsTaskConfigured(TaskType.PREFETCH,true);
        List<Task> maintenanceTasks = config.getMaintenanceTasks();
        taskScheduler.addTasksToQueue(maintenanceTasks);
        assertThat(taskScheduler.getMaintenanceQueue().size(),is(0));
    }

    @Test
    public void testIsGitMaintenanceTaskRunning(){
        // Setting value to true
        config.setIsGitMaintenanceRunning(true);
        boolean isGitMaintenanceTaskRunning = taskScheduler.isGitMaintenanceTaskRunning(config);
        assertTrue(isGitMaintenanceTaskRunning);

        // set value to false
        config.setIsGitMaintenanceRunning(false);
        isGitMaintenanceTaskRunning = taskScheduler.isGitMaintenanceTaskRunning(config);
        assertFalse(isGitMaintenanceTaskRunning);
    }

    @Test
    public void testCreateNoExecutorThread(){
        config.setCronSyntax(TaskType.PREFETCH,"5 1 1 1 1");
        config.setIsTaskConfigured(TaskType.PREFETCH,true);

        List<Task> maintenanceTasks = config.getMaintenanceTasks();
        taskScheduler.addTasksToQueue(maintenanceTasks);
        taskScheduler.createTaskExecutorThread();
        assertNull(taskScheduler.getTaskExecutor());

    }

    @Test
    public void testCreateExecutionThread(){

        config.setCronSyntax(TaskType.PREFETCH,"* * * * *");
        config.setIsTaskConfigured(TaskType.PREFETCH,true);

        List<Task> maintenanceTasks = config.getMaintenanceTasks();
        taskScheduler.addTasksToQueue(maintenanceTasks);
        taskScheduler.createTaskExecutorThread();
        assertTrue(taskScheduler.getTaskExecutor().isAlive());
    }

    @Test
    public void testTerminateMaintenanceTask(){
        taskScheduler.terminateMaintenanceTaskExecution();
        assertNull(taskScheduler.getTaskExecutor());
        assertEquals(0,taskScheduler.getMaintenanceQueue().size());
    }

    // Need to revist this test
//    @Test
//    public void testTerminateMaintenanceTaskDuringThreadExecution() throws Exception {
//        config.setCronSyntax(TaskType.PREFETCH,"* * * * *");
//        config.setIsTaskConfigured(TaskType.PREFETCH,true);
//        // Need to add few caches to test if the Thread is being terminated...
//
//        sampleRepo1.init();
//        sampleRepo1.git("checkout", "-b", "bug/JENKINS-42817");
//        sampleRepo1.write("file", "modified");
//        sampleRepo1.git("commit", "--all", "--message=dev");
//
//        SCMFileSystem.of(j.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo1.toString(), null), Collections.singletonList(new BranchSpec("*/bug/JENKINS-42817")), null, null, Collections.emptyList()));
//
//        List<Task> tasks = config.getMaintenanceTasks();
//        taskScheduler.addTasksToQueue(tasks);
//        taskScheduler.createTaskExecutorThread();
//
//        assertTrue(taskScheduler.getTaskExecutor().isAlive());
//        taskScheduler.terminateMaintenanceTaskExecution();
//        // This test could depend on CPU speed. Faster execution can fail the test.
//        Thread.sleep(1);
//        assertFalse(taskScheduler.getTaskExecutor().isAlive());
//    }

}
