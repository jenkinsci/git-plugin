package jenkins.plugins.git.maintenance;

import com.google.gson.Gson;
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

        if(!isGitMaintenanceTaskRunning(config))
            return;

        List<Task> configuredTasks = config.getMaintenanceTasks();
        addTasksToQueue(configuredTasks);

        // Option of Using the same thread for executing more maintenance task, or create a new thread the next minute and execute the maintenance task.
        createTaskExecutorThread();
        System.out.println(taskExecutor.isAlive() + " Status of execution after");
    }

    boolean checkIsTaskInQueue(Task task){
        return maintenanceQueue.stream().anyMatch(queuedTask -> queuedTask.getTaskType().equals(task.getTaskType()));
    }

    void createTaskExecutorThread(){
        // Create a new thread and execute the tasks present in the queue;
        if(!maintenanceQueue.isEmpty() && (taskExecutor == null || !taskExecutor.isAlive())) {
            Task currentTask = maintenanceQueue.remove(0);
            Gson gson = new Gson();
            // To avoid mutatbility
            Task copyCurrentTask = gson.fromJson(gson.toJson(currentTask),Task.class);
            taskExecutor = new Thread(new TaskExecutor(copyCurrentTask), "maintenance-task-executor");
            taskExecutor.start();
        }
    }

    void addTasksToQueue(List<Task> configuredTasks){
        boolean isTaskExecutable;
        for(Task task : configuredTasks){
            if(!task.getIsTaskConfigured() || checkIsTaskInQueue(task))
                continue;

            isTaskExecutable = task.checkIsTaskExecutable(cal);
            if(isTaskExecutable){
                maintenanceQueue.add(task);
            }
        }
    }

    boolean isGitMaintenanceTaskRunning(MaintenanceTaskConfiguration config){
        return config.getIsGitMaintenanceRunning();
    }
}
