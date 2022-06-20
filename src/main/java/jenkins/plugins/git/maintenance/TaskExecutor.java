package jenkins.plugins.git.maintenance;

import java.io.File;

public class TaskExecutor implements Runnable {

    Task maintenanceTask;
    File[] cachesDir;

    public TaskExecutor(Task maintenanceTask){
        this.maintenanceTask = maintenanceTask;
        cachesDir = getCachesDir();
    }

    @Override
    public void run() {
        // Need a way to iterate through all the caches present on Jenkins controller.
        // Need to get the Git Client from the Git-Client-Plugin.
        // Execute Maintenance Tasks in this class.

    }

    File[] getCachesDir(){
        return GitMaintenanceSCM.getCachesDirectory();
    }
}
