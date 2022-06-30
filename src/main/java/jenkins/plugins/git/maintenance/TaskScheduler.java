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
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaskScheduler {

    MaintenanceTaskConfiguration config;
    Calendar cal;
    List<Task> maintenanceQueue;
    Thread taskExecutor;

    private static final Logger LOGGER = Logger.getLogger(TaskScheduler.class.getName());

    public TaskScheduler(){
       this.config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
       this.cal = new GregorianCalendar();
       this.maintenanceQueue = new LinkedList<Task>();
    }

    public void scheduleTasks() {
        assert config != null;

        if(!isGitMaintenanceTaskRunning(config)) {
            // Logs ever 1 min. Need to check performance impact.
            LOGGER.log(Level.FINER,"Maintenance Task execution not configured");
            return;
        }

            List<Task> configuredTasks = config.getMaintenanceTasks();
            addTasksToQueue(configuredTasks);

            // Option of Using the same thread for executing more maintenance task, or create a new thread the next minute and execute the maintenance task.
            createTaskExecutorThread();
    }

    boolean checkIsTaskInQueue(Task task){
        return maintenanceQueue.stream().anyMatch(queuedTask -> queuedTask.getTaskType().equals(task.getTaskType()));
    }

    void createTaskExecutorThread(){
        // Create a new thread and execute the tasks present in the queue;
        if(!maintenanceQueue.isEmpty() && (taskExecutor == null || !taskExecutor.isAlive())) {
            Task currentTask = maintenanceQueue.remove(0);
            taskExecutor = new Thread(new TaskExecutor(currentTask), "maintenance-task-executor");
            taskExecutor.start();
            LOGGER.log(Level.FINE,"Thread created to execute " + currentTask.getTaskName() + " maintenance task.");
        }
    }

    void addTasksToQueue(List<Task> configuredTasks){

        boolean isTaskExecutable;
        for(Task task : configuredTasks){
            try {
                if(!task.getIsTaskConfigured() || checkIsTaskInQueue(task))
                    continue;

                CronTabList cronTabList = getCronTabList(task.getCronSyntax());
                isTaskExecutable = checkIsTaskExecutable(cronTabList);
                if(isTaskExecutable){
                    maintenanceQueue.add(task);
                    LOGGER.log(Level.FINE,task.getTaskName() + " added to maintenance queue.");
                }
            }catch (ANTLRException e){
                // Logged every minute. Need to check performance.
                LOGGER.log(Level.WARNING,"Invalid cron syntax:[ "+ task.getTaskName() + " ]" + ",msg: " + e.getMessage());
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
