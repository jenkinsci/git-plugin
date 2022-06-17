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
    Thread taskExecutor;
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

        boolean isTaskExecutable;
        for(Task task : configuredTasks){
            if(!task.getIsTaskConfigured() || checkIsTaskInQueue(task))
                continue;

            isTaskExecutable = task.checkIsTaskExecutable(cal);
            if(isTaskExecutable){
                maintenanceQueue.add(task);
            }
        }

        // Create a new thread and execute the tasks present in the queue;
        if(!maintenanceQueue.isEmpty() && (taskExecutor == null || !taskExecutor.isAlive())) {
            System.out.println("Entered this statement");
            taskExecutor = new Thread(new TaskExecutor(maintenanceQueue), "maintenance-task-executor");
            taskExecutor.start();
        }

        System.out.println(taskExecutor.isAlive() + " Status of execution after");
        System.out.println(maintenanceQueue);
    }

    private boolean checkIsTaskInQueue(Task task){
        return maintenanceQueue.stream().anyMatch(queuedTask -> queuedTask.getTaskType().equals(task.getTaskType()));
    }
}
