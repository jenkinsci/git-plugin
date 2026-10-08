/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.git;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Allows plugins defining credentials used for Git checkouts to behave specially according to contextual information.
 * Compared to {@link Credentials#forRun} this permits a richer Git-specific context to be accessed.
 */
@Restricted(Beta.class)
public interface GitCredentialContextualizer extends ExtensionPoint {

    /**
     * Optionally customizes a credential according to its specific usage.
     * Used by various entry points such as {@link GitSCM#createClient(TaskListener, EnvVars, Run, FilePath)}.
     * For example, an app credential might create a clone which offers tokens scoped to just one repository.
     * @param credential something matching {@link GitClient#CREDENTIALS_MATCHER}
     * @param build a build attempting to access a Git repository for any of a variety of reasons
     * @param url the Git repository URL, as in {@link UserRemoteConfig#getUrl}
     * @return a specialized credential object, or null to do nothing special
     */
    @CheckForNull StandardUsernameCredentials contextualize(@NonNull StandardUsernameCredentials credential, @NonNull Run<?, ?> build, @NonNull String url);

}
