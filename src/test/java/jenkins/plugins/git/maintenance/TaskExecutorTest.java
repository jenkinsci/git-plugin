package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class TaskExecutorTest {

    TaskExecutor taskExecutor;

    public TaskExecutorTest(TaskExecutor taskExecutor){
        this.taskExecutor = taskExecutor;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteTasks() throws Exception {
        List<Object[]> tasksToBeExecuted = new ArrayList<>();

        MaintenanceTaskConfiguration config = new MaintenanceTaskConfiguration();
        // No need to add cron syntax as TaskExecutor class doesn't depend on cron syntax.
        List<Task> maintenanceTasks = config.getMaintenanceTasks();
        for (Task task : maintenanceTasks) {
            Object[] currentTaskData = {new TaskExecutor(task)};
            tasksToBeExecuted.add(currentTaskData);
        }

        return tasksToBeExecuted;
    }


    @Test
    public void testGetGitClient(){
        Jenkins jenkins = Jenkins.getInstanceOrNull();

        // taskExecutor.getGitClient();
    }
}
