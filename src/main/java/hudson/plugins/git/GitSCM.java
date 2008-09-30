package hudson.plugins.git;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.util.GitUtils;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.ByteBuffer;
import hudson.util.FormFieldValidator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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

    private List<RemoteRepository> repositories;
    
    /**
     * In-repository branch to follow. Null indicates "master". 
     */
    private final String branch;
    
    private final boolean doMerge;
    
    private final boolean          doGenerateSubmoduleConfigurations;
    
    private final String mergeTarget;
    
    private GitWeb browser;

    private Collection<SubmoduleConfig> submoduleCfg;

    public Collection<SubmoduleConfig> getSubmoduleCfg()
  {
    return submoduleCfg;
  }

  public void setSubmoduleCfg(Collection<SubmoduleConfig> submoduleCfg)
  {
    this.submoduleCfg = submoduleCfg;
  }

    @DataBoundConstructor
    public GitSCM(String source, String branch, boolean doMerge, boolean doGenerateSubmoduleConfigurations,
      String mergeTarget, List<RemoteRepository> repositories,
      Collection<SubmoduleConfig> submoduleCfg,
      GitWeb browser)
  {
        this.source = source;

        // normalization
        branch = Util.fixEmpty(branch);
        
        this.repositories = repositories;
        this.branch = branch;
        this.browser = browser;
        this.doMerge = doMerge;
        this.mergeTarget = mergeTarget;
        
        this.doGenerateSubmoduleConfigurations = doGenerateSubmoduleConfigurations;
        this.submoduleCfg = submoduleCfg;
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
        return branch==null?"":branch;
    }

    @Override
    public GitWeb getBrowser() {
        return browser;
    }
    
    public List<RemoteRepository> getRepositories()
    {
    return repositories;
  }
    
    @Override
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException, InterruptedException {
    	
    	IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);
    	
    	listener.getLogger().println("Poll for changes");
    	
    	if(git.hasGitRepo())
    	{
    		// Repo is there - do a fetch
    		listener.getLogger().println("Update repository");
	    	// return true if there are changes, false if not
	    	
    		// Get from 'Main' repo
    		git.fetch();
    		
    		for(RemoteRepository remoteRepository : repositories)
    		{
    		  fetchFrom(git, launcher, workspace, listener, remoteRepository);
    		}
	    	
	    	// Find out if there are any changes from there to now
	    	return branchesThatNeedBuilding(launcher, workspace, listener).size() > 0;
    	}
    	else
    	{
    		return true;
    		
    	}
    	
       
    }
    
    /**
   * Fetch information from a particular remote repository. Attempt to fetch from submodules, if they exist in the local
   * WC
   * 
   * @param git
   * @param listener
   * @param remoteRepository
   */
  private void fetchFrom(IGitAPI git, Launcher launcher, FilePath workspace, TaskListener listener,
      RemoteRepository remoteRepository)
  {
    try
    {
      git.fetch(remoteRepository.getUrl(), remoteRepository.getRefspec());

      List<IndexEntry> submodules = new GitUtils(listener, git).getSubmodules("HEAD");

      for (IndexEntry submodule : submodules)
      {
        try
        {
          RemoteRepository submoduleRemoteRepository = remoteRepository.getSubmoduleRepository(submodule.getFile());

          FilePath subdir = new FilePath(workspace, submodule.getFile());
          IGitAPI subGit = new GitAPI(getDescriptor().getGitExe(), launcher, subdir, listener);

          subGit.fetch(submoduleRemoteRepository.getUrl(), submoduleRemoteRepository.getRefspec());
        }
        catch (GitException ex)
        {
          listener.getLogger().println(
              "Problem fetching from " + remoteRepository.getName() + " - could be unavailable. Continuing anyway");
        }

      }
    }
    catch (GitException ex)
    {
      listener.getLogger().println(
          "Problem fetching from " + remoteRepository.getName() + " / " + remoteRepository.getUrl()
              + " - could be unavailable. Continuing anyway");
    }

  }

 

    private Collection<Revision> filterBranches(Collection<Revision> branches)
    {
    	if( this.branch == null || this.branch.length() == 0 )
    		return branches;
    	Set<Revision> interesting = new HashSet<Revision>();
    for (Revision r : branches)
    	{
        	for (Branch b : r.getBranches())
      {
        if (!b.getName().equals(this.branch)) interesting.add(r);
      }
    	}
    	return interesting;
    }

    @Override
  public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener,
      File changelogFile) throws IOException, InterruptedException
  {

    IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);

    if (git.hasGitRepo())
    {
      // It's an update

      listener.getLogger().println("Checkout (update)");

      git.fetch();

      for (RemoteRepository remoteRepository : repositories)
      {
        try
        {
          git.fetch(remoteRepository.getUrl(), remoteRepository.getRefspec());
        }
        catch (GitException ex)
        {
          listener.getLogger().println(
              "Problem fetching from " + remoteRepository.getName() + " - could be unavailable. Continuing anyway");
        }
      }

    }
    else
    {
      listener.getLogger().println("Checkout (clone)");
      git.clone(source);
      if (git.hasGitModules())
      {
        git.submoduleInit();
        git.submoduleUpdate();
      }
    }

    Set<Revision> toBeBuilt = branchesThatNeedBuilding(launcher, workspace, listener);

    String log = "Candidate revisions to be built: ";
    for (Revision b : toBeBuilt)
      log += b.toString() + "; ";
    listener.getLogger().println(log);

    Revision revToBuild = null;
    String buildnumber = "hudson-" + build.getProject().getName() + "-" + build.getNumber();
    if (toBeBuilt.size() == 0)
    {
      revToBuild = new Revision(git.revParse("HEAD"));
      listener.getLogger().println("Nothing to do (no unbuilt branches) - rebuilding HEAD " + revToBuild);
    }
    else
    {
      revToBuild = toBeBuilt.iterator().next();
    }

    if (this.doMerge)
    {
      // Do we need to merge this revision onto MergeTarget
      if (!revToBuild.containsBranchName(getMergeTarget()))
      {
        // Only merge if there's a branch to merge that isn't us..
        listener.getLogger().println("Merging " + revToBuild + " onto " + getMergeTarget());

        // checkout origin/blah
        git.checkout(getRemoteMergeTarget());

        try
        {
          git.merge(revToBuild.getSha1());
        }
        catch (Exception ex)
        {
          listener.getLogger().println("Branch not suitable for integration as it does not merge cleanly");

          // We still need to tag something to prevent repetitive builds from happening - tag the candidate
          // branch.
          git.checkout(revToBuild.getSha1());

          git.tag(buildnumber, "Hudson Build #" + build.getNumber());

          return false;
        }

        if (git.hasGitModules())
        {
          git.submoduleUpdate();
        }
        // Tag the successful merge
        git.tag(buildnumber, "Hudson Build #" + build.getNumber());

        String lastRevWas = whenWasBranchLastBuilt(revToBuild.getSha1(), launcher, workspace, listener);

        // Fetch the diffs into the changelog file
        putChangelogDiffsIntoFile(lastRevWas, git.revParse("HEAD"), launcher, workspace, listener, changelogFile);

        return true;
      }
    }

    // Straight compile-the-branch
    listener.getLogger().println("Checking out " + revToBuild);
    git.checkout(revToBuild.getSha1());

    // if( compileSubmoduleCompares )
    if (doGenerateSubmoduleConfigurations)
    {
      SubmoduleCombinator combinator = new SubmoduleCombinator(git, launcher, listener, workspace, submoduleCfg);
      combinator.createSubmoduleCombinations();
    }

    if (git.hasGitModules())
    {
      git.submoduleInit();

      // Git submodule update will only 'fetch' from where it regards as 'origin'. However,
      // it is possible that we are building from a RemoteRepository with changes
      // that are not in 'origin' AND it may be a new module that we've only just discovered.
      // So - try updating from all RRs, then use the submodule Update to do the checkout

      for (RemoteRepository remoteRepository : repositories)
      {
        fetchFrom(git, launcher, workspace, listener, remoteRepository);
      }

      // Update to the correct checkout
      git.submoduleUpdate();

    }

    String lastRevWas = whenWasBranchLastBuilt(revToBuild.getSha1(), launcher, workspace, listener);

    if( lastRevWas != null )
    {
      // No previous revision has been built - don't generate every
      // single change in existence.
      
      changelogFile.delete();
      FileOutputStream fos = new FileOutputStream(changelogFile);
      fos.write("First Time Build of Branch.".getBytes());
      fos.close();
    }
    else
    {
      // Fetch the diffs into the changelog file
      putChangelogDiffsIntoFile(lastRevWas, revToBuild.getSha1(), launcher, workspace, listener, changelogFile);
    }
    
    git.tag(buildnumber, "Hudson Build #" + build.getNumber());

    return true;
  }
    
    
    
  

    
    /**
     * Are there any branches that haven't been built?
     * 
     * @return SHA1 id of the branch that requires building, or NULL if none
     * are found.
     * @throws IOException 
     * @throws InterruptedException 
     */
    private Set<Revision> branchesThatNeedBuilding(Launcher launcher, FilePath workspace, TaskListener listener)
      throws IOException 
    {
    	IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);
    	
    	// These are the set of things tagged that we have tried to build
    	Set<String> setOfThingsBuilt = new HashSet<String>();
    	Set<Revision> branchesThatNeedBuilding = new HashSet<Revision>();
    	
    	for(Tag tag : git.getTags() )
    	{
    		if( tag.getName().startsWith("hudson") )
    			setOfThingsBuilt.add(tag.getCommitSHA1());
    	}
    	
    	for (Revision revision : filterBranches(new GitUtils(listener, git).getTipBranches()))
    	{
    		if (!setOfThingsBuilt.contains(revision.getSha1())) branchesThatNeedBuilding.add(revision);
    	}
    	
    	// There could be a relationship between branches - so master may have merged br1, so br1
    	// hasn't been built, but master has.
    	
    	// If there's any other branch in the list needing building that contains a branch, remove it
    	
    	return (branchesThatNeedBuilding);
    }
    


    
    
	private String whenWasBranchLastBuilt(String branchId, Launcher launcher, FilePath workspace, TaskListener listener) throws IOException 
    {
    	IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);
    	
    	Set<String> setOfThingsBuilt = new HashSet<String>();
    	
    	for(Tag tag : git.getTags() )
    	{
    		if( tag.getName().startsWith("hudson") )
    			setOfThingsBuilt.add(tag.getCommitSHA1());
    	}
    	
    	if (setOfThingsBuilt.isEmpty()) {
    		return null;
    	}
    	
    	while(true)
    	{
    		try
    		{
	    		String rev = git.revParse(branchId);
	    		if(setOfThingsBuilt.contains(rev) )
	    			return rev;
	    		branchId += "^";
    		}
    		catch(GitException ex)
    		{
    			// It's never been built.
    			return null;
    		}
    	}
    	
    	
    }
    
    private void putChangelogDiffsIntoFile(String revFrom, String revTo, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile) throws IOException 
    {
    	IGitAPI git = new GitAPI(getDescriptor().getGitExe(), launcher, workspace, listener);
    	
    	// Delete it to prevent confusion
    	changelogFile.delete();
    	FileOutputStream fos = new FileOutputStream(changelogFile);
    	//fos.write("<data><![CDATA[".getBytes());
    	git.log(revFrom, revTo, fos);
    	//fos.write("]]></data>".getBytes());
    	fos.close();
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
          List<RemoteRepository> remoteRepositories = new ArrayList<RemoteRepository>();

      String[] names = req.getParameterValues("git.repo.name");
      String[] urls = req.getParameterValues("git.repo.url");
      String[] refs = req.getParameterValues("git.repo.refspec");
      if( names != null )
      {
        for (int i = 0; i < names.length; i++)
        {
          remoteRepositories.add(new RemoteRepository(names[i], urls[i], refs[i]));
        }
      }
      
      Collection<SubmoduleConfig> submoduleCfg = new ArrayList<SubmoduleConfig>();
      String[] submoduleName = req.getParameterValues("git.submodule.name");
      String[] submoduleMatch = req.getParameterValues("git.submodule.match");

      if (submoduleName != null)
      {
        for (int i = 0; i < submoduleName.length; i++)
        {
          SubmoduleConfig cfg = new SubmoduleConfig();
          cfg.setSubmoduleName(submoduleName[i]);
          cfg.setBranches(submoduleMatch[i].split(","));
          submoduleCfg.add(cfg);
        }
      }
      
      
      return new GitSCM(
                    req.getParameter("git.source"),
                    req.getParameter("git.branch"),
                    req.getParameter("git.merge")!=null,
                    req.getParameter("git.generate") != null,
                    req.getParameter("git.mergeTarget"),
                    remoteRepositories,
                    submoduleCfg,
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

                        // String result = baos.toString();
                        
                        ok();
                        
                    } catch (Exception e) {
                    	error("Unable to check git version");
                    } 
                    
                }
            }.process();
        }
    }


    private static final long serialVersionUID = 1L;

	public boolean getDoMerge() {
		return doMerge;
	}
	
	public boolean getDoGenerate()
  {
    return this.doGenerateSubmoduleConfigurations;
  }

	public String getMergeTarget() {
		return mergeTarget;
	}

	public String getRemoteMergeTarget() {
		return "origin/" + mergeTarget;
	}
	
}
