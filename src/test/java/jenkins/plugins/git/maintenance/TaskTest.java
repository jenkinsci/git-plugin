package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class TaskTest {

    private final Task task;
    private final TaskType taskType;
    private final String taskName;

    public TaskTest(Task task,TaskType taskType,String taskName){
        this.task = task;
        this.taskType = taskType;
        this.taskName = taskName;
    }

    @Parameterized.Parameters(name="{0}")
    public static Collection permuteTasks(){
        List<Object[]> tasks = new ArrayList<>();
        for(TaskType taskType : TaskType.values()){
            Object[] task = {new Task(taskType),taskType,taskType.getTaskName()};
            tasks.add(task);
        }

        return tasks;
    }

    @Test
    public void getTaskType(){
        assertEquals(taskType,task.getTaskType());
    }

    @Test
    public void getTaskName(){
        assertEquals(taskName,task.getTaskName());
    }

    @Test
    public void setIsConfigured(){
        boolean isConfigured = true;
        task.setIsTaskConfigured(isConfigured);
        assertEquals(isConfigured,task.getIsTaskConfigured());
    }

    @Test
    public void checkCronSyntax() throws ANTLRException {
        String cronSyntax = "* * 1 * 1";
        task.setCronSyntax(cronSyntax);
        assertEquals(cronSyntax,task.getCronSyntax());
    }

    @Test
    public void isTaskExecutable() throws ANTLRException {
        Calendar cal = new GregorianCalendar();
        String cronSyntax = "* * * * *";
        task.setCronSyntax(cronSyntax);
        boolean isTaskExecutable = task.checkIsTaskExecutable(cal);
        assertTrue(isTaskExecutable);

        cronSyntax = "H * * * *";
        task.setCronSyntax(cronSyntax);
        isTaskExecutable = task.checkIsTaskExecutable(cal);
        assertFalse(isTaskExecutable);
    }
}
