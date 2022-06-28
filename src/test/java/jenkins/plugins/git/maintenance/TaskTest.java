package jenkins.plugins.git.maintenance;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(Parameterized.class)
public class TaskTest {

    private final Task task;
    private final TaskType taskType;
    private final String taskName;

    public TaskTest(Task task, TaskType taskType, String taskName) {
        this.task = task;
        this.taskType = taskType;
        this.taskName = taskName;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteTasks() {
        List<Object[]> tasks = new ArrayList<>();
        for (TaskType taskType : TaskType.values()) {
            Object[] task = {new Task(taskType), taskType, taskType.getTaskName()};
            tasks.add(task);
        }

        return tasks;
    }

    @Test
    public void getTaskType() {
        assertThat(task.getTaskType(), is(taskType));
    }

    @Test
    public void getTaskName() {
        assertThat(task.getTaskName(), is(taskName));
    }

    @Test
    public void setIsConfigured() {
        task.setIsTaskConfigured(true);
        assertTrue(task.getIsTaskConfigured());
    }

    @Test
    public void checkCronSyntax(){
        String cronSyntax = "* * 1 * 1";
        task.setCronSyntax(cronSyntax);
        assertThat(task.getCronSyntax(), is(cronSyntax));
    }

    @Test
    public void testGetTaskType() {
        assertThat(task.getTaskType(), is(taskType));
    }

    @Test
    public void testGetTaskName() {
        assertThat(task.getTaskName(), is(taskName));
    }

    @Test
    public void testGetCronSyntax(){
        String cronSyntax = "* * 1 * 1";
        task.setCronSyntax(cronSyntax);
        assertThat(task.getCronSyntax(), is(cronSyntax));
    }

    @Test
    public void testSetIsTaskConfigured() {
        task.setIsTaskConfigured(true);
        assertTrue(task.getIsTaskConfigured());
    }

    @Test
    public void testGetIsTaskConfigured() {
        task.setIsTaskConfigured(true);
        assertTrue(task.getIsTaskConfigured());
    }

    @Test
    public void testGetIsTaskConfiguredFalse() {
        task.setIsTaskConfigured(false);
        assertFalse(task.getIsTaskConfigured());
    }
}
