package jenkins.plugins.git.maintenance;

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


}
