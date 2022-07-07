package jenkins.plugins.git.maintenance;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.util.GitUtils;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
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
        LOGGER.log(Level.FINE,"New Thread created to execute " + maintenanceTask.getTaskName());
    }

    @Override
    public void run() {

        LOGGER.log(Level.FINE,"Executing maintenance task " + maintenanceTask.getTaskName() + " on git caches.");
        GitClient gitClient;
        for(GitMaintenanceSCM.Cache cache : caches){

            // For now adding lock to all kinds of maintenance tasks. Need to study on which task needs a lock and which doesn't.
            Lock lock = cache.getLock();
            File cacheFile = cache.getCacheFile();
            try {
                gitClient = getGitClient(cacheFile);
                if(gitClient == null)
                    return;

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

        if(gitVersionAtLeast(2,30,0)){
            executeGitMaintenance(gitClient,taskType);
        }else{
            executeLegacyGitMaintenance(gitClient,taskType);
        }
    }

    void executeGitMaintenance(GitClient gitClient,TaskType taskType) throws InterruptedException {

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
    void executeLegacyGitMaintenance(GitClient gitClient,TaskType taskType){

    }

    private boolean gitVersionAtLeast(int neededMajor, int neededMinor, int neededPatch){
        return MaintenanceTaskConfiguration.gitVersionAtLeast(neededMajor,neededMinor,neededPatch);
    }

    List<GitMaintenanceSCM.Cache> getCaches(){
        List<GitMaintenanceSCM.Cache> caches =  GitMaintenanceSCM.getCaches();
        LOGGER.log(Level.FINE,"Fetched all caches present on Jenkins Controller.");
        return caches;
    }

    GitClient getGitClient(File file) throws IOException, InterruptedException {
        // What exactly is default tool here?
        TaskListener listener = new LogTaskListener(LOGGER,Level.FINE);
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        // How to get Jenkins controller as the node?
        GitTool gitTool = GitUtils.resolveGitTool(null,jenkins,null, listener);
        if(gitTool == null) {
            LOGGER.log(Level.WARNING,"No GitTool found while running " + maintenanceTask.getTaskName());
            return null;
        }

        String gitExe = gitTool.getGitExe();
        FilePath workspace = new FilePath(file);
        Git git = Git.with(listener,null).in(workspace).using(gitExe);

        GitClient gitClient = git.getClient();
        if(gitClient instanceof CliGitAPIImpl)
            return gitClient;

        LOGGER.log(Level.WARNING,"Cli Git is not being used to execute maintenance tasks");
        return null;
    }
}
