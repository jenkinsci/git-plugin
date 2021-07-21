package jenkins.plugins.git;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.credentialsbinding.BindingDescriptor;
import org.jenkinsci.plugins.credentialsbinding.MultiBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.AbstractOnDiskBinding;
import org.jenkinsci.plugins.credentialsbinding.impl.UnbindableDir;
import org.jenkinsci.plugins.gitclient.CliGitAPIImpl;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class GitSSHPrivateKeyBinding extends MultiBinding<SSHUserPrivateKey> implements GitCredentialBindings, SSHKeyUtils {
    final static private String PRIVATE_KEY = "PRIVATE_KEY";
    final static private String PASSPHRASE = "PASSPHRASE";
    final private String gitToolName;
    private transient boolean unixNodeType;

    @DataBoundConstructor
    public GitSSHPrivateKeyBinding(String gitToolName, String credentialsId) {
        super(credentialsId);
        this.gitToolName = gitToolName;
        //Variables could be added if needed
    }

    public String getGitToolName() {
        return this.gitToolName;
    }

    private void setUnixNodeType(boolean value) {
        this.unixNodeType = value;
    }

    @Override
    public MultiEnvironment bind(@NonNull Run<?, ?> run, @Nullable FilePath filePath,
                                 @Nullable Launcher launcher, @NonNull TaskListener taskListener) throws IOException, InterruptedException {
        final Map<String, String> secretValues = new LinkedHashMap<>();
        final Map<String, String> publicValues = new LinkedHashMap<>();
        SSHUserPrivateKey credentials = getCredentials(run);
        setCredentialPairBindings(credentials, secretValues, publicValues);
        GitTool cliGitTool = getCliGitTool(run, this.gitToolName, taskListener);
        if (cliGitTool != null && filePath != null && launcher != null) {
            final UnbindableDir unbindTempDir = UnbindableDir.create(filePath);
            setUnixNodeType(isCurrentNodeOSUnix(launcher));
            setGitEnvironmentVariables(getGitClientInstance(cliGitTool.getGitExe(), unbindTempDir.getDirPath(),
                    new EnvVars(), taskListener), publicValues);

        } else {

        }
    }

    @Override
    public Set<String> variables(@NonNull Run<?, ?> run) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(PRIVATE_KEY);
        keys.add(PASSPHRASE);
        return keys;
    }

    @Override
    public void setCredentialPairBindings(@NonNull StandardCredentials credentials, Map<String, String> secretValues, Map<String, String> publicValues) {
        SSHUserPrivateKey sshUserPrivateKey = (SSHUserPrivateKey) credentials;
        if (sshUserPrivateKey.isUsernameSecret()) {
            secretValues.put(PRIVATE_KEY, sshUserPrivateKey.getUsername());
        } else {
            publicValues.put(PRIVATE_KEY, sshUserPrivateKey.getUsername());
        }
        secretValues.put(PASSPHRASE, Secret.toString(((SSHUserPrivateKey) credentials).getPassphrase()));
    }

    /*package*/void setGitEnvironmentVariables(@NonNull GitClient git, Map<String,String> publicValues) throws IOException, InterruptedException {
        setGitEnvironmentVariables(git,null,publicValues);
    }

    @Override
    public void setGitEnvironmentVariables(@NonNull GitClient git, Map<String, String> secretValues, Map<String, String> publicValues) throws IOException, InterruptedException {
        if (unixNodeType && isGitVersionAtLeast(git, 2, 3, 0, 0)) {
            publicValues.put("GIT_TERMINAL_PROMPT", "false");
        } else {
            publicValues.put("GCM_INTERACTIVE", "false");
        }
    }

    @Override
    public GitClient getGitClientInstance(String gitToolExe, FilePath repository, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        Git gitInstance = Git.with(listener, env).using(gitToolExe);
        return gitInstance.getClient();
    }

    @Override
    protected Class<SSHUserPrivateKey> type() {
        return SSHUserPrivateKey.class;
    }

    private boolean isGitVersionAtLeast(GitClient git, int major, int minor, int rev, int bugfix) {
        return ((CliGitAPIImpl) git).isCliGitVerAtLeast(major, minor, rev, bugfix);
    }

    protected static final class GenerateSSHScript extends AbstractOnDiskBinding<SSHUserPrivateKey> {

    protected final class SSHScriptFile extends AbstractOnDiskBinding<SSHUserPrivateKey> {

        private final String sshExePath;

        protected GenerateSSHScript(SSHUserPrivateKey credentials,String sshExePath) {
            super(SSHKeyUtils.getPrivateKey(credentials)+":"+SSHKeyUtils.getPassphrase(credentials), credentials.getId());
            this.privateKeyVariable = SSHKeyUtils.getPrivateKey(credentials);
            this.passphraseVariable = SSHKeyUtils.getPassphrase(credentials);
            this.sshExePath = sshExePath;
        }

        @Override
        protected FilePath write(SSHUserPrivateKey credentials, FilePath workspace) throws IOException, InterruptedException {
            FilePath tempFile;
            if (unixNodeType) {
                tempFile = workspace.createTempFile("gitSSHScript", ".sh");
                tempFile.write(
                        "ssh -i "
                                + getPrivateKeyFile(credentials, workspace).getRemote()
                                + " -o StrictHostKeyChecking=no $@", null);
                tempFile.chmod(0500);
            } else {
                tempFile = workspace.createTempFile("gitSSHScript", ".bat");
                tempFile.write("@echo off\r\n"
                        + "\""
                        + this.sshExePath
                        + "\""
                        + " -i "
                        + "\""
                        + getPrivateKeyFile(credentials, workspace).getRemote()
                        + "\""
                        + " -o StrictHostKeyChecking=no", null);
            }
            return tempFile;
        }

        @Override
        protected Class<SSHUserPrivateKey> type() {
            return SSHUserPrivateKey.class;
        }
    }

    @Symbol("GitSSHPrivateKey")
    @Extension
    public static final class DescriptorImpl extends BindingDescriptor<SSHUserPrivateKey> {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.GitSSHPrivateKeyBinding_DisplayName();
        }

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
        protected Class<SSHUserPrivateKey> type() {
            return SSHUserPrivateKey.class;
        }

        @Override
        public boolean requiresWorkspace() {
            return true;
        }
    }
}
