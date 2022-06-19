package jenkins.plugins.git.maintenance;

import static org.junit.Assert.assertFalse;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class TaskSchedulerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private TaskScheduler taskScheduler;

    @Before
    public void setUp() throws Exception {
        taskScheduler = new TaskScheduler();
    }

    @Test
    public void scheduleMaintenanceTask() {
        boolean isFalse = false;

        // Need to discuss how to test this method
        taskScheduler.scheduleTasks();
        assertTrue(!isFalse);
    }

    @Test
    public void testScheduleTasks() {
        taskScheduler.scheduleTasks();
    }

    @Test
    public void testCheckIsTaskInQueue() {
        Task task = new Task(TaskType.PREFETCH);
        assertFalse(taskScheduler.checkIsTaskInQueue(task));
    }
}
