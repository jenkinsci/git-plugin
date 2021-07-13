package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.util.GitUtils;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;

public interface GitCredentialBindings {

    void setKeyBindings(@NonNull StandardCredentials credentials);

    void setRunEnvironmentVariables(@NonNull FilePath filePath, @NonNull TaskListener listener) throws IOException, InterruptedException;

    GitClient getGitClientInstance(String gitExe, FilePath repository,
                                   EnvVars env, TaskListener listener) throws IOException, InterruptedException;

    default boolean isCurrentNodeOSUnix(@NonNull Launcher launcher){
        return launcher.isUnix();
    }

    default GitTool getCliGitTool(Run<?, ?> run, String gitToolName,
                              TaskListener listener) throws IOException, InterruptedException {

        Executor buildExecutor = run.getExecutor();
        if (buildExecutor != null) {
            Node currentNode = buildExecutor.getOwner().getNode();
            //Check node is not null
            if (currentNode != null) {
                GitTool nameSpecificGitTool = GitUtils.resolveGitTool(gitToolName,currentNode,new EnvVars(),listener);
                if(nameSpecificGitTool != null) {
                    GitTool typeSpecificGitTool = nameSpecificGitTool.getDescriptor().getInstallation(gitToolName);
                    //Check if tool is of type GitTool
                    if (typeSpecificGitTool != null && typeSpecificGitTool.getClass().equals(GitTool.class)) {
                        return nameSpecificGitTool;
                    }
                }
            }
        }
        return null;
    }
}