package jenkins.plugins.git.maintenance;

public class Task {

    TaskType task;
    String msg;
    String cronSyntax;
    boolean isConfigured;

    public Task(TaskType task, String msg){
        this.task = task;
        this.msg = msg;
    }

    public TaskType getTaskName(){
        return this.task;
    }

    public String getTaskMessage(){
        return this.msg;
    }

    public String getCronSyntax(){ return this.cronSyntax; }

    public void setCronSyntax(String cronSyntax){
        this.cronSyntax = cronSyntax;
    }
}
