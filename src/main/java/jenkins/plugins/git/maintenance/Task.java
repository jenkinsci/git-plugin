package jenkins.plugins.git.maintenance;

/**
 * POJO to store configuration for maintenance tasks.
 * @since TODO
 *
 * @author Hrushikesh Rao
 */
public class Task {

    private TaskType task;
    private String cronSyntax;
    private boolean isConfigured;

    /**
     * Initialize a maintenance task object.
     *
     * @param task {@link TaskType}.
     */
    public Task(TaskType task){
        // Can add default cron syntax recommended by git documentation
        this.task = task;
    }

    /**
     * A convenience constructor that copies the Task object.
     *
     * @param copyTask task object.
     *
     */
    public Task(Task copyTask){
        // Used for copying the task;
        this(copyTask.getTaskType());
        setCronSyntax(copyTask.getCronSyntax());
        setIsTaskConfigured(copyTask.getIsTaskConfigured());
    }

    /**
     * Gets the TaskType enum.
     *
     * @return TaskType {@link TaskType}.
     */
    public TaskType getTaskType(){
        return this.task;
    }

    /**
     * Gets the name of the maintenance task.
     *
     * @return maintenance task name.
     */
    public String getTaskName(){
        return this.task.getTaskName();
    }

    /**
     *
     * @return cron syntax configured for a maintenance task.
     */
    public String getCronSyntax(){ return this.cronSyntax; }

    /**
     *  Toggle the state of execution of maintenance task.
     *
     * @param isConfigured if true, corresponding task is configured and executed using cron syntax.
     */
    public void setIsTaskConfigured(boolean isConfigured){
        this.isConfigured = isConfigured;
    }

    /**
     *
     * @return A boolean to check maintenance task is configured by administrator.
     */
    public boolean getIsTaskConfigured(){
        return this.isConfigured;
    }

    /**
     * Configure the Cron Syntax
     *
     * @param cronSyntax cron syntax as String
     *
     */
    public void setCronSyntax(String cronSyntax){
        this.cronSyntax = cronSyntax;
    }
}
