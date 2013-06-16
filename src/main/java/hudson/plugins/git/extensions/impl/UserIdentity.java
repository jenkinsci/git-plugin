package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;

import static hudson.Util.*;

/**
 * {@link GitSCMExtension} that sets a different name and/or e-mail address for commits.
 *
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class UserIdentity extends GitSCMExtension {
    private String name;
    private String email;

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

    @Override
    public void populateEnvironmentVariables(GitSCM scm, Map<String, String> env) {
        if (name!=null) {
            env.put("GIT_COMMITTER_NAME", name);
            env.put("GIT_AUTHOR_NAME", name);
        }
        if (email!=null) {
            env.put("GIT_COMMITTER_EMAIL", email);
            env.put("GIT_AUTHOR_EMAIL", email);
        }
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Custom user name/e-mail address";
        }
    }
}

