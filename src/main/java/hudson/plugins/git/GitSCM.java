package hudson.plugins.git;

import hudson.*;
import hudson.FilePath.FileCallable;
import hudson.model.*;
import hudson.plugins.git.browser.GitWeb;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.sound.midi.MidiDevice.Info;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Git SCM.
 * 
 * @author Nigel Magnay
 */
public class GitSCM extends SCM implements Serializable {
    /**
     * Source repository URL from which we pull.
     */
    private final String source;

    /**
     * In-repository branch to follow. Null indicates "master". 
     */
    private final String branch;

    private GitWeb browser;

    @DataBoundConstructor
    public GitSCM(String source, String branch, GitWeb browser) {
        this.source = source;

        // normalization
        branch = Util.fixEmpty(branch);
        if(branch!=null && branch.equals("master"))
            branch = null;
        this.branch = branch;

        this.browser = browser;
    }

    /**
     * Gets the source repository path.
     * Either URL or local file path.
     */
    public String getSource() {
        return source;
    }

    /**
     * In-repository branch to follow. Null indicates "default".
     */
    public String getBranch() {
        return branch==null?"master":branch;
    }

    @Override
    public GitWeb getBrowser() {
        return browser;
    }

    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
    	
    	listener.getLogger().println("Poll for changes");
    	
    	boolean canUpdate = workspace.act(new FileCallable<Boolean>() {
            public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
                
            	File dotGit = new File(ws, ".git");
            	
            	return dotGit.exists();
            }
        });
    	
    	if(canUpdate)
    	{
    		listener.getLogger().println("Update repository");
	    	// return true if there are changes, false if not
	    	if(!fetch(launcher,workspace,listener))
	        	return false;
    	}
    	else
    	{
    		listener.getLogger().println("Clone entire repository");
    		clone(launcher,workspace,listener);
    		return true;
    		
    	}
    	
        return anyChanges(launcher, workspace, listener);
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        
    	
    	
    	boolean canUpdate = workspace.act(new FileCallable<Boolean>() {
            public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
                
            	File dotGit = new File(ws, ".git");
            	
            	return dotGit.exists();
            }
        });
    	
        if(canUpdate)
        {
        	listener.getLogger().println("Checkout (update)");
        	
            if(!fetch(launcher,workspace,listener))            
            	return false;
            
            // Fetch the diffs into the changelog file
            diff(launcher, workspace, listener, changelogFile);
            
            return merge(launcher, workspace, listener);
            
        }
        else
        {
        	listener.getLogger().println("Checkout (clone)");
            return clone(launcher,workspace,listener);
        }
    }
    
    /**
     * Check if there are any changes to be merged / checked out.
     * @return true or false
     * @throws InterruptedException 
     */
    private boolean anyChanges(Launcher launcher, FilePath workspace, TaskListener listener) throws InterruptedException
    {
    	
    	
    	listener.getLogger().println("Diff against original");
    	
    	ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getDescriptor().getGitExe(),"diff","--shortstat", "origin");
        
        try {
        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        	
            if(launcher.launch(args.toCommandArray(),
            		createEnvVarMap(),
            		baos,workspace).join()!=0) {
                listener.error("Failed to diff "+source);
                return false;
            }
            
            baos.close();
            String result = baos.toString();
            
            // Output is nothing if no changes, or something of the format
            //  1 files changed, 1 insertions(+), 1 deletions(-)

            return ( result.contains("changed") );
            	
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to diff "+source));
            return false;
        }
    }
    
    private boolean diff(Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile) throws InterruptedException
    {
    	// Delete it to prevent confusion
    	changelogFile.delete();
    	
    	listener.getLogger().println("Diff against original");
    	// git log --numstat -M --summary --pretty=raw HEAD..origin

    	// Find the changes between our current working copy and now
    	ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getDescriptor().getGitExe(),"log","--numstat", "-M", "--summary", "--pretty=raw", "HEAD..origin");
        
        try {
        	FileOutputStream fos = new FileOutputStream(changelogFile);
        	
            if(launcher.launch(args.toCommandArray(),
            		createEnvVarMap(),
            		fos,workspace).join()!=0) {
                listener.error("Failed to diff "+source);
                return false;
            }
            
            fos.close();
            
            return true;
            	
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to diff "+source));
            return false;
        }
    }

    /**
     * Fetch any updates to the local repository from the remote.
     */
    private boolean fetch(Launcher launcher, FilePath workspace, TaskListener listener) throws InterruptedException {
      
    	listener.getLogger().println("Fetching upstream changes");
    	
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getDescriptor().getGitExe(),"fetch");
        
        try {
            if(launcher.launch(args.toCommandArray(),
            		createEnvVarMap(),
            		listener.getLogger(),workspace).join()!=0) {
                listener.error("Failed to fetch "+source);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to fetch "+source));
            return false;
        }

        return true;
    }
    
    /**
     * Merge any changes into the head.
     */
    private boolean merge(Launcher launcher, FilePath workspace, TaskListener listener) throws InterruptedException {
      
    	listener.getLogger().println("Merging changes changes");
    	
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getDescriptor().getGitExe(),"merge","origin");
        
        try {
            if(launcher.launch(args.toCommandArray(),createEnvVarMap(),listener.getLogger(),workspace).join()!=0) {
                listener.error("Failed to merge "+source);
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to merge "+source));
            return false;
        }

        return true;
    }
    
    /**
     * Start from scratch and clone the whole repository.
     */
    private boolean clone(Launcher launcher, FilePath workspace, TaskListener listener) throws InterruptedException {
    	listener.getLogger().println("Cloning repository " + source);
    	
    	try {
            workspace.deleteRecursive();                    
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clean the workspace"));
            return false;
        }

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getDescriptor().getGitExe(),"clone");
        
        args.add(source,workspace.getRemote());
        try {
            if(launcher.launch(args.toCommandArray(),createEnvVarMap(),listener.getLogger(),null).join()!=0) {
                listener.error("Clone returned an error result");
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to clone "+source));
            return false;
        }
        
        // createEmptyChangeLog(changelogFile, listener, "changelog");

        return true; 
    }

    protected final Map<String,String> createEnvVarMap() {
        Map<String,String> env = new HashMap<String,String>();
    
        return env;
    }
    
    @Override
    public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new GitChangeLogParser();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends SCMDescriptor<GitSCM> {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private String gitExe;

        private  DescriptorImpl() {
            super(GitSCM.class, GitWeb.class);
            load();
        }

        public String getDisplayName() {
            return "Git";
        }

        /**
         * Path to git executable.
         */
        public String getGitExe() {
            if(gitExe==null) return "git";
            return gitExe;
        }

        public SCM newInstance(StaplerRequest req) throws FormException {
            return new GitSCM(
                    req.getParameter("git.source"),
                    req.getParameter("git.branch"),
                    RepositoryBrowsers.createInstance(GitWeb.class, req, "git.browser"));
        }

        public boolean configure(StaplerRequest req) throws FormException {
            gitExe = req.getParameter("git.gitExe");
            save();
            return true;
        }

        public void doGitExeCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.Executable(req,rsp) {
                protected void checkExecutable(File exe) throws IOException, ServletException {
                    ByteBuffer baos = new ByteBuffer();
                    try {
                        Proc proc = Hudson.getInstance().createLauncher(TaskListener.NULL).launch(
                                new String[]{getGitExe(), "--version"}, new String[0], baos, null);
                        proc.join();

                        String result = baos.toString();
                        
                        ok();
                        
                    } catch (IOException e) {
                        // failed
                    } catch (InterruptedException e) {
                        // failed
                    }
                    error("Unable to check git version");
                }
            }.process();
        }
    }


    private static final long serialVersionUID = 1L;
}
