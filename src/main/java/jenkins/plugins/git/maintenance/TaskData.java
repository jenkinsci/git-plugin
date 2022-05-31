package jenkins.plugins.git.maintenance;

public class TaskData {

    String task;
    String msg;

    public TaskData(String task, String msg){
        this.task = task;
        this.msg = msg;
    }

    public String getTaskName(){
        return this.task;
    }

    public String getTaskMessage(){
        return this.msg;
    }

}
