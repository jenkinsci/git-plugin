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

    /**
     * Does this project need submodule support?
     */
    private boolean hasSubmodules;
    
    private GitWeb browser;

    @DataBoundConstructor
    public GitSCM(String source, String branch, boolean submodules, GitWeb browser) {
        this.source = source;

        // normalization
        branch = Util.fixEmpty(branch);
        if(branch!=null && branch.equals("master"))
            branch = null;
        this.branch = branch;
        this.hasSubmodules = submodules;
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
    	
    	IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);
    	
    	listener.getLogger().println("Poll for changes");
    	
    	boolean canUpdate = workspace.act(new FileCallable<Boolean>() {
            public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
                
            	File dotGit = new File(ws, ".git");
            	
            	return dotGit.exists();
            }
        });
    	
    	if(git.hasGitRepo())
    	{
    		// Repo is there - do a fetch
    		listener.getLogger().println("Update repository");
	    	// return true if there are changes, false if not
	    	git.fetch();
	    	
	    	// Find out if there are any changes from there to now
	    	return anyChanges(launcher, workspace, listener);
    	}
    	else
    	{
    		listener.getLogger().println("Clone entire repository");
    		git.clone(source);
    	
    		if( git.hasGitModules())
    		{
    			git.submoduleInit();
    			git.submoduleUpdate();
    		}
    		
    		// Yes, there are changes as it is a clone
    		return true;
    		
    	}
    	
       
    }

    @Override
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener, File changelogFile) throws IOException, InterruptedException {
        
    	IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);
    	
        if(git.hasGitRepo())
        {
        	// It's an update
        	
        	listener.getLogger().println("Checkout (update)");
        	
            git.fetch();            
            	
            // Fetch the diffs into the changelog file
            putChangelogDiffsIntoFile(launcher, workspace, listener, changelogFile);
            
            git.merge();
            if( git.hasGitModules())
    		{	
            	git.submoduleUpdate();
    		}     
        }
        else
        {
        	listener.getLogger().println("Checkout (clone)");
            git.clone(source);
            if( git.hasGitModules())
    		{	
	            git.submoduleInit();
	            git.submoduleUpdate();
    		}
        }
        
        return true;
    }
    
    /**
     * Check if there are any changes to be merged / checked out.
     * @return true or false
     * @throws IOException 
     * @throws InterruptedException 
     */
    private boolean anyChanges(Launcher launcher, FilePath workspace, TaskListener listener) throws IOException 
    {
    	IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);
    	
    	listener.getLogger().println("Diff against original");
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
    	git.diff(baos);
    	
        baos.close();
        String result = baos.toString();
        
        // Output is nothing if no changes, or something of the format
        //  1 files changed, 1 insertions(+), 1 deletions(-)

        return ( result.contains("changed") );

    }
    
    private void putChangelogDiffsIntoFile(Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile) throws IOException 
    {
    	IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);
    	
    	// Delete it to prevent confusion
    	changelogFile.delete();
    	FileOutputStream fos = new FileOutputStream(changelogFile);
    	git.log(fos);
    	fos.close();
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
                    req.getParameter("git.submodules")!= null,
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

	public boolean getHasSubmodules() {
		return hasSubmodules;
	}

	public void setHasSubmodules(boolean hasSubmodules) {
		this.hasSubmodules = hasSubmodules;
	}
}
