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
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.AbstractOnDiskBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.UnbindableDir;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class GitUsernamePasswordBinding extends MultiBinding<StandardUsernamePasswordCredentials> implements GitCredentialBindings {
    final static private String GIT_USERNAME_KEY = "GIT_USERNAME";
    final static private String GIT_PASSWORD_KEY = "GIT_PASSWORD";
    final private String gitToolName;
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
        final Map<String, String> secretValues = new LinkedHashMap<>();
        final Map<String, String> publicValues = new LinkedHashMap<>();
        StandardUsernamePasswordCredentials credentials = getCredentials(run);
        setCredentialPairBindings(credentials,secretValues,publicValues);
        GitTool cliGitTool = getCliGitTool(run, this.gitToolName, taskListener);
        if (cliGitTool != null && filePath != null) {
            final UnbindableDir unbindTempDir = UnbindableDir.create(filePath);
            setUnixNodeType(isCurrentNodeOSUnix(launcher));
            setGitEnvironmentVariables(getGitClientInstance(cliGitTool.getGitExe(), unbindTempDir.getDirPath(),
                                                            new EnvVars(), taskListener), publicValues);
            GenerateGitScript gitScript = new GenerateGitScript(
                                                  credentials.getUsername(),
                                                  credentials.getPassword().getPlainText(),
                                                  credentials.getId(),
                                                  this.unixNodeType);
            FilePath gitTempFile = gitScript.write(credentials, unbindTempDir.getDirPath());
            secretValues.put("GIT_ASKPASS", gitTempFile.getRemote());
            return new MultiEnvironment(secretValues, publicValues, unbindTempDir.getUnbinder());
        } else {
            taskListener.getLogger().println("JGit and JGitApache type Git tools are not supported by this binding");
            return new MultiEnvironment(secretValues,publicValues);
        }
    }

    @Override
    public Set<String> variables(@NonNull Run<?, ?> build) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(GIT_USERNAME_KEY);
        keys.add(GIT_PASSWORD_KEY);
        return keys;
    }

    @Override
    public void setCredentialPairBindings(@NonNull StandardCredentials credentials, Map<String,String> secretValues, Map<String,String> publicValues) {
        StandardUsernamePasswordCredentials usernamePasswordCredentials = (StandardUsernamePasswordCredentials) credentials;
        if(usernamePasswordCredentials.isUsernameSecret()){
            secretValues.put(GIT_USERNAME_KEY, usernamePasswordCredentials.getUsername());
        }else{
            publicValues.put(GIT_USERNAME_KEY, usernamePasswordCredentials.getUsername());
        }
        secretValues.put(GIT_PASSWORD_KEY, usernamePasswordCredentials.getPassword().getPlainText());
    }

    /*package*/void setGitEnvironmentVariables(@NonNull GitClient git, Map<String,String> publicValues) throws IOException, InterruptedException {
        setGitEnvironmentVariables(git,null,publicValues);
    }

    @Override
    public void setGitEnvironmentVariables(@NonNull GitClient git, Map<String,String> secretValues, Map<String,String> publicValues) throws IOException, InterruptedException {
        if (unixNodeType && ((CliGitAPIImpl) git).isCliGitVerAtLeast(2,3,0,0))
        {
            publicValues.put("GIT_TERMINAL_PROMPT", "false");
        } else {
            publicValues.put("GCM_INTERACTIVE", "false");
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

            FilePath usernameFile = workspace.createTempFile("username", ".txt");
            usernameFile.write(this.userVariable + "\n", null);
            FilePath passwordFile = workspace.createTempFile("password", ".txt");
            passwordFile.write(this.passVariable + "\n", null);

              //Hard Coded platform dependent newLine
            if (this.unixNodeType) {
                gitEcho = workspace.createTempFile("auth", ".sh");
                gitEcho.write("#!/bin/sh\n"
                        + "\n"
                        + "case \"$1\" in\n"
                        + "        Username*) cat " + unixArgEncodeFileName(usernameFile.getRemote()) + ";;\n"
                        + "        Password*) cat " + unixArgEncodeFileName(passwordFile.getRemote()) + ";;\n"
                        + "        esac\n", null);
                gitEcho.chmod(0500);
            } else {
                gitEcho = workspace.createTempFile("auth", ".bat");
                gitEcho.write("@ECHO OFF\r\n"
                        + "SET ARG=%~1\r\n"
                        + "IF %ARG:~0,8%==Username type " + windowsArgEncodeFileName(usernameFile.getRemote()) + "\r\n"
                        + "IF %ARG:~0,8%==Password type " + windowsArgEncodeFileName(passwordFile.getRemote()), null);
            }
            return gitEcho;
        }

        @Override
        protected Class<StandardUsernamePasswordCredentials> type() {
            return StandardUsernamePasswordCredentials.class;
        }

        protected String unixArgEncodeFileName(String filename) {
            if (filename.contains("'")) {
                filename = filename.replace("'", "'\\''");
            }
            return "'" + filename + "'";
        }

        protected String windowsArgEncodeFileName(String filename) {
            if (filename.contains("\"")) {
                filename = filename.replace("\"", "^\"");
            }
            return "\"" + filename + "\"";
        }
    }

    // Mistakenly defined GitUsernamePassword in first release, prefer gitUsernamePassword as symbol
    @Symbol({"gitUsernamePassword", "GitUsernamePassword"})
    @Extension
    public static final class DescriptorImpl extends BindingDescriptor<StandardUsernamePasswordCredentials> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.GitUsernamePasswordBinding_DisplayName();
        }

        @RequirePOST
        public ListBoxModel doFillGitToolNameItems() {
            ListBoxModel items = new ListBoxModel();
             List<GitTool> toolList = Jenkins.get().getDescriptorByType(GitSCM.DescriptorImpl.class).getGitTools();
             for (GitTool t : toolList){
                 if(t.getClass().equals(GitTool.class)){
                     items.add(t.getName());
                 }
             }
             return items;
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
