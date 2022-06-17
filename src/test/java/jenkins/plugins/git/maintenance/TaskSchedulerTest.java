package jenkins.plugins.git.maintenance;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TaskSchedulerTest {

    private final TaskScheduler taskScheduler = new TaskScheduler();

    @Test
    public void scheduleMaintenanceTask(){
        boolean isFalse = false;

        // Need to discuss how to test this method
        taskScheduler.scheduleTasks();
        assertTrue(isFalse);
    }
}
