package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;

public class Task {

    private TaskType task;
    private String cronSyntax;
    private boolean isConfigured;

    public Task(TaskType task){
        // Can add default cron syntax recommended by git documentation
        this.task = task;
    }

    public Task(Task copyTask){
        // Used for copying the task;
        this(copyTask.getTaskType());
        setCronSyntax(copyTask.getCronSyntax());
        setIsTaskConfigured(copyTask.getIsTaskConfigured());
    }

    public TaskType getTaskType(){
        return this.task;
    }

    public String getTaskName(){
        return this.task.getTaskName();
    }

    public String getCronSyntax(){ return this.cronSyntax; }

    public void setIsTaskConfigured(boolean isConfigured){
        this.isConfigured = isConfigured;
    }

    public boolean getIsTaskConfigured(){
        return this.isConfigured;
    }

    public void setCronSyntax(String cronSyntax){
        this.cronSyntax = cronSyntax;
    }
}
