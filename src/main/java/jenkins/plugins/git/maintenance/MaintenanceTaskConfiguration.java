package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.scheduler.CronTab;
import hudson.util.StreamTaskListener;
import jenkins.model.GlobalConfiguration;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * MaintenanceTaskConfiguration is responsible for creating tasks,fetching tasks, storing the configuration in Jenkins. It extends the
 * <b>{@link GlobalConfiguration}</b> class.
 * @since TODO
 *
 * @author Hrushikesh Rao
 */
@Extension
public class MaintenanceTaskConfiguration extends GlobalConfiguration {

    /**
     * It is the core data structure which stores all maintenance tasks.
     * A map ensure the efficient data access. The {@link TaskType} is key and {@link Task} is value.
     *
     */
    private Map<TaskType,Task> maintenanceTasks;
    private boolean isGitMaintenanceRunning;

    private static final Logger LOGGER = Logger.getLogger(MaintenanceTaskConfiguration.class.getName());

    /**
     * The constructor checks if a <i>maintenanceTaskConfiguration.xml</i> file is present on Jenkins.
     * This xml file contains maintenance data configured by the administrator.
     * If file is present, the data is loaded from the file. The file would be missing if administrators never configured maintenance task on Jenkins.
     * If file is missing a data structure is created by calling {@link MaintenanceTaskConfiguration#configureMaintenanceTasks()}.
     *
     */
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

    /**
     *  Initializes a data structure if configured for first time. The data structure is used to store and fetch maintenance configuration.
     */
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

    /**
     * Gets a copy of list of Maintenance Tasks.
     *
     * @return List of {@link Task}.
     */
    public List<Task> getMaintenanceTasks(){
        List<Task> maintenanceTasks = new ArrayList<>();
        for(Map.Entry<TaskType,Task> entry : this.maintenanceTasks.entrySet()){
           maintenanceTasks.add(entry.getValue());
        }
        return ImmutableList.copyOf(maintenanceTasks);
    }

    /**
     * Set the cron Syntax of a maintenance task.
     *
     * @param taskType type of maintenance task.
     * @param cronSyntax cron syntax corresponding to task.
     */
    public void setCronSyntax(TaskType taskType, String cronSyntax){
        Task updatedTask = maintenanceTasks.get(taskType);
        updatedTask.setCronSyntax(cronSyntax);
        maintenanceTasks.put(taskType,updatedTask);
        LOGGER.log(Level.FINE,"Assigned " + cronSyntax + " to " + taskType.getTaskName());
    }

    /**
     * Returns the status of git maintenance i.e. is it configured or not.
     *
     * @return A boolean if git maintenance is configured globally.
     */
    public boolean getIsGitMaintenanceRunning(){
        return isGitMaintenanceRunning;
    }

    /**
     * Set the execution status of git maintenance globally. If false, the git maintenance is not executed on any cache.
     *
     * @param executionStatus a boolean to set the global git maintenance.
     */
    public void setIsGitMaintenanceRunning(boolean executionStatus){isGitMaintenanceRunning = executionStatus;}

    /**
     * Maintenance task state can be changed by toggling the isConfigured boolean variable present in {@link Task} class.
     * If isConfigured is true, the maintenance task is executed when corresponding cronSyntax is valid else task is not executed at all.
     *
     * @param taskType The type of maintenance task.
     * @param isConfigured The state of execution of maintenance Task.
     */
    public void setIsTaskConfigured(TaskType taskType, boolean isConfigured){
        Task task = maintenanceTasks.get(taskType);
        task.setIsTaskConfigured(isConfigured);
        LOGGER.log(Level.FINE,taskType.getTaskName() + " execution status: " + isConfigured);
    }

    /**
     * Validates the input cron syntax.
     *
     * @param cron Cron syntax as String.
     * @return Empty string if no error, else a msg describing error in cron syntax.
     * @throws ANTLRException during incorrect cron input.
     */
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

    /**
     * Returns the git version used for maintenance.
     *
     * @return the git version used for maintenance.
     */
    static List<Integer> getGitVersion(){

        final TaskListener procListener = StreamTaskListener.fromStderr();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new Launcher.LocalLauncher(procListener).launch().cmds("git", "--version").stdout(out).join();
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.WARNING, "Exception checking git version " + ex);
        }
        String versionOutput = "";
        try {
            versionOutput = out.toString(StandardCharsets.UTF_8.toString()).trim();
        } catch (UnsupportedEncodingException ue) {
            LOGGER.log(Level.WARNING, "Unsupported encoding checking git version", ue);
        }
        final String[] fields = versionOutput.split(" ")[2].replaceAll("msysgit.", "").replaceAll("windows.", "").split("\\.");

        // Eg: [2, 31, 4]
        // 0th index is Major Version.
        // 1st index is Minor Version.
        // 2nd index is Patch Version.
        return Arrays.stream(fields).map(Integer::parseInt).collect(Collectors.toList());
    }
}
