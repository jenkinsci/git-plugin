package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import hudson.scheduler.CronTab;
import hudson.scheduler.CronTabList;
import hudson.scheduler.Hash;
import jenkins.model.GlobalConfiguration;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaskScheduler {

    private MaintenanceTaskConfiguration config;
    private Calendar cal;
    private List<Task> maintenanceQueue;
    private Thread taskExecutor;
    private TaskExecutor taskExecutorRunnable;

    private static final Logger LOGGER = Logger.getLogger(TaskScheduler.class.getName());
    private static final Random random = new Random();

    public TaskScheduler(){
       this.config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
       this.cal = new GregorianCalendar();
       this.maintenanceQueue = new LinkedList<Task>();
       LOGGER.log(Level.FINE,"TaskScheduler class Initialized.");
    }

    public void scheduleTasks() {
        if(config != null) {
            if (!isGitMaintenanceTaskRunning(config)) {
                // Logs ever 1 min. Need to check performance impact.
                LOGGER.log(Level.FINER, "Maintenance Task execution not configured in UI.");
                return;
            }

            List<Task> configuredTasks = config.getMaintenanceTasks();
            addTasksToQueue(configuredTasks);
            // Option of Using the same thread for executing more maintenance task, or create a new thread the next minute and execute the maintenance task.
            createTaskExecutorThread();
        }else{
            LOGGER.log(Level.FINE,"Couldn't load Global git maintenance configuration. Internal Error.");
        }

    }

    boolean checkIsTaskInQueue(Task task){
        boolean isTaskInQueue = maintenanceQueue.stream().anyMatch(queuedTask -> queuedTask.getTaskType().equals(task.getTaskType()));
        if(isTaskInQueue){
            LOGGER.log(Level.FINE,task.getTaskName() + " is already present in maintenance queue.");
        }
        return isTaskInQueue;
    }

    void createTaskExecutorThread(){
        // Create a new thread and execute the tasks present in the queue;
        if(!maintenanceQueue.isEmpty() && (taskExecutor == null || !taskExecutor.isAlive())) {
            Task currentTask = maintenanceQueue.remove(0);
            taskExecutorRunnable = new TaskExecutor(currentTask);
            taskExecutor = new Thread(taskExecutorRunnable, "maintenance-task-executor");
            taskExecutor.start();
            LOGGER.log(Level.FINE,"Thread [" + taskExecutor.getName() +"] created to execute " + currentTask.getTaskName() + " task.");
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
        // Random number between 0 & 100000
        String seed = String.valueOf((random.nextInt(100000)));
        CronTab cronTab = new CronTab(cronSyntax.trim(), Hash.from(seed));
        return new CronTabList(Collections.singletonList(cronTab));
    }

    boolean checkIsTaskExecutable(CronTabList cronTabList){
        boolean isTaskExecutable = false;

        isTaskExecutable = cronTabList.check(cal);
        // Further validation such as not schedule a task every minute etc. can be added here.

        return isTaskExecutable;
    }

    void terminateMaintenanceTaskExecution(){
        this.maintenanceQueue = new LinkedList<>();
        if(taskExecutor != null && taskExecutor.isAlive())
            taskExecutorRunnable.terminateThread();

        LOGGER.log(Level.FINE,"Terminated Execution of maintenance tasks");
    }

    List<Task> getMaintenanceQueue(){
        return maintenanceQueue;
    }
    Thread getTaskExecutor(){
        return taskExecutor;
    }
}
