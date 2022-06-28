package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import hudson.scheduler.CronTab;
import hudson.scheduler.CronTabList;
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
       this.maintenanceQueue = new LinkedList<Task>();
    }

    public void scheduleTasks() {
        assert config != null;

        if(!isGitMaintenanceTaskRunning(config))
            return;

        try {
            List<Task> configuredTasks = config.getMaintenanceTasks();
            addTasksToQueue(configuredTasks);

            // Option of Using the same thread for executing more maintenance task, or create a new thread the next minute and execute the maintenance task.
            createTaskExecutorThread();

        }catch (ANTLRException e){
            // Log the error to a log file...

        }
        System.out.println(taskExecutor.isAlive() + " Status of execution after");
    }

    boolean checkIsTaskInQueue(Task task){
        return maintenanceQueue.stream().anyMatch(queuedTask -> queuedTask.getTaskType().equals(task.getTaskType()));
    }

    void createTaskExecutorThread() throws ANTLRException {
        // Create a new thread and execute the tasks present in the queue;
        if(!maintenanceQueue.isEmpty() && (taskExecutor == null || !taskExecutor.isAlive())) {
            Task currentTask = maintenanceQueue.remove(0);
            taskExecutor = new Thread(new TaskExecutor(currentTask), "maintenance-task-executor");
            taskExecutor.start();
            System.out.println("Executing maintenance task " + currentTask.getTaskName());
        }
    }

    void addTasksToQueue(List<Task> configuredTasks) throws ANTLRException {
        boolean isTaskExecutable;
        for(Task task : configuredTasks){
            if(!task.getIsTaskConfigured() || checkIsTaskInQueue(task))
                continue;

            CronTabList cronTabList = getCronTabList(task.getCronSyntax());
            isTaskExecutable = checkIsTaskExecutable(cronTabList);
            if(isTaskExecutable){
                maintenanceQueue.add(task);
            }
        }
    }

    boolean isGitMaintenanceTaskRunning(MaintenanceTaskConfiguration config){
        return config.getIsGitMaintenanceRunning();
    }

    CronTabList getCronTabList(String cronSyntax) throws ANTLRException {
        CronTab cronTab = new CronTab(cronSyntax.trim());
        return new CronTabList(Collections.singletonList(cronTab));
    }

    boolean checkIsTaskExecutable(CronTabList cronTabList){
        boolean isTaskExecutable = false;

        isTaskExecutable = cronTabList.check(cal);
        // Further validation such as not schedule a task every minute etc. can be added here.

        return isTaskExecutable;
    }
}
