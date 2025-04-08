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

/**
 * TaskScheduler is responsible for scheduling maintenance tasks. It validates if a task is configured and verifies cron syntax before scheduling.
 * TaskScheduler acts as a producer by adding the appropriate task into a maintenance queue.
 *
 * @author Hrushikesh Rao
 */
public class TaskScheduler {

    private MaintenanceTaskConfiguration config;

    /**
     * Stores the order of execution of maintenance tasks.
     */
    private List<Task> maintenanceQueue;

    /**
     * {@link TaskExecutor} executes the maintenance tasks on all the caches.
     */
    private Thread taskExecutor;
    private TaskExecutor taskExecutorRunnable;

    private static final Logger LOGGER = Logger.getLogger(TaskScheduler.class.getName());
    private static final Random random = new Random();

    /**
     * Loads the maintenance configuration configured by the administrator. Also initializes an empty maintenance queue.
     */
    public TaskScheduler(){
       this.config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
       this.maintenanceQueue = new LinkedList<>();
       LOGGER.log(Level.FINE,"TaskScheduler class Initialized.");
    }

    /**
     * Schedules maintenance tasks on caches.
     */
    void scheduleTasks() {
        if(config != null) {
            if (!isGitMaintenanceTaskRunning(config)) {
                // Logs ever 1 min. Need to check performance impact.
                LOGGER.log(Level.FINER, "Maintenance Task execution not configured in UI.");
                return;
            }

            List<Task> maintenanceTasks = config.getMaintenanceTasks();
            addTasksToQueue(maintenanceTasks);
            // Option of Using the same thread for executing more maintenance task, or create a new thread the next minute and execute the maintenance task.
            createTaskExecutorThread();
        }else{
            LOGGER.log(Level.FINE,"Couldn't load Global git maintenance configuration. Internal Error.");
        }

    }

    /**
     * Checks for duplication of maintenance task in the queue.
     *
     * @param task type of maintenance task ({@link Task}).
     * @return a boolean to see if a task is already present in maintenance queue.
     */
    boolean checkIsTaskInQueue(Task task){
        boolean isTaskInQueue = maintenanceQueue.stream().anyMatch(queuedTask -> queuedTask.getTaskType().equals(task.getTaskType()));
        if(isTaskInQueue){
            LOGGER.log(Level.FINE,task.getTaskName() + " is already present in maintenance queue.");
        }
        return isTaskInQueue;
    }

    /**
     *  A new Thread {@link TaskExecutor} is created which executes the maintenance task on caches.
     */
    void createTaskExecutorThread(){
        // Create a new thread and execute the tasks present in the queue;
        if(!maintenanceQueue.isEmpty() && (taskExecutor == null || !taskExecutor.isAlive())) {
            Task currentTask = maintenanceQueue.remove(0);
            taskExecutorRunnable = new TaskExecutor(currentTask);
            taskExecutor = new Thread(taskExecutorRunnable, "maintenance-task-executor");
            taskExecutor.start();
            LOGGER.log(Level.INFO,"Thread [" + taskExecutor.getName() +"] created to execute " + currentTask.getTaskName() + " task.");
        }
    }

    /**
     * Iterates through all the maintenance tasks and adds the task to queue if valid.
     *
     * @param maintenanceTasks List of maintenance tasks {@link Task}
     */
    void addTasksToQueue(List<Task> maintenanceTasks){

        boolean isTaskExecutable;
        for(Task task : maintenanceTasks){
            try {
                if(!task.getIsTaskConfigured() || checkIsTaskInQueue(task))
                    continue;

                CronTabList cronTabList = getCronTabList(task.getCronSyntax());
                isTaskExecutable = checkIsTaskExecutable(cronTabList);
                if(isTaskExecutable){
                    maintenanceQueue.add(task);
                    LOGGER.log(Level.INFO,task.getTaskName() + " added to maintenance queue.");
                }
            }catch (ANTLRException e){
                // Logged every minute. Need to check performance.
                LOGGER.log(Level.WARNING,"Invalid cron syntax:[ "+ task.getTaskName() + " ]" + ",msg: " + e.getMessage());
            }
        }
    }

    /**
     * Checks if global git maintenance is configured.
     * @param config {@link MaintenanceTaskConfiguration}.
     * @return Global git maintenance is configured or not.
     */
    boolean isGitMaintenanceTaskRunning(MaintenanceTaskConfiguration config){
        return config.getIsGitMaintenanceRunning();
    }

    /**
     * Returns CronTabList for a cronSyntax.
     *
     * @param cronSyntax cron syntax for maintenance task.
     * @return {@link CronTabList}
     * @throws ANTLRException
     */
    CronTabList getCronTabList(String cronSyntax) throws ANTLRException {
        // Random number between 0 & 100000
        String seed = String.valueOf((random.nextInt(100000)));
        CronTab cronTab = new CronTab(cronSyntax.trim(), Hash.from(seed));
        return new CronTabList(Collections.singletonList(cronTab));
    }

    /**
     * Checks if the cron syntax matches the current time. Returns true if valid.
     *
     * @param cronTabList {@link CronTabList}
     * @return maintenance task should be scheduled or not.
     */
    boolean checkIsTaskExecutable(CronTabList cronTabList){
        boolean isTaskExecutable = false;

        Calendar cal = new GregorianCalendar();
        isTaskExecutable = cronTabList.check(cal);
        // Further validation such as not schedule a task every minute etc. can be added here.

        return isTaskExecutable;
    }

    /**
     * Terminates the {@link TaskExecutor} thread to stop executing maintenance on caches. Also empties the maintenance queue.
     */
    void terminateMaintenanceTaskExecution(){
        this.maintenanceQueue = new LinkedList<>();
        if(taskExecutor != null && taskExecutor.isAlive())
            taskExecutorRunnable.terminateThread();

        LOGGER.log(Level.INFO,"Terminated Execution of maintenance tasks");
    }

    List<Task> getMaintenanceQueue(){
        return maintenanceQueue;
    }
    Thread getTaskExecutor(){
        return taskExecutor;
    }
}
