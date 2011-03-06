package hudson.plugins.git;

import hudson.plugins.git.util.GitUtils;
import org.apache.commons.lang.StringUtils;
import org.spearce.jgit.lib.RepositoryConfig;
import org.spearce.jgit.transport.RemoteConfig;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * Author: Tom Huybrechts
 */
public class Repository implements Serializable {

    private final String url;
    private final String repoName;
    private final String refSpec;

    @DataBoundConstructor
    public Repository(String url, String repoName, String refSpec) {
        this.url = url;
        this.repoName = repoName;
        this.refSpec = refSpec;
    }

    public String getUrl() {
        return url;
    }

    public String getRepoName() {
        return repoName;
    }

    public String getRefSpec() {
        return refSpec;
    }

    public RemoteConfig createRemoteConfig() throws IOException {
        File temp = File.createTempFile("tmp", "config");
        try {
            List<org.spearce.jgit.transport.RemoteConfig> remoteRepositories;
            RepositoryConfig repoConfig = new RepositoryConfig(null, temp);
            // Make up a repo config from the request parameters

            String repoName = this.repoName.replace(' ', '_');

            String refSpec = this.refSpec;
            if(StringUtils.isEmpty(refSpec)) {
                        refSpec = "+refs/heads/*:refs/remotes/" + repoName + "/*";
            }

            repoConfig.setString("remote", repoName, "url", url);
            repoConfig.setString("remote", repoName, "fetch", refSpec);

            try {
                repoConfig.save();
                remoteRepositories = org.spearce.jgit.transport.RemoteConfig.getAllRemoteConfigs(repoConfig);
            }
            catch (Exception e) {
                throw new GitException("Error creating repositories", e);
            }
            return remoteRepositories.get(0);
        } finally {
            temp.delete();
        }

    }
}
