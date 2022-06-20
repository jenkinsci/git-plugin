package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import hudson.scheduler.CronTab;
import hudson.scheduler.CronTabList;

import java.util.Calendar;
import java.util.Collections;

public class Task {

    private TaskType task;
    private String cronSyntax;
    private boolean isConfigured;

    private CronTabList cronTabList;

    public Task(TaskType task){
        // Can add default cron syntax recommended by git documentation
        this.task = task;
    }

    public Task(Task copyTask) throws ANTLRException {
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

    public void setCronSyntax(String cronSyntax) throws ANTLRException {
        this.cronSyntax = cronSyntax;
        setCronTabList(cronSyntax);
    }

    private void setCronTabList(String cronSyntax) throws ANTLRException {
        cronTabList = new CronTabList(Collections.singletonList(new CronTab(cronSyntax)));
    }

    public boolean checkIsTaskExecutable(Calendar cal){
        return cronTabList.check(cal);
    }
}
