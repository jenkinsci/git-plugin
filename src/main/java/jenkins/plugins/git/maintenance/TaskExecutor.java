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
import java.util.List;
import java.util.concurrent.locks.Lock;

public class TaskExecutor implements Runnable {

    Task maintenanceTask;
    List<GitMaintenanceSCM.Cache> caches;

    public TaskExecutor(Task maintenanceTask) throws ANTLRException {
        this.maintenanceTask = new Task(maintenanceTask);
        caches = getCaches();
    }

    @Override
    public void run() {

        System.out.println("Entered the Thread");
        // Execute Maintenance Tasks in this class.

        // TODO

        GitClient gitClient;
        for(GitMaintenanceSCM.Cache cache : caches){

            // For now adding lock to all kinds of maintenance tasks. Need to study on which task needs a lock and which doesn't.
            Lock lock = cache.getLock();
            try {
                File cacheFile = cache.getCacheFile();
                System.out.println("Executing maintenance task for " + cacheFile.getName());
                gitClient = getGitClient(cacheFile);
                TaskType taskType = maintenanceTask.getTaskType();

                lock.lock();
                System.out.println("Locked the cache");

                executeMaintenanceTask(gitClient,taskType);

            } catch (IOException | InterruptedException e) {
                System.out.println("Need to handle error");
            }finally {
                lock.unlock();
                System.out.println("Unlocked the cache");
            }
        }

    }

    void executeMaintenanceTask(GitClient gitClient,TaskType taskType) throws InterruptedException{

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

    List<GitMaintenanceSCM.Cache> getCaches(){
        return GitMaintenanceSCM.getCaches();
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
