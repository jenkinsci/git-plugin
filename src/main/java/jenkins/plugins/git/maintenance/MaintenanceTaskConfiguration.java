package jenkins.plugins.git.maintenance;

import java.util.HashMap;
import java.util.Map;

public class MaintenanceTaskConfiguration {

    Map<TaskType,Task> maintenanceTasks;

    public MaintenanceTaskConfiguration(){
        configureMaintenanceTasks();
    }
    private void configureMaintenanceTasks(){
       // check git version and based on git version, add the maintenance tasks to the list
        maintenanceTasks = new HashMap<>();

        maintenanceTasks.put(TaskType.COMMIT_GRAPH,new Task(TaskType.COMMIT_GRAPH,"Commit Graph msg"));
        maintenanceTasks.put(TaskType.PREFETCH,new Task(TaskType.PREFETCH,"Prefetch msg"));
        maintenanceTasks.put(TaskType.GC,new Task(TaskType.GC,"Gc msg"));
        maintenanceTasks.put(TaskType.LOOSE_OBJECTS,new Task(TaskType.LOOSE_OBJECTS,"Loose Objects msg"));
        maintenanceTasks.put(TaskType.INCREMENTAL_REPACK,new Task(TaskType.INCREMENTAL_REPACK,"Incremental Repack msg"));
    }

    public Map<TaskType, Task> getMaintenanceTasks(){
        return maintenanceTasks;
    }

    public void setCronSyntax(TaskType taskType, String cronSyntax){
        Task updatedTask = maintenanceTasks.get(taskType);
        updatedTask.setCronSyntax(cronSyntax);
        maintenanceTasks.put(taskType,updatedTask);
    }


}
