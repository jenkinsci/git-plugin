package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.scheduler.CronTab;
import jenkins.model.GlobalConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class MaintenanceTaskConfiguration extends GlobalConfiguration {

    private Map<TaskType,Task> maintenanceTasks;
    private boolean isGitMaintenanceRunning;

    private static final Logger LOGGER = Logger.getLogger(MaintenanceTaskConfiguration.class.getName());

    public MaintenanceTaskConfiguration(){

        LOGGER.log(Level.FINE,"Loading git-maintenance configuration if present on jenkins controller.");
        load();
        if(maintenanceTasks == null) {
            LOGGER.log(Level.FINE,"Git maintenance configuration not present on Jenkins, creating a default configuration");
            configureMaintenanceTasks();
            isGitMaintenanceRunning = false;
        }else{
            LOGGER.log(Level.FINE,"Loaded git maintenance configuration successfully.");
        }
    }
    private void configureMaintenanceTasks(){
        // check git version and based on git version, add the maintenance tasks to the list
        // Can add default cron syntax for maintenance tasks.
        maintenanceTasks = new LinkedHashMap<>();

        maintenanceTasks.put(TaskType.COMMIT_GRAPH,new Task(TaskType.COMMIT_GRAPH));
        maintenanceTasks.put(TaskType.PREFETCH,new Task(TaskType.PREFETCH));
        maintenanceTasks.put(TaskType.GC,new Task(TaskType.GC));
        maintenanceTasks.put(TaskType.LOOSE_OBJECTS,new Task(TaskType.LOOSE_OBJECTS));
        maintenanceTasks.put(TaskType.INCREMENTAL_REPACK,new Task(TaskType.INCREMENTAL_REPACK));
    }

    public List<Task> getMaintenanceTasks(){
        List<Task> maintenanceTasks = new ArrayList<>();
        for(Map.Entry<TaskType,Task> entry : this.maintenanceTasks.entrySet()){
           maintenanceTasks.add(entry.getValue());
        }
        return ImmutableList.copyOf(maintenanceTasks);
    }

    public void setCronSyntax(TaskType taskType, String cronSyntax){
        Task updatedTask = maintenanceTasks.get(taskType);
        updatedTask.setCronSyntax(cronSyntax);
        maintenanceTasks.put(taskType,updatedTask);
        LOGGER.log(Level.FINE,"Assigned " + cronSyntax + " to " + taskType.getTaskName());
    }

    public boolean getIsGitMaintenanceRunning(){
        return isGitMaintenanceRunning;
    }

    public void setIsGitMaintenanceRunning(boolean executionStatus){isGitMaintenanceRunning = executionStatus;}

    public void setIsTaskConfigured(TaskType taskType, boolean isConfigured){
        Task task = maintenanceTasks.get(taskType);
        task.setIsTaskConfigured(isConfigured);
        LOGGER.log(Level.FINE,taskType.getTaskName() + " execution status: " + isConfigured);
    }

    public static String checkSanity(String cron) throws ANTLRException {
       try {
           CronTab cronTab = new CronTab(cron.trim());
           String msg = cronTab.checkSanity();
           return msg;
       }catch(ANTLRException e){
           if(cron.contains("**"))
               throw new ANTLRException("You appear to be missing whitespace between * and *.");
           throw new ANTLRException(String.format("Invalid input: \"%s\": %s", cron, e), e);
       }
    }
}
