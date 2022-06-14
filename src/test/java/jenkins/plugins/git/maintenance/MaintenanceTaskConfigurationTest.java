package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MaintenanceTaskConfigurationTest {

    private final MaintenanceTaskConfiguration config = new MaintenanceTaskConfiguration();

//    @Test
//    public void configureMaintenanceTasks(){
//        config.configureMaintenanceTasks();
//        assertEquals(TaskType.GC,config.getMaintenanceTasks().get(TaskType.GC).task);
//        assertEquals(TaskType.PREFETCH,config.getMaintenanceTasks().get(TaskType.PREFETCH).task);
//        assertEquals(TaskType.INCREMENTAL_REPACK,config.getMaintenanceTasks().get(TaskType.INCREMENTAL_REPACK).task);
//        assertEquals(TaskType.LOOSE_OBJECTS,config.getMaintenanceTasks().get(TaskType.LOOSE_OBJECTS).task);
//        assertEquals(TaskType.COMMIT_GRAPH,config.getMaintenanceTasks().get(TaskType.COMMIT_GRAPH).task);
//    }

    @Test
    public void setCronSyntax(){
        String cronSyntax = "* * * 1 *";
        for(TaskType taskType : TaskType.values()){
            config.setCronSyntax(taskType,cronSyntax);
        }

        for(TaskType taskType : TaskType.values()){
            Task maintenanceTask = config.getMaintenanceTasks().get(taskType);
            assertEquals(cronSyntax,maintenanceTask.getCronSyntax());
        }
    }

    @Test
    public void setIsMaintenanceTaskConfigured(){
        boolean isMaintenanceTaskConfigured = true;

        for(TaskType taskType : TaskType.values()){
            config.setIsTaskConfigured(taskType,isMaintenanceTaskConfigured);
        }

        for(TaskType taskType : TaskType.values()){
            Task maintenanceTask = config.getMaintenanceTasks().get(taskType);
            assertEquals(isMaintenanceTaskConfigured,maintenanceTask.getIsTaskConfigured());
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

        // Doesn't throw any error
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

}
