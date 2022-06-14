package jenkins.plugins.git.maintenance;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class TaskTest {

    private Task task;
    private TaskType taskType;
    private String taskName;

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
    public void checkCronSyntax(){
        String cronSyntax = "* * 1 * 1";
        task.setCronSyntax(cronSyntax);
        assertEquals(cronSyntax,task.getCronSyntax());
    }
}
