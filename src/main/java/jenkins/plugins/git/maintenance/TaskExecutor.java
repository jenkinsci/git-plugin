package jenkins.plugins.git.maintenance;

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

    public TaskExecutor(Task maintenanceTask){
        this.maintenanceTask = maintenanceTask;
        cachesDir = getCachesDir();
    }

    @Override
    public void run() {
        // Execute Maintenance Tasks in this class.

        // TODO
        // Need to get the Git Client from the Git-Client-Plugin.
        // Need to add locks while running maintenance tasks on caches and remove locks after the maintenance tasks.

        GitClient gitClient;
        for(File file : cachesDir){
            try {
                gitClient = getGitClient(file);

                // Need to add git maintenance command in git client plugin

            } catch (IOException e) {

                throw new RuntimeException(e);
            } catch (InterruptedException e) {

                throw new RuntimeException(e);
            }
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
