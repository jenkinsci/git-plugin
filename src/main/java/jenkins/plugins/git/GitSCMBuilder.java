package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RefSpec;

/**
 * @author Stephen Connolly
 */
public class GitSCMBuilder<B extends GitSCMBuilder<B>> extends SCMBuilder<B, GitSCM> {

    private final List<GitSCMExtension> extensions = new ArrayList<>();
    private List<String> refSpecs = new ArrayList<>();
    private final List<UserRemoteConfig> remoteConfigs = new ArrayList<>();
    private GitRepositoryBrowser browser;
    private String gitTool;
    private String remoteName = AbstractGitSCMSource.DEFAULT_REMOTE_NAME;
    private String remote;
    private String credentialsId;

    public GitSCMBuilder(@NonNull SCMHead head, SCMRevision revision) {
        super(GitSCM.class, head, revision);
    }

    public final B withExtension(@CheckForNull GitSCMExtension extension) {
        if (extension != null) {
            // the extensions only allow one of each type.
            for (Iterator<GitSCMExtension> iterator = extensions.iterator(); iterator.hasNext(); ) {
                if (extension.getClass().equals(iterator.next().getClass())) {
                    iterator.remove();
                }
            }
            extensions.add(extension);
        }
        return (B) this;
    }

    public final B withExtensions(GitSCMExtension... extensions) {
        for (GitSCMExtension extension : extensions) {
            withExtension(extension);
        }
        return (B) this;
    }

    public final B withExtensions(List<GitSCMExtension> extensions) {
        for (GitSCMExtension extension : extensions) {
            withExtension(extension);
        }
        return (B) this;
    }

    public final List<GitSCMExtension> extensions() {
        return new ArrayList<>(extensions);
    }

    public final B withRemoteConfig(@CheckForNull UserRemoteConfig remoteConfig) {
        if (remoteConfig != null) {
            // the remoteConfigs only allow one of each type.
            for (Iterator<UserRemoteConfig> iterator = remoteConfigs.iterator(); iterator.hasNext(); ) {
                if (remoteConfig.getClass().equals(iterator.next().getClass())) {
                    iterator.remove();
                }
            }
            remoteConfigs.add(remoteConfig);
        }
        return (B) this;
    }

    public final B withRemoteConfigs(UserRemoteConfig... remoteConfigs) {
        for (UserRemoteConfig remoteConfig : remoteConfigs) {
            withRemoteConfig(remoteConfig);
        }
        return (B) this;
    }

    public final B withRemoteConfigs(List<UserRemoteConfig> remoteConfigs) {
        for (UserRemoteConfig remoteConfig : remoteConfigs) {
            withRemoteConfig(remoteConfig);
        }
        return (B) this;
    }

    public final List<UserRemoteConfig> remoteConfigs() {
        return new ArrayList<>(remoteConfigs);
    }

    public final B withRemoteName(String remoteName) {
        this.remoteName = StringUtils.defaultIfBlank(remoteName, AbstractGitSCMSource.DEFAULT_REMOTE_NAME);
        return (B) this;
    }

    public final String remoteName() {
        return remoteName;
    }

    public final B withBrowser(GitRepositoryBrowser browser) {
        this.browser = browser;
        return (B) this;
    }

    public final GitRepositoryBrowser browser() {
        return browser;
    }
    
    public final B withGitTool(String gitTool) {
        this.gitTool = gitTool;
        return (B) this;
    }
    
    public final String gitTool() {
        return gitTool;
    }

    public B withRefSpecs(List<String> refSpecs) {
        this.refSpecs.clear();
        this.refSpecs.addAll(refSpecs);
        return (B) this;
    }

    public B withAdditionalRefSpecs(List<String> refSpecs) {
        this.refSpecs.addAll(refSpecs);
        return (B) this;
    }

    public B withRefSpec(String refSpec) {
        this.refSpecs.add(refSpec);
        return (B) this;
    }

    public List<String> refSpecs() {
        if (refSpecs.isEmpty()) {
            return Collections.singletonList(AbstractGitSCMSource.REF_SPEC_DEFAULT);
        }
        return new ArrayList<>(refSpecs);
    }

    public List<RefSpec> asRefSpecs() {
        List<RefSpec> result = new ArrayList<>(Math.max(refSpecs.size(), 1));
        for (String template: refSpecs()){
            result.add(new RefSpec(
                    template.replaceAll(AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER, remoteName())
            ));
        }
        return result;
    }

    public List<UserRemoteConfig> asRemoteConfigs() {
        List<RefSpec> refSpecs = asRefSpecs();
        List<UserRemoteConfig> result = new ArrayList<>(refSpecs.size());
        String remote = remote();
        for (RefSpec refSpec : refSpecs) {
            result.add(new UserRemoteConfig(remote, remoteName(), refSpec.toString(), credentialsId()));
        }
        return result;

    }

    public B withRemote(String remote) {
        this.remote = remote;
        return (B)this;
    }

    public String remote() {
        return remote;
    }

    public B withCredentials(String credentialsId) {
        this.credentialsId = credentialsId;
        return (B)this;
    }

    public String credentialsId() {
        return credentialsId;
    }

    @NonNull
    @Override
    public GitSCM build() {
        List<GitSCMExtension> extensions = extensions();
        if (revision() instanceof AbstractGitSCMSource.SCMRevisionImpl) {
            // remove any conflicting BuildChooserSetting if present
            for (Iterator<GitSCMExtension> iterator = extensions.iterator(); iterator.hasNext(); ) {
                if (iterator.next() instanceof BuildChooserSetting) {
                    iterator.remove();
                }
            }
            extensions.add(new BuildChooserSetting(new AbstractGitSCMSource.SpecificRevisionBuildChooser(
                    (AbstractGitSCMSource.SCMRevisionImpl) revision())));
        }
        return new GitSCM(
                asRemoteConfigs(),
                Collections.singletonList(new BranchSpec(head().getName())),
                false, Collections.<SubmoduleConfig>emptyList(),
                browser(), gitTool(),
                extensions);
    }
}
