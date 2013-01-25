package hudson.plugins.git;

import hudson.*;
import hudson.FilePath.FileCallable;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class GitPublisher extends Recorder implements Serializable, MatrixAggregatable {
    private static final long serialVersionUID = 1L;

    /**
     * Store a config version so we're able to migrate config on various
     * functionality upgrades.
     */
    private Long configVersion;

    private boolean pushMerge;
    private boolean pushOnlyIfSuccess;
    
    private List<TagToPush> tagsToPush;
    // Pushes HEAD to these locations
    private List<BranchToPush> branchesToPush;
    // notes support
    private List<NoteToPush> notesToPush;
    
    @DataBoundConstructor
    public GitPublisher(List<TagToPush> tagsToPush,
                        List<BranchToPush> branchesToPush,
                        List<NoteToPush> notesToPush,
                        boolean pushOnlyIfSuccess,
                        boolean pushMerge) {
        this.tagsToPush = tagsToPush;
        this.branchesToPush = branchesToPush;
        this.notesToPush = notesToPush;
        this.pushMerge = pushMerge;
        this.pushOnlyIfSuccess = pushOnlyIfSuccess;
        this.configVersion = 2L;
    }

    public boolean isPushOnlyIfSuccess() {
        return pushOnlyIfSuccess;
    }
    
    public boolean isPushMerge() {
        return pushMerge;
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
    
    public List<TagToPush> getTagsToPush() {
        if (tagsToPush == null) {
            tagsToPush = new ArrayList<TagToPush>();
        }

        return tagsToPush;
    }

    public List<BranchToPush> getBranchesToPush() {
        if (branchesToPush == null) {
            branchesToPush = new ArrayList<BranchToPush>();
        }

        return branchesToPush;
    }
    
    public List<NoteToPush> getNotesToPush() {
        if (notesToPush == null) {
            notesToPush = new ArrayList<NoteToPush>();
        }

        return notesToPush;
    }
    
    
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    /**
     * For a matrix project, push should only happen once.
     */
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build,launcher,listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                return GitPublisher.this.perform(build,launcher,listener);
            }
        };
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build,
                           Launcher launcher, final BuildListener listener)
        throws InterruptedException {

        // during matrix build, the push back would happen at the very end only once for the whole matrix,
        // not for individual configuration build.
        if (build instanceof MatrixRun) {
            return true;
        }

        SCM scm = build.getProject().getScm();

        if (!(scm instanceof GitSCM)) {
            return false;
        }

        final GitSCM gitSCM = (GitSCM) scm;

        if(gitSCM.getUseShallowClone()) {
        	listener.getLogger().println("GitPublisher disabled while using shallow clone.");
        	return true;
    	}
        
        final String projectName = build.getProject().getName();
        final FilePath workspacePath = build.getWorkspace();
        final int buildNumber = build.getNumber();
        final Result buildResult = build.getResult();

        // If pushOnlyIfSuccess is selected and the build is not a success, don't push.
        if (pushOnlyIfSuccess && buildResult.isWorseThan(Result.SUCCESS)) {
            listener.getLogger().println("Build did not succeed and the project is configured to only push after a successful build, so no pushing will occur.");
            return true;
        }
        else {
            final String gitExe = gitSCM.getGitExe(build.getBuiltOn(), listener);
            EnvVars tempEnvironment;
            try {
                tempEnvironment = build.getEnvironment(listener);
            } catch (IOException e) {
                e.printStackTrace(listener.error("Failed to build up environment"));
                tempEnvironment = new EnvVars();
            }

            String confName = gitSCM.getGitConfigNameToUse();
            if ((confName != null) && (!confName.equals(""))) {
                tempEnvironment.put("GIT_COMMITTER_NAME", confName);
                tempEnvironment.put("GIT_AUTHOR_NAME", confName);
            }
            String confEmail = gitSCM.getGitConfigEmailToUse();
            if ((confEmail != null) && (!confEmail.equals(""))) {
                tempEnvironment.put("GIT_COMMITTER_EMAIL", confEmail);
                tempEnvironment.put("GIT_AUTHOR_EMAIL", confEmail);
            }
            
            final EnvVars environment = tempEnvironment;
            final FilePath workingDirectory = gitSCM.workingDirectory(workspacePath,environment);
            
            boolean pushResult = true;
            // If we're pushing the merge back...
            if (pushMerge) {
                boolean mergeResult;
                try {
                    mergeResult = workingDirectory.act(new FileCallable<Boolean>() {
                            private static final long serialVersionUID = 1L;
                            
                            public Boolean invoke(File workspace,
                                                  VirtualChannel channel) throws IOException {
                                
                                IGitAPI git = new GitAPI(
                                                         gitExe, new FilePath(workspace),
                                                         listener, environment, new String[0]);
                                
                                // We delete the old tag generated by the SCM plugin
                                String buildnumber = "jenkins-" + projectName + "-" + buildNumber;
                                git.deleteTag(buildnumber);
                                
                                // And add the success / fail state into the tag.
                                buildnumber += "-" + buildResult.toString();
                                
                                git.tag(buildnumber, "Jenkins Build #" + buildNumber);
                                
                                PreBuildMergeOptions mergeOptions = gitSCM.getMergeOptions();
                                
                                if (mergeOptions.doMerge() && buildResult.isBetterOrEqualTo(Result.SUCCESS)) {
                                    RemoteConfig remote = mergeOptions.getMergeRemote();
                                    listener.getLogger().println("Pushing HEAD to branch " + mergeOptions.getMergeTarget() + " of " + remote.getName() + " repository");
                                    
                                    git.push(remote, "HEAD:" + mergeOptions.getMergeTarget());
                                } else {
                                    //listener.getLogger().println("Pushing result " + buildnumber + " to origin repository");
                                    //git.push(null);
                                }
                                
                                return true;
                            }
                        });
                } catch (Throwable e) {
                    e.printStackTrace(listener.error("Failed to push merge to origin repository"));
                    build.setResult(Result.FAILURE);
                    mergeResult = false;
                    
                }
                
                if (!mergeResult) {
                    pushResult = false;
                }
            }
            if (isPushTags()) {
                boolean allTagsResult = true;
                for (final TagToPush t : tagsToPush) {
                    boolean tagResult = true;
                    if (t.getTagName() == null) {
                        listener.getLogger().println("No tag to push defined");
                        tagResult = false;
                    }
                    if (t.getTargetRepoName() == null) {
                        listener.getLogger().println("No target repo to push to defined");
                        tagResult = false;
                    }
                    if (tagResult) {
                        final String tagName = environment.expand(t.getTagName());
                        final String tagMessage = hudson.Util.fixNull(environment.expand(t.getTagMessage()));
                        final String targetRepo = environment.expand(t.getTargetRepoName());
                        
                        try {
                            tagResult = workingDirectory.act(new FileCallable<Boolean>() {
                                    private static final long serialVersionUID = 1L;
                                    
                                    public Boolean invoke(File workspace,
                                                          VirtualChannel channel) throws IOException {
                                        
                                        IGitAPI git = new GitAPI(gitExe, new FilePath(workspace),
                                                                 listener, environment, new String[0]);
                                        
                                        RemoteConfig remote = gitSCM.getRepositoryByName(targetRepo);
                                        
                                        if (remote == null) {
                                            listener.getLogger().println("No repository found for target repo name " + targetRepo);
                                            return false;
                                        }
                                        
                                        if (t.isCreateTag() || t.isUpdateTag()) {
                                            if (git.tagExists(tagName) && !t.isUpdateTag()) {
                                                listener.getLogger().println("Tag " + tagName + " already exists and Create Tag is specified, so failing.");
                                                return false;
                                            }

                                            if (tagMessage.isEmpty()) {
                                                git.tag(tagName, "Jenkins Git plugin tagging with " + tagName);
                                            } else {
                                                git.tag(tagName, tagMessage);
                                            }
                                        }
                                        else if (!git.tagExists(tagName)) {
                                            listener.getLogger().println("Tag " + tagName + " does not exist and Create Tag is not specified, so failing.");
                                            return false;
                                        }

                                        listener.getLogger().println("Pushing tag " + tagName + " to repo "
                                                                     + targetRepo);
                                        git.push(remote, tagName);
                                        
                                        return true;
                                    }
                                });
                        } catch (Throwable e) {
                            e.printStackTrace(listener.error("Failed to push tag " + tagName + " to " + targetRepo));
                            build.setResult(Result.FAILURE);
                            tagResult = false;
                        }
                    }
                    
                    if (!tagResult) {
                        allTagsResult = false;
                    }
                }
                if (!allTagsResult) {
                    pushResult = false;
                }
            }
            
            if (isPushBranches()) {
                boolean allBranchesResult = true;
                for (final BranchToPush b : branchesToPush) {
                    boolean branchResult = true;
                    if (b.getBranchName() == null) {
                        listener.getLogger().println("No branch to push defined");
                        return false;
                    }
                    if (b.getTargetRepoName() == null) {
                        listener.getLogger().println("No branch repo to push to defined");
                        return false;
                    }
                    final String branchName = environment.expand(b.getBranchName());
                    final String targetRepo = environment.expand(b.getTargetRepoName());
                    
                    if (branchResult) {
                        try {
                            branchResult = workingDirectory.act(new FileCallable<Boolean>() {
                                    private static final long serialVersionUID = 1L;
                                    
                                    public Boolean invoke(File workspace,
                                                          VirtualChannel channel) throws IOException {
                                        
                                        IGitAPI git = new GitAPI(gitExe, new FilePath(workspace),
                                                                 listener, environment, new String[0]);
                                        
                                        RemoteConfig remote = gitSCM.getRepositoryByName(targetRepo);
                                        
                                        if (remote == null) {
                                            listener.getLogger().println("No repository found for target repo name " + targetRepo);
                                            return false;
                                        }
                                        
                                        listener.getLogger().println("Pushing HEAD to branch " + branchName + " at repo "
                                                                     + targetRepo);
                                        git.push(remote, "HEAD:" + branchName);
                                        
                                        return true;
                                    }
                                });
                        } catch (Throwable e) {
                            e.printStackTrace(listener.error("Failed to push branch " + branchName + " to " + targetRepo));
                            build.setResult(Result.FAILURE);
                            branchResult = false;
                        }
                    }
                    
                    if (!branchResult) {
                        allBranchesResult = false;
                    }
                }
                if (!allBranchesResult) {
                    pushResult = false;
                }
                
            }
                     
            if (isPushNotes()) {
                boolean allNotesResult = true;
                for (final NoteToPush b : notesToPush) {
                    boolean noteResult = true;
                    if (b.getnoteMsg() == null) {
                        listener.getLogger().println("No note to push defined");
                        return false;
                    }
                    
                    b.setEmptyTargetRepoToOrigin();
                    final String noteMsg = environment.expand(b.getnoteMsg());
                    final String noteNamespace = environment.expand(b.getnoteNamespace());
                    final String targetRepo = environment.expand(b.getTargetRepoName());
                    final boolean noteReplace = b.getnoteReplace();
                    
                    if (noteResult) {
                        try {
                            noteResult = workingDirectory.act(new FileCallable<Boolean>() {
                                    private static final long serialVersionUID = 1L;
   
                                    
                                    public Boolean invoke(File workspace,
                                                          VirtualChannel channel) throws IOException {
                                        
                                        IGitAPI git = new GitAPI(gitExe, new FilePath(workspace),
                                                                 listener, environment, new String[0]);

                                        
                                        RemoteConfig remote = gitSCM.getRepositoryByName(targetRepo);

                                        if (remote == null) {
                                            listener.getLogger().println("No repository found for target repo name " + targetRepo);
                                            return false;
                                        }
                                        
                                        listener.getLogger().println("Adding note \"" + noteMsg + "\" to namespace \""+noteNamespace +"\"" );

                                        if ( noteReplace )
                                        	git.addNote(    noteMsg, noteNamespace );
                                        else
                                        	git.appendNote( noteMsg, noteNamespace );
                                        
                                        
                                        git.push(remote, "refs/notes/*" );
                                        
                                        return true;
                                    }
                                });
                        } catch (Throwable e) {
                            e.printStackTrace(listener.error("Failed to add note \"" + noteMsg + "\" to \"" + noteNamespace+"\""));
                            build.setResult(Result.FAILURE);
                            noteResult = false;
                        }
                    }
                    
                    if (!noteResult) {
                        allNotesResult = false;
                    }
                }
                if (!allNotesResult) {
                    pushResult = false;
                }
                
            }
            
            return pushResult;
        }
    }

    /**
     * Handles migration from earlier version - if we were pushing merges, we'll be
     * instantiated but tagsToPush will be null rather than empty.
     * @return This.
     */
    private Object readResolve() {
        // Default unspecified to v0
        if(configVersion == null)
            this.configVersion = 0L;

        if (this.configVersion < 1L) {
            if (tagsToPush == null) {
                this.pushMerge = true;
            }
        }

        return this;
    }
    
    @Extension(ordinal=-1)
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return "Git Publisher";
        }

        @Override
        public String getHelpFile() {
            return "/plugin/git/gitPublisher.html";
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         *
         * I don't think this actually ever gets called, but I'm modernizing it anyway.
         *
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value)
            throws IOException  {
            return FilePath.validateFileMask(project.getSomeWorkspace(),value);
        }

        public FormValidation doCheckTagName(@QueryParameter String value) {
            return checkFieldNotEmpty(value, "Tag Name");
        }

        public FormValidation doCheckBranchName(@QueryParameter String value) {
            return checkFieldNotEmpty(value, "Branch Name");
        }
        
        public FormValidation doCheckNoteMsg(@QueryParameter String value) {
            return checkFieldNotEmpty(value, "Note");
        }
        
        public FormValidation doCheckRemote(
                @AncestorInPath AbstractProject project, StaplerRequest req)
                throws IOException, ServletException {
            String remote = req.getParameter("value");
            boolean isMerge = req.getParameter("isMerge") != null;

            // Added isMerge because we don't want to allow empty remote names
            // for tag/branch pushes.
            if (remote.length() == 0 && isMerge)
                return FormValidation.ok();

            FormValidation validation = checkFieldNotEmpty(remote,
                    "Remote Name");
            if (validation.kind != FormValidation.Kind.OK)
                return validation;

            GitSCM scm = (GitSCM) project.getScm();
            if (scm.getRepositoryByName(remote) == null)
                return FormValidation
                        .error("No remote repository configured with name '"
                                + remote + "'");

            return FormValidation.ok();
        }
                
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        private FormValidation checkFieldNotEmpty(String value, String field) {
            value = StringUtils.strip(value);

            if (value == null || value.equals("")) {
                return FormValidation.error(field + " is required.");
            }
            return FormValidation.ok();
        }
    }

    public static abstract class PushConfig extends AbstractDescribableImpl<PushConfig> implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String targetRepoName;

        public PushConfig(String targetRepoName) {
            this.targetRepoName = Util.fixEmptyAndTrim(targetRepoName);
        }
        
        public String getTargetRepoName() {
            return targetRepoName;
        }

        public void setTargetRepoName() {
            this.targetRepoName = targetRepoName;
        }
        
        public void setEmptyTargetRepoToOrigin(){
            if (targetRepoName == null || targetRepoName.trim().isEmpty() ){
            	targetRepoName = "origin";
            }
        }
    }

    public static final class BranchToPush extends PushConfig {
        private String branchName;

        public String getBranchName() {
            return branchName;
        }

        @DataBoundConstructor
        public BranchToPush(String targetRepoName, String branchName) {
            super(targetRepoName);
            this.branchName = Util.fixEmptyAndTrim(branchName);
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PushConfig> {
            @Override
            public String getDisplayName() {
                return "";
            }
        }
    }

    public static final class TagToPush extends PushConfig {
        private String tagName;
        private String tagMessage;
        private boolean createTag;
        private boolean updateTag;

        public String getTagName() {
            return tagName;
        }

        public String getTagMessage() {
            return tagMessage;
        }

        public boolean isCreateTag() {
            return createTag;
        }

        public boolean isUpdateTag() {
            return updateTag;
        }

        @DataBoundConstructor
        public TagToPush(String targetRepoName, String tagName, String tagMessage, boolean createTag, boolean updateTag) {
            super(targetRepoName);
            this.tagName = Util.fixEmptyAndTrim(tagName);
            this.tagMessage = tagMessage;
            this.createTag = createTag;
            this.updateTag = updateTag;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PushConfig> {
            @Override
            public String getDisplayName() {
                return "";
            }
        }
    }
    

    public static final class NoteToPush extends PushConfig {

        private String noteMsg;
        private String noteNamespace;
        private boolean noteReplace;

        public String getnoteMsg() {
            return noteMsg;
        }
        
        public String getnoteNamespace() {
        	return noteNamespace;
        }
        
        public boolean getnoteReplace() {
        	return noteReplace;
        }

        @DataBoundConstructor
        public NoteToPush( String targetRepoName, String noteMsg, String noteNamespace, boolean noteReplace ) {
        	super(targetRepoName);
            this.noteMsg = Util.fixEmptyAndTrim(noteMsg);
            this.noteReplace = noteReplace;
            
            if ( noteNamespace != null && !noteNamespace.trim().isEmpty() )
    			this.noteNamespace = Util.fixEmptyAndTrim(noteNamespace);
    		else
    			this.noteNamespace = "master";
            
        //    throw new GitException("Toimii2 " + this.noteMsg + "   namespace: "+this.noteNamespace );
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<PushConfig> {
            @Override
            public String getDisplayName() {
                return "";
            }
        }
    }
    
}
