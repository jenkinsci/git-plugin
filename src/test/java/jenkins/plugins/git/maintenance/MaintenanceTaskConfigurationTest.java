package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertFalse;

public class MaintenanceTaskConfigurationTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    private final MaintenanceTaskConfiguration config = new MaintenanceTaskConfiguration();

    @Test
    public void setCronSyntax() throws ANTLRException {
        String cronSyntax = "* * * 1 *";
        for(TaskType taskType : TaskType.values()){
            config.setCronSyntax(taskType,cronSyntax);
        }

        for(TaskType taskType : TaskType.values()){
            List<Task> maintenanceTasks = config.getMaintenanceTasks();
            for(Task task : maintenanceTasks){
                if(task.getTaskType().equals(taskType)){
                    assertThat(task.getCronSyntax(), is(cronSyntax));
                    break;
                }
            }
        }
    }

    @Test
    public void setIsMaintenanceTaskConfigured(){
        boolean isMaintenanceTaskConfigured = true;

        for(TaskType taskType : TaskType.values()){
            config.setIsTaskConfigured(taskType,isMaintenanceTaskConfigured);
        }

        for(TaskType taskType : TaskType.values()){
            List<Task> maintenanceTasks = config.getMaintenanceTasks();
            for(Task task : maintenanceTasks){
                if(task.getTaskType().equals(taskType)){
                    assertThat(task.getIsTaskConfigured(), is(isMaintenanceTaskConfigured));
                    break;
                }
            }
        }
    }

    @Test
    public void setIsMaintenanceTaskRunning(){
        // Default status
        assertFalse(config.getIsGitMaintenanceRunning());

        // When status is set to true
        // Maintenance needs to be running
        // assertTrue(config.getIsGitMaintenanceRunning());
    }

    @Test
    public void checkValidCronSyntax() throws ANTLRException {

        MaintenanceTaskConfiguration.checkSanity("* * * * *");
        MaintenanceTaskConfiguration.checkSanity("1 * * * * ");
        MaintenanceTaskConfiguration.checkSanity("H H(8-15)/2 * * 1-5");
        MaintenanceTaskConfiguration.checkSanity("H H 1,15 1-11 *");
    }

    @Test(expected = ANTLRException.class)
    public void checkInvalidCronSyntax() throws ANTLRException{
        MaintenanceTaskConfiguration.checkSanity("");
        MaintenanceTaskConfiguration.checkSanity("*****");
        MaintenanceTaskConfiguration.checkSanity("a * * 1 *");
    }

    @Test
    public void testGetGitVersion(){
        List<Integer> gitVersion = MaintenanceTaskConfiguration.getGitVersion();
        assertThat("Version list size error", gitVersion.size(), is(greaterThan(1)));
        assertThat("Major version out of range", gitVersion.get(0), is(both(greaterThan(0)).and(lessThan(99))));
        assertThat("Minor version out of range", gitVersion.get(1), is(both(greaterThan(-1)).and(lessThan(99))));
        if (gitVersion.size() > 2) {
            assertThat("Patch version out of range", gitVersion.get(2), is(both(greaterThan(-1)).and(lessThan(99))));
        }
    }

    // This test depends on the computers git version.
//    @Test
//    public void testGitVersionAtLeast(){
//        assertTrue(MaintenanceTaskConfiguration.gitVersionAtLeast());
//        assertTrue(MaintenanceTaskConfiguration.gitVersionAtLeast());
//        assertTrue(MaintenanceTaskConfiguration.gitVersionAtLeast());
//        assertTrue(MaintenanceTaskConfiguration.gitVersionAtLeast());
//    }
}
