package jenkins.plugins.git.maintenance;

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
import java.util.logging.Level;
import java.util.logging.Logger;

public class TaskExecutor implements Runnable {

    Task maintenanceTask;
    List<GitMaintenanceSCM.Cache> caches;

    private static final Logger LOGGER = Logger.getLogger(TaskExecutor.class.getName());

    public TaskExecutor(Task maintenanceTask){
        this.maintenanceTask = new Task(maintenanceTask);
        caches = getCaches();
    }

    @Override
    public void run() {

        LOGGER.log(Level.FINE,"Running maintenance task " + maintenanceTask.getTaskName() + " on git caches.");
        GitClient gitClient;
        for(GitMaintenanceSCM.Cache cache : caches){

            // For now adding lock to all kinds of maintenance tasks. Need to study on which task needs a lock and which doesn't.
            Lock lock = cache.getLock();
            File cacheFile = cache.getCacheFile();
            try {
                gitClient = getGitClient(cacheFile);

                if(gitClient == null) {
                    LOGGER.log(Level.WARNING,"No GitTool found while running " + maintenanceTask.getTaskName());
                    return;
                }

                TaskType taskType = maintenanceTask.getTaskType();

                lock.lock();
                LOGGER.log(Level.FINE,"Cache " + cacheFile.getName() + " locked.");

                executeMaintenanceTask(gitClient,taskType);

            } catch (IOException | InterruptedException e) {
                // What is this error???
                LOGGER.log(Level.WARNING,"Git Client couldn't be initialized.");
            }finally {
                lock.unlock();
                LOGGER.log(Level.FINE,"Cache " + cacheFile.getName() + " unlocked.");
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
            LOGGER.log(Level.WARNING,"Invalid maintenance task.");
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
