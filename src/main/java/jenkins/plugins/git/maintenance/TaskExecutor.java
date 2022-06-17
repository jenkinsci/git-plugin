package jenkins.plugins.git.maintenance;

import java.util.List;

public class TaskExecutor implements Runnable {

    List<Task> maintenanceQueue;
    public TaskExecutor(List<Task> maintenanceQueue){
        this.maintenanceQueue = maintenanceQueue;
    }
    @Override
    public void run() {
        // Need a way to iterate through all the caches present on Jenkins controller.
        // Need to get the Git Client from the Git-Client-Plugin.
        // Execute Maintenance Tasks in this class.
    }
}
