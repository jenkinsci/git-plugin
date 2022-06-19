package jenkins.plugins.git.maintenance;

public class TaskExecutor implements Runnable {

    Task maintenanceTask;

    public TaskExecutor(Task maintenanceTask){
        this.maintenanceTask = maintenanceTask;
    }

    @Override
    public void run() {
        // Need a way to iterate through all the caches present on Jenkins controller.
        // Need to get the Git Client from the Git-Client-Plugin.
        // Execute Maintenance Tasks in this class.

    }
}
