package jenkins.plugins.git.maintenance;

import java.util.ArrayList;
import java.util.List;

public class TaskExecutor implements Runnable {

    List<Task> maintenanceQueue;

    public TaskExecutor(List<Task> maintenanceQueue){
        /* Defensive copy avoids risk of caller modifying the queue after object creation */
        this.maintenanceQueue = new ArrayList<>(maintenanceQueue);
    }

    public int queueLength() {
        return maintenanceQueue.size();
    }

    @Override
    public void run() {
        // Need a way to iterate through all the caches present on Jenkins controller.
        // Need to get the Git Client from the Git-Client-Plugin.
        // Execute Maintenance Tasks in this class.
    }
}
