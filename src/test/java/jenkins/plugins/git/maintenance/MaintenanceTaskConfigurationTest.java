package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

import static org.junit.Assert.assertEquals;

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
                    assertEquals(cronSyntax,task.getCronSyntax());
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
                    assertEquals(isMaintenanceTaskConfigured,task.getIsTaskConfigured());
                    break;
                }
            }
        }
    }

    @Test
    public void setIsMaintenanceTaskRunning(){
        // When status is false.
        boolean status = config.getIsGitMaintenanceRunning();
        assertEquals(false,status);

        // When status is set to true
//        status = config.setIsGitMaintenanceTaskRunning();
//        assertEquals(true,status);
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
        assertEquals(3,gitVersion.size());
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
