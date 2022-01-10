package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import static hudson.Util.fixEmptyAndTrim;

/**
 * {@link GitSCMExtension} that sets a different name and/or e-mail address for commits.
 *
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class UserIdentity extends GitSCMExtension {
    private final String name;
    private final String email;

    @DataBoundConstructor
    public UserIdentity(String name, String email) {
        this.name = fixEmptyAndTrim(name);
        this.email = fixEmptyAndTrim(email);
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void populateEnvironmentVariables(GitSCM scm, Map<String, String> env) {
        // for backward compatibility, in case the user's shell script invokes Git inside
        if (name!=null) {
            env.put("GIT_COMMITTER_NAME", name);
            env.put("GIT_AUTHOR_NAME", name);
        }
        if (email!=null) {
            env.put("GIT_COMMITTER_EMAIL", email);
            env.put("GIT_AUTHOR_EMAIL", email);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UserIdentity that = (UserIdentity) o;

        return Objects.equals(name, that.name)
                && Objects.equals(email, that.email);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, email);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "UserIdentity{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GitClient decorate(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
        GitSCM.DescriptorImpl d = scm.getDescriptor();

        String n = d.getGlobalConfigName();
        if (name!=null) n = name;

        String e = d.getGlobalConfigEmail();
        if (email!=null) e = email;

        git.setAuthor(n,e);
        git.setCommitter(n,e);

        return git;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Custom user name/e-mail address";
        }
    }
}
