/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import com.google.inject.Inject;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Job;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.Collections;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Runs Git using {@link GitSCM}.
 */
public final class GitStep extends SCMStep {

    private final String url;
    private String branch = "master";
    private String credentialsId;

    @DataBoundConstructor
    public GitStep(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public String getBranch() {
        return branch;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setBranch(String branch) {
        this.branch = branch;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    @Override
    public SCM createSCM() {
        return new GitSCM(GitSCM.createRepoList(url, credentialsId), Collections.singletonList(new BranchSpec("*/" + branch)), false, Collections.<SubmoduleConfig>emptyList(), null, null, Collections.<GitSCMExtension>singletonList(new LocalBranch(branch)));
    }

    @Extension
    public static final class DescriptorImpl extends SCMStepDescriptor {

        @Inject
        private UserRemoteConfig.DescriptorImpl delegate;

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Job<?,?> project,
                                                     @QueryParameter String url,
                                                     @QueryParameter String credentialsId) {
            return delegate.doFillCredentialsIdItems(project, url, credentialsId);
        }

        public FormValidation doCheckUrl(@AncestorInPath Item item,
                                         @QueryParameter String credentialsId,
                                         @QueryParameter String value) throws IOException, InterruptedException {
            return delegate.doCheckUrl(item, credentialsId, value);
        }

        @Override
        public String getFunctionName() {
            return "git";
        }

        @Override
        public String getDisplayName() {
            return Messages.GitStep_git();
        }

    }

}
