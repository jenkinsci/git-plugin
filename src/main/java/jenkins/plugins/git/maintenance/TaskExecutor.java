package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.util.GitUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.File;
import java.io.IOException;

public class TaskExecutor implements Runnable {

    Task maintenanceTask;
    File[] cachesDir;

    public TaskExecutor(Task maintenanceTask) throws ANTLRException {
        this.maintenanceTask = new Task(maintenanceTask);
        cachesDir = getCachesDir();
    }

    @Override
    public void run() {
        // Execute Maintenance Tasks in this class.

        // TODO
        // Need to add locks while running maintenance tasks on caches and remove locks after the maintenance tasks.

        GitClient gitClient;
        for(File file : cachesDir){
            try {
                System.out.println("entered the thread");
                gitClient = getGitClient(file);
                TaskType taskType = maintenanceTask.getTaskType();
                executeMaintenanceTask(gitClient,taskType);

            } catch (IOException e) {

                throw new RuntimeException(e);
            } catch (InterruptedException e) {

                throw new RuntimeException(e);
            }
        }

    }

    void executeMaintenanceTask(GitClient gitClient,TaskType taskType){

        if(taskType.equals(TaskType.GC)){
            gitClient.maintenance("gc");
        }else if(taskType.equals(TaskType.COMMIT_GRAPH)){
            gitClient.maintenance("commit-graph");
        }else if(taskType.equals(TaskType.PREFETCH)){
            gitClient.maintenance("prefetch");
        }else if(taskType.equals(TaskType.INCREMENTAL_REPACK)){
            gitClient.maintenance("incremental-repack");
        }else if(taskType.equals(TaskType.LOOSE_OBJECTS)){
            gitClient.maintenance("loose-objects");
        }else{
            // Error
        }

    }

    File[] getCachesDir(){
        return GitMaintenanceSCM.getCachesDirectory();
    }

    GitClient getGitClient(File file) throws IOException, InterruptedException {
        // What exactly is default tool here?
        TaskListener listener = TaskListener.NULL;
        GitTool gitTool = GitUtils.resolveGitTool("Default", listener);
        if(gitTool == null)
            return null;

        String gitExe = gitTool.getGitExe();
        FilePath workspace = new FilePath(file);
        Git git = Git.with(listener,null).in(workspace).using(gitExe);

        return git.getClient();
    }
}
