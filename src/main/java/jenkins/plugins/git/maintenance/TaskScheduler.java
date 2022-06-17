package jenkins.plugins.git.maintenance;

import jenkins.model.GlobalConfiguration;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;

public class TaskScheduler {

    MaintenanceTaskConfiguration config;
    Calendar cal;
    List<Task> maintenanceQueue;
    public TaskScheduler(){
       this.config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
       this.cal = new GregorianCalendar();
       this.maintenanceQueue = Collections.synchronizedList(new LinkedList<Task>());
    }

    public void scheduleTasks(){
        assert config != null;

        if(!config.getIsGitMaintenanceRunning())
            return;

        List<Task> configuredTasks = config.getMaintenanceTasks();

        boolean isSchedulable = false;
//        for(Task task : configuredTasks){
//            isSchedulable = task.isTaskSchedulable(cal);
//
//
//        }
    }
}
