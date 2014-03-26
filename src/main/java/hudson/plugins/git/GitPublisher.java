package hudson.plugins.git;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Result;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

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
    
    private String replaceAdditionalEnvironmentalVariables(String input, AbstractBuild<?, ?> build){
    	if (build == null){
    		return input;
    	}
        String buildResult = build.getResult().toString();
        String buildDuration = build.getDurationString();

        if ( buildResult == null){ 
        	buildResult = ""; 
        }
        if ( buildDuration == null){ 
        	buildDuration = ""; 
        }
        else{
        	buildDuration = buildDuration.replaceAll("and counting", "");
        }
        
        input = input.replaceAll("\\$BUILDRESULT", buildResult);
        input = input.replaceAll("\\$BUILDDURATION", buildDuration);
        return input;
    }
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build,
                           Launcher launcher, final BuildListener listener)
            throws InterruptedException, IOException {

        // during matrix build, the push back would happen at the very end only once for the whole matrix,
        // not for individual configuration build.
        if (build instanceof MatrixRun) {
            return true;
        }

        SCM scm = build.getProject().getScm();
        List<GitSCM> scmlist = new ArrayList<GitSCM>();

        try {
            boolean multiSCMInstalled = false;
            try{
                final Class<?> multiSCMClass = Class.forName("org.jenkinsci.plugins.multiplescms.MultiSCM");
                multiSCMInstalled = true;
            }catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if(multiSCMInstalled){
                final Class<?> multiSCMClass = Class.forName("org.jenkinsci.plugins.multiplescms.MultiSCM");
                final Method configuredSCM = multiSCMClass.getMethod("getConfiguredSCMs", null);

                List<SCM> scmlist_tmp = (List<SCM>) configuredSCM.invoke(build.getProject().getScm(), null);

                for (SCM s : scmlist_tmp) {
                    if (s instanceof GitSCM) {
                        for (TagToPush t : tagsToPush) {
                            if (t.getTagName() == null)
                                throw new AbortException("No tag to push defined");

                            if (t.getTargetRepoName() == null)
                                throw new AbortException("No target repo to push to defined");
                            EnvVars environment = build.getEnvironment(listener);

                            String targetRepo = environment.expand(t.getTargetRepoName());
                            if (targetRepo.equals(((GitSCM) s).getScmName())) {
                                scmlist.add((GitSCM) s);
                            }
                        }
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        // If this is not a MultiSCM, add the GitSCM and continue
        if (scmlist.isEmpty()){
            scmlist.add((GitSCM) scm);
        }

        for (GitSCM gitSCM : scmlist) {

            if(gitSCM.getUseShallowClone()) {
                listener.getLogger().println("GitPublisher disabled while using shallow clone.");
                return true;
            }

            final String projectName = build.getProject().getName();
            final int buildNumber = build.getNumber();
            final Result buildResult = build.getResult();

            // If pushOnlyIfSuccess is selected and the build is not a success, don't push.
            if (pushOnlyIfSuccess && buildResult.isWorseThan(Result.SUCCESS)) {
                listener.getLogger().println("Build did not succeed and the project is configured to only push after a successful build, so no pushing will occur.");
                return true;
            }
            else {
                EnvVars environment = build.getEnvironment(listener);

                GitClient git  = gitSCM.createClient(listener,environment,build);

                // If we're pushing the merge back...
                if (pushMerge) {
                    try {
                        if (!gitSCM.getSkipTag()) {
                            // We delete the old tag generated by the SCM plugin
                            String buildnumber = "jenkins-" + projectName.replace(" ", "_") + "-" + buildNumber;
                            if (git.tagExists(buildnumber))
                                git.deleteTag(buildnumber);

                            // And add the success / fail state into the tag.
                            buildnumber += "-" + buildResult.toString();

                            git.tag(buildnumber, "Jenkins Build #" + buildNumber);
                        }

                        PreBuildMergeOptions mergeOptions = gitSCM.getMergeOptions();

                        String mergeTarget = environment.expand(mergeOptions.getMergeTarget());

                        if (mergeOptions.doMerge() && buildResult.isBetterOrEqualTo(Result.SUCCESS)) {
                            RemoteConfig remote = mergeOptions.getMergeRemote();
                            listener.getLogger().println("Pushing HEAD to branch " + mergeTarget + " of " + remote.getName() + " repository");

                            git.push(remote.getName(), "HEAD:" + mergeTarget);
                        } else {
                            //listener.getLogger().println("Pushing result " + buildnumber + " to origin repository");
                            //git.push(null);
                        }
                    } catch (FormException e) {
                        e.printStackTrace(listener.error("Failed to push merge to origin repository"));
                        return false;
                    } catch (GitException e) {
                        e.printStackTrace(listener.error("Failed to push merge to origin repository"));
                        return false;
                    }
                }

                if (isPushTags()) {
                    for (TagToPush t : tagsToPush) {
                        if (t.getTagName() == null)
                            throw new AbortException("No tag to push defined");

                        if (t.getTargetRepoName() == null)
                            throw new AbortException("No target repo to push to defined");

                        String tagName = environment.expand(t.getTagName());
                        String tagMessage = hudson.Util.fixNull(environment.expand(t.getTagMessage()));
                        String targetRepo = environment.expand(t.getTargetRepoName());

                        try {
                            RemoteConfig remote = null;
                            if (gitSCM.getRepositories().get(0).getName().equals(gitSCM.getScmName())){
                                remote = gitSCM.getRepositoryByName(gitSCM.getScmName());   
                            }

                            if (remote == null){
                                remote = gitSCM.getRepositoryByName(targetRepo);
                            }
                            if (remote == null)
                                throw new AbortException("No repository found for target repo name " + targetRepo);

                            if(!t.getTargetRepoName().equals(remote.getName())){
                                continue;
                            }

                            boolean tagExists = git.tagExists(tagName.replace(' ', '_'));
                            if (t.isCreateTag() || t.isUpdateTag()) {
                                if (tagExists && !t.isUpdateTag()) {
                                    throw new AbortException("Tag " + tagName + " already exists and Create Tag is specified, so failing.");
                                }

                                if (tagMessage.length()==0) {
                                    git.tag(tagName, "Jenkins Git plugin tagging with " + tagName);
                                } else {
                                    git.tag(tagName, tagMessage);
                                }
                            }
                            else if (!tagExists) {
                                throw new AbortException("Tag " + tagName + " does not exist and Create Tag is not specified, so failing.");
                            }

                            if(remote.getName().equals(gitSCM.getScmName())){
                                listener.getLogger().println("Pushing tag " + tagName + " to repo "
                                        + targetRepo);
                                git.push(remote.getName(), tagName);
                            }
                        } catch (GitException e) {
                            e.printStackTrace(listener.error("Failed to push tag " + tagName + " to " + targetRepo));
                            return false;
                        }
                    }
                }

                if (isPushBranches()) {
                    for (BranchToPush b : branchesToPush) {
                        if (b.getBranchName() == null)
                            throw new AbortException("No branch to push defined");

                        if (b.getTargetRepoName() == null)
                            throw new AbortException("No branch repo to push to defined");

                        String branchName = environment.expand(b.getBranchName());
                        String targetRepo = environment.expand(b.getTargetRepoName());

                        try {
                            RemoteConfig remote = null;
                            if (gitSCM.getRepositories().get(0).getName().equals(gitSCM.getScmName())){
                                remote = gitSCM.getRepositoryByName(gitSCM.getScmName());   
                            }

                            if (remote == null){
                                remote = gitSCM.getRepositoryByName(targetRepo);
                            }
                            if (remote == null)
                                throw new AbortException("No repository found for target repo name " + targetRepo);

                            if(!b.getTargetRepoName().equals(remote.getName())){
                                continue;
                            }

                            if(remote.getName().equals(gitSCM.getScmName())){
                                listener.getLogger().println("Pushing HEAD to branch " + branchName + " at repo "
                                        + targetRepo);
                                git.push(remote.getName(), "HEAD:" + branchName);
                            }
                        } catch (GitException e) {
                            e.printStackTrace(listener.error("Failed to push branch " + branchName + " to " + targetRepo));
                            return false;
                        }
                    }
                }

                if (isPushNotes()) {
                    for (NoteToPush b : notesToPush) {
                        if (b.getnoteMsg() == null)
                            throw new AbortException("No note to push defined");

                        b.setEmptyTargetRepoToOrigin();
                        String noteMsgTmp = environment.expand(b.getnoteMsg());
                        String noteMsg = replaceAdditionalEnvironmentalVariables(noteMsgTmp, build);
                        String noteNamespace = environment.expand(b.getnoteNamespace());
                        String targetRepo = environment.expand(b.getTargetRepoName());
                        boolean noteReplace = b.getnoteReplace();

                        try {
                            RemoteConfig remote = null;
                            if (gitSCM.getRepositories().get(0).getName().equals(gitSCM.getScmName())){
                                remote = gitSCM.getRepositoryByName(gitSCM.getScmName());   
                            }

                            if (remote == null){
                                remote = gitSCM.getRepositoryByName(targetRepo);
                            }

                            if (remote == null) {
                                listener.getLogger().println("No repository found for target repo name " + targetRepo);
                                return false;
                            }

                            if(!b.getTargetRepoName().equals(remote.getName())){
                                continue;
                            }

                            listener.getLogger().println("Adding note to namespace \""+noteNamespace +"\":\n" + noteMsg + "\n******" );

                            if ( noteReplace )
                                git.addNote(    noteMsg, noteNamespace );
                            else
                                git.appendNote( noteMsg, noteNamespace );

                            if(remote.getName().equals(gitSCM.getScmName())){
                                git.push(remote.getName(), "refs/notes/*" );
                            }
                        } catch (GitException e) {
                            e.printStackTrace(listener.error("Failed to add note: \n" + noteMsg  + "\n******"));
                            return false;
                        }
                    }
                }
            }
        }
        return true;
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

            SCM projectSCM = project.getScm();
            GitSCM scm = null;

            if (projectSCM instanceof GitSCM) {
                scm = (GitSCM) projectSCM;
            } else {
                try {
                    final Class<?> multiSCMClass = Class.forName("org.jenkinsci.plugins.multiplescms.MultiSCM");
                    final Method configuredSCM = multiSCMClass.getMethod("getConfiguredSCMs", null);
                    List<SCM> scmlist = (List<SCM>) configuredSCM.invoke(project.getScm(), null);

                    for (SCM s : scmlist) {
                        if (s instanceof GitSCM) {
                            if (remote.equals(((GitSCM) s).getScmName())) {
                                if(((GitSCM) s).getRepositories().get(0).getName() != null && ((GitSCM) s).getRepositories().get(0).getName().equals(remote)){
                                    scm = (GitSCM) s;
                                }else{
                                    return FormValidation.error("No remote repository configured with name '" + remote + "'");                                    
                                }
                            }
                        }
                    }

                } catch (ClassNotFoundException e) {
                    return FormValidation.error(e, "MultiSCM not on classpath");
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (SecurityException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }

            if ((scm == null) || (scm.getRepositoryByName(remote) == null))
                return FormValidation.error("No remote repository configured with name '" + remote + "'");

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

        public void setTargetRepoName(String targetRepoName) {
            this.targetRepoName = targetRepoName;
        }
        
        public void setEmptyTargetRepoToOrigin(){
            if (targetRepoName == null || targetRepoName.trim().length()==0){
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
            
            if ( noteNamespace != null && noteNamespace.trim().length()!=0)
    			this.noteNamespace = Util.fixEmptyAndTrim(noteNamespace);
    		else
    			this.noteNamespace = "master";
            
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
