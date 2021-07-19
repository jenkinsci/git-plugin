package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.util.GitUtils;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface GitCredentialBindings {

    /**
     * Sets secret or public pair value(s)
     * @param credentials The credentials {@link com.cloudbees.plugins.credentials.common.StandardCredentials}. Cannot be null
     * @param secretValues The values{@link java.util.Map} to be hidden in build logs
     * @param publicValues The values{@link java.util.Map} to be visible in build logs
     **/
    void setCredentialPairBindings(@NonNull StandardCredentials credentials, Map<String,String> secretValues, Map<String,String> publicValues);

    /**
     * Set Git specific environment variable
     * @param git GitClient {@link org.jenkinsci.plugins.gitclient.GitClient}. Cannot be null.
     * @param secretValues The values{@link java.util.Map} to be hidden in build logs
     * @param publicValues The values{@link java.util.Map} to be visible in build logs
     **/
    void setGitEnvironmentVariables(@NonNull GitClient git, Map<String,String> secretValues, Map<String,String> publicValues) throws IOException, InterruptedException;

    /**
     * Performed operations on a git repository. Using Git implementations JGit/JGit Apache/Cli Git
     * @param gitExe The path {@link java.lang.String} to git executable {@link org.jenkinsci.plugins.gitclient.Git#using(String)}
     * @param repository The path {@link java.lang.String} to working directory {@link org.jenkinsci.plugins.gitclient.Git#in(File)}
     * @param env The environment values {@link hudson.EnvVars}
     * @param listener The task listener.
     * @return a GitClient implementation {@link org.jenkinsci.plugins.gitclient.GitClient}
     **/
    GitClient getGitClientInstance(String gitExe, FilePath repository,
                                   EnvVars env, TaskListener listener) throws IOException, InterruptedException;
    /**
     * Checks the OS environment of the node/controller
     * @param launcher The launcher.Cannot be null
     * @return false if current node/controller is not running in windows environment
     **/
    default boolean isCurrentNodeOSUnix(@NonNull Launcher launcher){
        return launcher.isUnix();
    }

    /**
     * Ensures that the gitTool available is of type cli git/GitTool.class {@link hudson.plugins.git.GitTool}.
     * @param run The build {@link hudson.model.Run}. Cannot be null
     * @param gitToolName The name of the git tool {@link java.lang.String}
     * @param listener The task listener. Cannot be null.
     * @return A git tool of type GitTool.class {@link hudson.plugins.git.GitTool} or null
     **/
    default GitTool getCliGitTool(Run<?, ?> run, String gitToolName,
                                  TaskListener listener) throws IOException, InterruptedException {

        Executor buildExecutor = run.getExecutor();
        if (buildExecutor != null) {
            Node currentNode = buildExecutor.getOwner().getNode();
            //Check node is not null
            if (currentNode != null) {
                GitTool nameSpecificGitTool = GitUtils.resolveGitTool(gitToolName,currentNode,new EnvVars(),listener);
                if(nameSpecificGitTool != null){
                    GitTool typeSpecificGitTool = nameSpecificGitTool.getDescriptor().getInstallation(nameSpecificGitTool.getName());
                    if(typeSpecificGitTool != null) {
                        boolean check = typeSpecificGitTool.getClass().equals(GitTool.class);
                        if (check) {
                            return nameSpecificGitTool;
                        }
                    }
                }
            }
        }
        return null;
    }
}
