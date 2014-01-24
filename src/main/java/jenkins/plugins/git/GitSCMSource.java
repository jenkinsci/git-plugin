/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc., Stephen Connolly.
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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.plugins.git.GitStatus;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSourceOwners;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Stephen Connolly
 */
public class GitSCMSource extends AbstractGitSCMSource {
    private static final String DEFAULT_INCLUDES = "*";

    private static final String DEFAULT_EXCLUDES = "";

    public static final Logger LOGGER = Logger.getLogger(GitSCMSource.class.getName());

    private final String remote;

    private final String credentialsId;

    private final String includes;

    private final String excludes;

    private final boolean ignoreOnPushNotifications;

    @DataBoundConstructor
    public GitSCMSource(String id, String remote, String credentialsId, String includes, String excludes, boolean ignoreOnPushNotifications) {
        super(id);
        this.remote = remote;
        this.credentialsId = credentialsId;
        this.includes = includes;
        this.excludes = excludes;
        this.ignoreOnPushNotifications = ignoreOnPushNotifications;
    }

    public boolean isIgnoreOnPushNotifications() {
      return ignoreOnPushNotifications;
    }

    @Override
    public String getCredentialsId() {
        return credentialsId;
    }

    public String getRemote() {
        return remote;
    }

    @Override
    public String getIncludes() {
        return includes;
    }

    @Override
    public String getExcludes() {
        return excludes;
    }

    @Override
    protected List<RefSpec> getRefSpecs() {
        return Arrays.asList(new RefSpec("+refs/heads/*:refs/remotes/" + getRemoteName() + "/*"));
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.GitSCMSource_DisplayName();
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath SCMSourceOwner context,
                                                     @QueryParameter String remote) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.withEmptySelection();
            result.withMatching(GitClient.CREDENTIALS_MATCHER,
                    CredentialsProvider.lookupCredentials(
                            StandardUsernameCredentials.class,
                            context,
                            ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(remote).build()
                    )
            );
            return result;
        }


    }

    @Extension
    public static class ListenerImpl extends GitStatus.Listener {

        @Override
        public List<GitStatus.ResponseContributor> onNotifyCommit(URIish uri, String... branches) {
            List<GitStatus.ResponseContributor> result = new ArrayList<GitStatus.ResponseContributor>();
            boolean notified = false;
            // run in high privilege to see all the projects anonymous users don't see.
            // this is safe because when we actually schedule a build, it's a build that can
            // happen at some random time anyway.
            Authentication old = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
            try {
                for (final SCMSourceOwner owner : SCMSourceOwners.all()) {
                    for (SCMSource source : owner.getSCMSources()) {
                        if (source instanceof GitSCMSource) {
                            GitSCMSource git = (GitSCMSource) source;
                            if (git.ignoreOnPushNotifications) {
                              continue;
                            }
                            URIish remote;
                            try {
                                remote = new URIish(git.getRemote());
                            } catch (URISyntaxException e) {
                                // ignore
                                continue;
                            }
                            if (GitStatus.looselyMatches(uri, remote)) {
                                LOGGER.info("Triggering the indexing of " + owner.getFullDisplayName());
                                owner.onSCMSourceUpdated(source);
                                result.add(new GitStatus.ResponseContributor() {
                                    @Override
                                    public void addHeaders(StaplerRequest req, StaplerResponse rsp) {
                                        rsp.addHeader("Triggered", owner.getAbsoluteUrl());
                                    }

                                    @Override
                                    public void writeBody(PrintWriter w) {
                                        w.println("Scheduled indexing of " + owner.getFullDisplayName());
                                    }
                                });
                                notified = true;
                            }
                        }
                    }
                }
            } finally {
                SecurityContextHolder.getContext().setAuthentication(old);
            }
            if (!notified) {
                result.add(new GitStatus.MessageResponseContributor("No git consumers for URI " + uri.toString()));
            }
            return result;
        }
    }
}
