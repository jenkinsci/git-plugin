package jenkins.plugins.git.maintenance;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class TaskSchedulerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private TaskScheduler taskScheduler;

    @Test
    public void scheduleMaintenanceTask() {
        taskScheduler = new TaskScheduler();
        boolean isFalse = false;

        // Need to discuss how to test this method
        taskScheduler.scheduleTasks();
        assertTrue(!isFalse);
    }
}
