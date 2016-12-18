/*
 * The MIT License
 *
 * Copyright 2016.
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
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitPublisher;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.scm.SCM;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.PushCommand;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Runs Git Publishing steps using {@link GitSCM}.
 */
public final class GitPublisherStep extends AbstractStepImpl {

  private final String url;
  private String branch = "master";
  private String credentialsId;

  private boolean forcePush;

  private List<GitPublisher.TagToPush> tagsToPush;
  // Pushes HEAD to these locations
  private List<GitPublisher.BranchToPush> branchesToPush;
  // notes support
  private List<GitPublisher.NoteToPush> notesToPush;

  @DataBoundConstructor
  public GitPublisherStep(String url) {
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
  @Restricted(NoExternalUse.class)
  public void setBranch(String branch) {
    this.branch = branch;
  }

  @DataBoundSetter
  @Restricted(NoExternalUse.class)
  public void setCredentialsId(String credentialsId) {
    this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
  }

  @DataBoundSetter
  @Restricted(NoExternalUse.class)
  public void setTagsToPush(List<GitPublisher.TagToPush> tagsToPush) {
    this.tagsToPush = tagsToPush;
  }

  @DataBoundSetter
  @Restricted(NoExternalUse.class)
  public void setBranchesToPush(List<GitPublisher.BranchToPush> branchesToPush) {
    this.branchesToPush = branchesToPush;
  }

  @DataBoundSetter
  @Restricted(NoExternalUse.class)
  public void setNotesToPush(List<GitPublisher.NoteToPush> notesToPush) {
    this.notesToPush = notesToPush;
  }

  public SCM createSCM() {
    return new GitSCM(GitSCM.createRepoList(url, credentialsId), Collections.singletonList(new BranchSpec("*/" + branch)), false, Collections.<SubmoduleConfig>emptyList(), null,
      null, Collections.<GitSCMExtension>singletonList(new LocalBranch(branch)));
  }

  public boolean isForcePush() {
    return forcePush;
  }

  public boolean isPushTags() {
    if (tagsToPush == null) {
      return false;
    }
    return !tagsToPush.isEmpty();
  }

  public boolean isPushBranches() {
    if (branchesToPush == null) {
      return false;
    }
    return !branchesToPush.isEmpty();
  }

  public boolean isPushNotes() {
    if (notesToPush == null) {
      return false;
    }
    return !notesToPush.isEmpty();
  }

  public List<GitPublisher.TagToPush> getTagsToPush() {
    if (tagsToPush == null) {
      tagsToPush = new ArrayList<>();
    }

    return Collections.unmodifiableList(tagsToPush);
  }

  public List<GitPublisher.BranchToPush> getBranchesToPush() {
    if (branchesToPush == null) {
      branchesToPush = new ArrayList<>();
    }

    return Collections.unmodifiableList(branchesToPush);
  }

  public List<GitPublisher.NoteToPush> getNotesToPush() {
    if (notesToPush == null) {
      notesToPush = new ArrayList<>();
    }

    return Collections.unmodifiableList(notesToPush);
  }


  public static final class StepExecutionImpl extends AbstractSynchronousNonBlockingStepExecution<Void> {

    @javax.inject.Inject
    private transient GitPublisherStep step;
    @StepContextParameter
    private transient Run<?, ?> run;
    @StepContextParameter
    private transient FilePath workspace;
    @StepContextParameter
    private transient TaskListener listener;
    @StepContextParameter
    private transient Launcher launcher;

    @Override
    protected Void run() throws Exception {
      step.publish(run, workspace, listener, launcher);
      return null;
    }

    private static final long serialVersionUID = 1L;
  }

  public final void publish(Run<?, ?> run, FilePath workspace, TaskListener listener, Launcher launcher) throws Exception {
    final GitSCM gitSCM = (GitSCM) createSCM();

    EnvVars environment = run.getEnvironment(listener);

    final GitClient git = gitSCM.createClient(listener, environment, run, workspace);

    URIish remoteURI;

    if (isPushTags()) {
      for (final GitPublisher.TagToPush t : tagsToPush) {
        if (t.getTagName() == null) {
          throw new AbortException("No tag to push defined");
        }

        if (t.getTargetRepoName() == null) {
          throw new AbortException("No target repo to push to defined");
        }

        final String tagName = environment.expand(t.getTagName());
        final String tagMessage = hudson.Util.fixNull(environment.expand(t.getTagMessage()));
        final String targetRepo = environment.expand(t.getTargetRepoName());

        try {
          // Lookup repository with unexpanded name as GitSCM stores them unexpanded
          RemoteConfig remote = gitSCM.getRepositoryByName(t.getTargetRepoName());

          if (remote == null) {
            throw new AbortException("No repository found for target repo name " + targetRepo);
          }

          // expand environment variables in remote repository
          remote = gitSCM.getParamExpandedRepo(environment, remote);

          boolean tagExists = git.tagExists(tagName.replace(' ', '_'));
          if (t.isCreateTag() || t.isUpdateTag()) {
            if (tagExists && !t.isUpdateTag()) {
              throw new AbortException("Tag " + tagName + " already exists and Create Tag is specified, so failing.");
            }

            if (StringUtils.isBlank(tagMessage)) {
              git.tag(tagName, "Jenkins Git plugin tagging with " + tagName);
            } else {
              git.tag(tagName, tagMessage);
            }
          }
          else if (!tagExists) {
            throw new AbortException("Tag " + tagName + " does not exist and Create Tag is not specified, so failing.");
          }

          listener.getLogger().println("Pushing tag " + tagName + " to repo "
            + targetRepo);

          remoteURI = remote.getURIs().get(0);
          PushCommand push = git.push().to(remoteURI).ref(tagName);
          if (forcePush) {
            push.force();
          }
          push.execute();
        } catch (GitException e) {
          e.printStackTrace(listener.error("Failed to push tag " + tagName + " to " + targetRepo));
        }
      }
    }

    if (isPushBranches()) {
      for (final GitPublisher.BranchToPush b : branchesToPush) {
        if (b.getBranchName() == null)
          throw new AbortException("No branch to push defined");

        if (b.getTargetRepoName() == null)
          throw new AbortException("No branch repo to push to defined");

        final String branchName = environment.expand(b.getBranchName());
        final String targetRepo = environment.expand(b.getTargetRepoName());

        try {
          // Lookup repository with unexpanded name as GitSCM stores them unexpanded
          RemoteConfig remote = gitSCM.getRepositoryByName(b.getTargetRepoName());

          if (remote == null)
            throw new AbortException("No repository found for target repo name " + targetRepo);

          // expand environment variables in remote repository
          remote = gitSCM.getParamExpandedRepo(environment, remote);

          listener.getLogger().println("Pushing HEAD to branch " + branchName + " at repo "
            + targetRepo);
          remoteURI = remote.getURIs().get(0);
          PushCommand push = git.push().to(remoteURI).ref("HEAD:" + branchName);
          if (forcePush) {
            push.force();
          }
          push.execute();
        } catch (GitException e) {
          e.printStackTrace(listener.error("Failed to push branch " + branchName + " to " + targetRepo));
        }
      }
    }

    if (isPushNotes()) {
      for (final GitPublisher.NoteToPush b : notesToPush) {
        if (b.getnoteMsg() == null)
          throw new AbortException("No note to push defined");

        b.setEmptyTargetRepoToOrigin();
        String noteMsgTmp = environment.expand(b.getnoteMsg());
        final String noteMsg = replaceAdditionalEnvironmentalVariables(noteMsgTmp, run);
        final String noteNamespace = environment.expand(b.getnoteNamespace());
        final String targetRepo = environment.expand(b.getTargetRepoName());
        final boolean noteReplace = b.getnoteReplace();

        try {
          // Lookup repository with unexpanded name as GitSCM stores them unexpanded
          RemoteConfig remote = gitSCM.getRepositoryByName(b.getTargetRepoName());

          if (remote == null) {
            listener.getLogger().println("No repository found for target repo name " + targetRepo);
          }

          // expand environment variables in remote repository
          remote = gitSCM.getParamExpandedRepo(environment, remote);

          listener.getLogger().println("Adding note to namespace \""+noteNamespace +"\":\n" + noteMsg + "\n******" );

          if ( noteReplace )
            git.addNote(    noteMsg, noteNamespace );
          else
            git.appendNote( noteMsg, noteNamespace );

          remoteURI = remote.getURIs().get(0);
          PushCommand push = git.push().to(remoteURI).ref("refs/notes/*");
          if (forcePush) {
            push.force();
          }
          push.execute();
        } catch (GitException e) {
          e.printStackTrace(listener.error("Failed to add note: \n" + noteMsg  + "\n******"));
        }
      }
    }
  }

  private String replaceAdditionalEnvironmentalVariables(String input, Run<?, ?> run){
    if (run == null){
      return input;
    }
    String buildResult = "";
    Result result = run.getResult();
    if (result != null) {
      buildResult = result.toString();
    }
    String buildDuration = run.getDurationString().replaceAll("and counting", "");

    input = input.replaceAll("\\$BUILDRESULT", buildResult);
    input = input.replaceAll("\\$BUILDDURATION", buildDuration);
    return input;
  }

  @Extension
  public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

    public DescriptorImpl() {
      super(StepExecutionImpl.class);
    }

    @Inject
    private UserRemoteConfig.DescriptorImpl delegate;

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project,
      @QueryParameter String url,
      @QueryParameter String credentialsId) {
      return delegate.doFillCredentialsIdItems(project, url, credentialsId);
    }

    @Override
    public String getFunctionName() {
      return "gitPublisher";
    }

    @Override
    public String getDisplayName() {
      return Messages.GitPublisherStep_DisplayName();
    }
  }
}
