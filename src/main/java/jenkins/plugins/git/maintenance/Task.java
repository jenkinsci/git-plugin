package jenkins.plugins.git.maintenance;

public class Task {

    TaskType task;
    String cronSyntax;
    boolean isConfigured;

    public Task(TaskType task){
        // Can add default cron syntax recommended by git documentation
        this.task = task;
    }

    public TaskType getTaskType(){
        return this.task;
    }

    public String getTaskName(){
        return this.task.getTaskName();
    }

    public String getCronSyntax(){ return this.cronSyntax; }

    public void setCronSyntax(String cronSyntax){
        this.cronSyntax = cronSyntax;
    }
}
