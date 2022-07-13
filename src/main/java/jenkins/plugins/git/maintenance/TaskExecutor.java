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
    private List<GitMaintenanceSCM.Cache> caches;

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
        TaskType taskType = maintenanceTask.getTaskType();
        for(GitMaintenanceSCM.Cache cache : caches){
            // For now adding lock to all kinds of maintenance tasks. Need to study on which task needs a lock and which doesn't.
            Lock lock = cache.getLock();
            File cacheFile = cache.getCacheFile();
            try {
                gitClient = getGitClient(cacheFile);
                if(gitClient == null)
                    return;

                lock.lock();
                LOGGER.log(Level.FINE,"Cache " + cacheFile.getName() + " locked.");
                executeMaintenanceTask(gitClient,taskType);
            } catch (InterruptedException e) {
                LOGGER.log(Level.FINE,"Couldn't run " + taskType.getTaskName() + ".Msg: " + e.getMessage());
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
        // checking git version for every cache. Reason is because in the UI, the git version can be changed anytime.
        LOGGER.log(Level.FINE,"Git version >= 2.30.0 detected. Using official git maintenance command.");
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
    void executeLegacyGitMaintenance(GitClient gitClient,TaskType taskType) throws InterruptedException{
        LOGGER.log(Level.FINE,"Git version < 2.30.0 detected. Using legacy git maintenance commands");

        // If git version < 2.18.0 =====> run only gc.
        // If git version >= 2.18.0 && git version <=2.19.1 ======> run gc and commit-graph
        // If git version >= 2.20.0 && git version < 2.30 ========> run gc , commit-graph && multi-pack-index

        if(gitVersionAtLeast(2,20,0)){
            // execute gc, commit-graph && multi-pack-index
            if(taskType.equals(TaskType.GC)){
                gitClient.maintenanceLegacy("gc");
            }else if(taskType.equals(TaskType.INCREMENTAL_REPACK)){
                gitClient.maintenanceLegacy("incremental-repack");
            }else if(taskType.equals(TaskType.COMMIT_GRAPH)){
                gitClient.maintenanceLegacy("commit-graph");
            }else{
                LOGGER.log(Level.FINE,"Cannot execute " + taskType.getTaskName() + " maintenance task due to older git version");
            }
        }else if(gitVersionAtLeast(2,18,0)){
            // execute gc && commit-graph
            if(taskType.equals(TaskType.GC))
                gitClient.maintenanceLegacy("gc");
            else if(taskType.equals(TaskType.COMMIT_GRAPH))
                gitClient.maintenanceLegacy("commit-graph");
            else
                LOGGER.log(Level.FINE,"Cannot execute " + taskType.getTaskName() + " maintenance task due to older git version");
        }else {
            // These are git versions less than 2.18.0
            // execute gc only
            if(taskType.equals(TaskType.GC))
                gitClient.maintenanceLegacy("gc");
            else
                LOGGER.log(Level.FINE,"Cannot execute " + taskType.getTaskName() + " maintenance task due to older git version");
        }
    }

    boolean gitVersionAtLeast(int neededMajor, int neededMinor, int neededPatch){
        return MaintenanceTaskConfiguration.gitVersionAtLeast(neededMajor,neededMinor,neededPatch);
    }

    List<GitMaintenanceSCM.Cache> getCaches(){
        List<GitMaintenanceSCM.Cache> caches =  GitMaintenanceSCM.getCaches();
        LOGGER.log(Level.FINE,"Fetched all caches present on Jenkins Controller.");
        return caches;
    }

    GitClient getGitClient(File file){
        try {
            TaskListener listener = new LogTaskListener(LOGGER, Level.FINE);
            final Jenkins jenkins = Jenkins.getInstanceOrNull();
            // How to get Jenkins controller as the node?
            GitTool gitTool = GitUtils.resolveGitTool(null, jenkins, null, listener);
            if (gitTool == null) {
                LOGGER.log(Level.WARNING, "No GitTool found while running " + maintenanceTask.getTaskName());
                return null;
            }

            String gitExe = gitTool.getGitExe();
            if (file != null) {
                FilePath workspace = new FilePath(file);
                Git git = Git.with(listener, null).in(workspace).using(gitExe);

                GitClient gitClient = git.getClient();
                if (gitClient instanceof CliGitAPIImpl)
                    return gitClient;
                LOGGER.log(Level.WARNING, "JGit requested, but does not execute maintenance tasks");
            } else {
                LOGGER.log(Level.WARNING, "Cli Git will not execute maintenance tasks due to null file arg");
            }

        }catch (InterruptedException | IOException e ){
            LOGGER.log(Level.WARNING,"Git Client couldn't be initialized.");
        }
        return null;
    }
}
