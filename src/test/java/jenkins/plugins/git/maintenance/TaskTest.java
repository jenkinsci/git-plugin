package jenkins.plugins.git.maintenance;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
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
    public void checkCronSyntax() throws Exception {
        String cronSyntax = "* * 1 * 1";
        task.setCronSyntax(cronSyntax);
        assertThat(task.getCronSyntax(), is(cronSyntax));
    }

    @Test
    public void isTaskExecutable() throws Exception {
        Calendar cal = new GregorianCalendar();
        String cronSyntax = "* * * * *";
        task.setCronSyntax(cronSyntax);
        boolean isTaskExecutable = task.checkIsTaskExecutable(cal);
        assertTrue(isTaskExecutable);
    }

    @Test
    public void isTaskExecutableWithHSyntax() throws Exception {
        Calendar cal = new GregorianCalendar();
        String cronSyntax = "H * * * *";
        task.setCronSyntax(cronSyntax);
        boolean isTaskExecutable = task.checkIsTaskExecutable(cal);
        assertFalse(isTaskExecutable);
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
    public void testGetCronSyntax() throws Exception {
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

    // Syntax not yet supported by this class
    // Should parameterize test for cron syntax as well
    // @Test
    public void testSetCronSyntaxDaily() throws Exception {
        Calendar cal = new GregorianCalendar();
        task.setCronSyntax("@daily");
        assertTrue(task.checkIsTaskExecutable(cal));
    }

    // Should parameterize test for cron syntax as well
    @Test
    public void testCheckIsTaskExecutable() throws Exception {
        Calendar cal = new GregorianCalendar();
        String cronSyntax = "* * * * *";
        task.setCronSyntax(cronSyntax);
        assertTrue(task.checkIsTaskExecutable(cal));
    }

    // Should parameterize test for cron syntax as well
    @Test
    public void testCheckIsTaskExecutableWithHSyntax() throws Exception {
        Calendar cal = new GregorianCalendar();
        task.setCronSyntax("H * * * *");
        assertFalse(task.checkIsTaskExecutable(cal));
    }
}
