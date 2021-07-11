package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.AbstractOnDiskBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.UnbindableDir;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;


public class GitUsernamePasswordBinding extends MultiBinding<StandardUsernamePasswordCredentials> implements GitCredentialBindings {
    final static private String GIT_USERNAME_KEY = "GIT_USERNAME";
    final static private String GIT_PASSWORD_KEY = "GIT_PASSWORD";
    final private String gitToolName;
    final private Map<String, String> credMap = new LinkedHashMap<>();
    private GitTool cliGitTool = null;
    private transient boolean unixNodeType;

    @DataBoundConstructor
    public GitUsernamePasswordBinding(String gitToolName, String credentialsId) {
        super(credentialsId);
        this.gitToolName = gitToolName;
        //Variables could be added if needed
    }

    public String getGitToolName(){
        return this.gitToolName;
    }

    private void setUnixNodeType(boolean value) {
        this.unixNodeType = value;
    }

    @Override
    protected Class<StandardUsernamePasswordCredentials> type() {
        return StandardUsernamePasswordCredentials.class;
    }

    @Override
    public MultiEnvironment bind(@NonNull Run<?, ?> run, FilePath filePath,
                                 Launcher launcher, @NonNull TaskListener taskListener)
            throws IOException, InterruptedException {
        StandardUsernamePasswordCredentials credentials = getCredentials(run);
        setKeyBindings(credentials);
        cliGitTool = getCliGitTool(run, this.gitToolName, taskListener);
        if (cliGitTool != null && filePath != null) {
            final UnbindableDir unbindTempDir = UnbindableDir.create(filePath);
            setRunEnvironmentVariables(filePath, taskListener);
            GenerateGitScript gitScript = new GenerateGitScript(
                                                  credentials.getUsername(),
                                                  credentials.getPassword().getPlainText(),
                                                  credentials.getId(),
                                                  this.unixNodeType);
            FilePath gitTempFile = gitScript.write(credentials, unbindTempDir.getDirPath());
            credMap.put("GIT_ASKPASS", gitTempFile.getRemote());
            return new MultiEnvironment(credMap, unbindTempDir.getUnbinder());
        } else {
            taskListener.getLogger().println("JGit and JGitApache type Git tools are not supported by this binding");
            return new MultiEnvironment(credMap);
        }
    }

    @Override
    public Set<String> variables(@Nonnull Run<?, ?> build) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(GIT_USERNAME_KEY);
        keys.add(GIT_PASSWORD_KEY);
        return keys;
    }

    @Override
    public void setKeyBindings(@NonNull StandardCredentials credentials) {
        credMap.put(GIT_USERNAME_KEY, ((StandardUsernamePasswordCredentials) credentials).getUsername());
        credMap.put(GIT_PASSWORD_KEY, ((StandardUsernamePasswordCredentials) credentials).getPassword().getPlainText());
    }

    @Override
    public void setRunEnvironmentVariables(@NonNull FilePath filePath, @NonNull TaskListener listener) throws IOException, InterruptedException {
        if (unixNodeType && ((CliGitAPIImpl) getGitClientInstance(cliGitTool.getGitExe(),null,
                                                 new EnvVars(), listener)).
                                                 isCliGitVerAtLeast(2,3,0,0))
        {
            credMap.put("GIT_TERMINAL_PROMPT", "false");
        } else {
            credMap.put("GCM_INTERACTIVE", "false");
        }
    }

    @Override
    public GitClient getGitClientInstance(String gitToolExe, FilePath repository,
                                          EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        Git gitInstance = Git.with(listener, env).using(gitToolExe);
        return gitInstance.getClient();
    }

    protected static final class GenerateGitScript extends AbstractOnDiskBinding<StandardUsernamePasswordCredentials> {

        private final String userVariable;
        private final String passVariable;
        private final boolean unixNodeType;

        protected GenerateGitScript(String gitUsername, String gitPassword,
                                       String credentialId, boolean unixNodeType) {
            super(gitUsername + ":" + gitPassword, credentialId);
            this.userVariable = gitUsername;
            this.passVariable = gitPassword;
            this.unixNodeType = unixNodeType;
        }

        @Override
        protected FilePath write(StandardUsernamePasswordCredentials credentials, FilePath workspace)
                throws IOException, InterruptedException {
            FilePath gitEcho;
              //Hard Coded platform dependent newLine
            if (this.unixNodeType) {
                gitEcho = workspace.createTempFile("auth", ".sh");
                // [#!/usr/bin/evn sh] to be used if required, could have some corner cases
                gitEcho.write("case $1 in\n"
                        + "        Username*) echo " + this.userVariable
                        + "                ;;\n"
                        + "        Password*) echo " + this.passVariable
                        + "                ;;\n"
                        + "        esac\n", null);
                gitEcho.chmod(0500);
            } else {
                gitEcho = workspace.createTempFile("auth", ".bat");
                gitEcho.write("@ECHO OFF\r\n"
                        + "SET ARG=%~1\r\n"
                        + "IF %ARG:~0,8%==Username (ECHO " + this.userVariable + ")\r\n"
                        + "IF %ARG:~0,8%==Password (ECHO " + this.passVariable + ")", null);
            }
            return gitEcho;
        }

        @Override
        protected Class<StandardUsernamePasswordCredentials> type() {
            return StandardUsernamePasswordCredentials.class;
        }
    }

    @Symbol("GitUsernamePassword")
    @Extension
    public static final class DescriptorImpl extends BindingDescriptor<StandardUsernamePasswordCredentials> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.GitUsernamePasswordBinding_DisplayName();
        }

        @Override
        protected Class<StandardUsernamePasswordCredentials> type() {
            return StandardUsernamePasswordCredentials.class;
        }

        @Override
        public boolean requiresWorkspace() {
            return true;
        }
    }
}
