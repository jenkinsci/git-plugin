package hudson.plugins.git;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.BuildChooser;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.GitUtils;
import hudson.plugins.git.util.IBuildChooser;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.RepositoryBrowsers;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormFieldValidator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.RepositoryConfig;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;

/**
 * Git SCM.
 * 
 * @author Nigel Magnay
 */
public class GitSCM extends SCM implements Serializable {
    
    /**
     * All the remote repositories that we know about.
     */
	private final List<RemoteConfig> remoteRepositories;
	
	/**
	 * All the branches that we wish to care about building.
	 */
	private final List<BranchSpec> branches;

    /**
	 * Options for merging before a build.
	 */
	private final PreBuildMergeOptions mergeOptions;

    private final boolean doGenerateSubmoduleConfigurations;

	private GitWeb browser;

	private Collection<SubmoduleConfig> submoduleCfg;

	public Collection<SubmoduleConfig> getSubmoduleCfg() {
		return submoduleCfg;
	}

	public void setSubmoduleCfg(Collection<SubmoduleConfig> submoduleCfg) {
		this.submoduleCfg = submoduleCfg;
	}

	@DataBoundConstructor
	public GitSCM(
	        List<RemoteConfig> repositories,
	        List<BranchSpec> branches,
	        PreBuildMergeOptions mergeOptions,
	        boolean doGenerateSubmoduleConfigurations, 
			Collection<SubmoduleConfig> submoduleCfg, GitWeb browser) {
		
		// normalization
	    this.branches = branches;

		this.remoteRepositories = repositories;
		this.browser = browser;
		
		this.mergeOptions = mergeOptions;

		this.doGenerateSubmoduleConfigurations = doGenerateSubmoduleConfigurations;
		this.submoduleCfg = submoduleCfg;

	}

	@Override
	public GitWeb getBrowser() {
		return browser;
	}

	public List<RemoteConfig> getRepositories() {
		// Handle null-value to ensure backwards-compatibility, ie project configuration missing the <repositories/> XML element
		if (remoteRepositories == null)
			return new ArrayList<RemoteConfig>();
		return remoteRepositories;
	}

	@Override
	public boolean pollChanges(final AbstractProject project, Launcher launcher,
			final FilePath workspace, final TaskListener listener)
			throws IOException, InterruptedException 
	{
	    // Poll for changes. Are there any unbuilt revisions that Hudson ought to build ?
		
		final String gitExe = getDescriptor().getGitExe();
		
		final BuildData buildData = project.getLastBuild().getAction(BuildData.class);
		
		
		boolean pollChangesResult = workspace.act(new FileCallable<Boolean>() {
			public Boolean invoke(File localWorkspace, VirtualChannel channel)
					throws IOException {

				IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener);
				
				
				IBuildChooser buildChooser = new BuildChooser(GitSCM.this,git,new GitUtils(listener,git), buildData );
				
				if (git.hasGitRepo()) {
					// Repo is there - do a fetch
					listener.getLogger().println("Fetching changes from the remote Git repositories");

					// Fetch updates	
					for (RemoteConfig remoteRepository : getRepositories()) {
						fetchFrom(git, localWorkspace, listener, remoteRepository);
					}

					listener.getLogger().println("Polling for changes in");
					
					Collection<Revision> candidates = buildChooser.getCandidateRevisions(); 
					
					return (candidates.size() > 0);
				} else {
					listener.getLogger().println("No Git repository yet, an initial checkout is required");
					return true;
				}
			}

            
		});
		
		return pollChangesResult;
	}

	
	


	/**
	 * Fetch information from a particular remote repository. Attempt to fetch
	 * from submodules, if they exist in the local WC
	 * 
	 * @param git
	 * @param listener
	 * @param remoteRepository
	 * @throws  
	 */
	private void fetchFrom(IGitAPI git, File workspace, TaskListener listener,
	        RemoteConfig remoteRepository) {
		try {
			git.fetch(remoteRepository);

			List<IndexEntry> submodules = new GitUtils(listener, git)
					.getSubmodules("HEAD");

			for (IndexEntry submodule : submodules) {
				try {
					RemoteConfig submoduleRemoteRepository = getSubmoduleRepository(remoteRepository, submodule.getFile());

					File subdir = new File(workspace, submodule.getFile());
					IGitAPI subGit = new GitAPI(git.getGitExe(), new FilePath(subdir),
							listener);

					subGit.fetch(submoduleRemoteRepository);
				} catch (Exception ex) {
					listener
							.error(
									"Problem fetching from "
											+ remoteRepository.getName()
											+ " - could be unavailable. Continuing anyway");
				}

			}
		} catch (GitException ex) {
			listener.error(
					"Problem fetching from " + remoteRepository.getName()
							+ " / " + remoteRepository.getName()
							+ " - could be unavailable. Continuing anyway");
		}

	}

	public RemoteConfig getSubmoduleRepository(RemoteConfig orig, String name) throws URISyntaxException
    {
	    // Attempt to guess the submodule URL??
	    
        String refUrl = orig.getURIs().get(0).toString();

        if (refUrl.endsWith("/.git"))
        {
            refUrl = refUrl.substring(0, refUrl.length() - 4);
        }

        if (!refUrl.endsWith("/")) refUrl += "/";

        refUrl += name;

        if (!refUrl.endsWith("/")) refUrl += "/";

        refUrl += ".git";

        return newRemoteConfig(name, refUrl, orig.getFetchRefSpecs().get(0) );
    }

	private RemoteConfig newRemoteConfig(String name, String refUrl, RefSpec refSpec) throws URISyntaxException
	{
		
        File temp = null;
        try
        {
        	temp = File.createTempFile("tmp", "config");    
        	RepositoryConfig repoConfig = new RepositoryConfig(null, temp);
            // Make up a repo config from the request parameters
            
   
            repoConfig.setString("remote", name, "url", refUrl);
            repoConfig.setString("remote", name, "fetch", refSpec.toString());
            repoConfig.save();
            return RemoteConfig.getAllRemoteConfigs(repoConfig).get(0);
        }
        catch(Exception ex)
        {
        	throw new GitException("Error creating temp file");
        }
        finally
        {
        	if( temp != null )
        		temp.delete();
        }
        
		   
	}
	
	private boolean changeLogResult(String changeLog, File changelogFile) throws IOException
	{
		if (changeLog == null)
			return false;
		else {
			changelogFile.delete();

			FileOutputStream fos = new FileOutputStream(changelogFile);
			fos.write(changeLog.getBytes());
			fos.close();
			// Write to file
			return true;
		}
	}
	
	
	@Override
	public boolean checkout(final AbstractBuild build, Launcher launcher,
			final FilePath workspace, final BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {

	    listener.getLogger().println("Checkout:" + workspace.getName() + " / " + workspace.getRemote() + " - " + workspace.getChannel());
	    
		final String projectName = build.getProject().getName();
		final int buildNumber = build.getNumber();
		final String gitExe = getDescriptor().getGitExe();
		
		final String buildnumber = "hudson-" + projectName + "-" + buildNumber;
		
		final BuildData buildData = build.getProject().getLastBuild().getAction(BuildData.class);
		
		final Revision revToBuild = workspace.act(new FileCallable<Revision>() {
			public Revision invoke(File localWorkspace, VirtualChannel channel)
					throws IOException {
			    FilePath ws = new FilePath(localWorkspace);
			    listener.getLogger().println("Checkout:" + ws.getName() + " / " + ws.getRemote() + " - " + ws.getChannel());
			    
				IGitAPI git = new GitAPI(gitExe, ws, listener);

				if (git.hasGitRepo()) {
					// It's an update

					listener.getLogger().println("Fetching changes from the remote Git repository");

					for (RemoteConfig remoteRepository : getRepositories()) 
					{
					   fetchFrom(git,localWorkspace,listener,remoteRepository);
					}

				} else {
					listener.getLogger().println("Cloning the remote Git repository");
					
					// Go through the repositories, trying to clone from one
					//
					boolean successfullyCloned = false;
					for(RemoteConfig rc : remoteRepositories)
					{
					    try
					    {
					        git.clone(rc);
					        successfullyCloned = true;
					        break;
					    }
					    catch(GitException ex)
					    {
					        // Failed. Try the next one
					        listener.getLogger().println("Trying next repository");
					    }
					}
					
					if( !successfullyCloned )
					{
					    listener.error("Could not clone from a repository");
					    throw new GitException("Could not clone");
					}
					
					// Also do a fetch
					for (RemoteConfig remoteRepository : getRepositories()) 
                    {
                       fetchFrom(git,localWorkspace,listener,remoteRepository);
                    }
					
					if (git.hasGitModules()) {
						git.submoduleInit();
						git.submoduleUpdate();
					}
				}
				
                FilePath config = new FilePath(workspace, ".buildinfo");                
                IBuildChooser buildChooser = new BuildChooser(GitSCM.this,git,new GitUtils(listener,git), buildData );
				
                Collection<Revision> candidates = buildChooser.getCandidateRevisions();
				if( candidates.size() == 0 )
					return buildChooser.getLastBuiltRevision();
				return candidates.iterator().next();
			}
		});

		if( revToBuild == null )
		{
			// getBuildCandidates should make the last item the last build, so a re-build
			// will build the last built thing.
			listener.error("Nothing to do");
			return false;
		}
		listener.getLogger().println("Commencing build of " + revToBuild);
		Object[] returnData; // Changelog, BuildData

		if (mergeOptions.doMerge()) {
			if (!revToBuild.containsBranchName(mergeOptions.getMergeTarget())) {
				returnData = workspace.act(new FileCallable<Object[]>() {
					public Object[] invoke(File localWorkspace, VirtualChannel channel)
							throws IOException {
						IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener);
               
                        IBuildChooser buildChooser = new BuildChooser(GitSCM.this,git,new GitUtils(listener,git), buildData );
                        
						// Do we need to merge this revision onto MergeTarget

						// Only merge if there's a branch to merge that isn't
						// us..
						listener.getLogger().println(
								"Merging " + revToBuild + " onto "
										+ mergeOptions.getMergeTarget());

						// checkout origin/blah
						ObjectId target = git.revParse(mergeOptions.getMergeTarget());
						git.checkout(target.name());

						try {
							git.merge(revToBuild.getSha1().name());
						} catch (Exception ex) {
							listener
									.getLogger()
									.println(
											"Branch not suitable for integration as it does not merge cleanly");

							// We still need to tag something to prevent
							// repetitive builds from happening - tag the
							// candidate
							// branch.
							git.checkout(revToBuild.getSha1().name());

							git
									.tag(buildnumber, "Hudson Build #"
											+ buildNumber);
							
							
			                
							buildChooser.revisionBuilt(revToBuild, false);
							build.addAction(buildChooser.getData());
							return null;
						}

						if (git.hasGitModules()) {
							git.submoduleUpdate();
						}
						
						// Tag the successful merge
						git.tag(buildnumber, "Hudson Build #" + buildNumber);
						
						String changeLog = "";
						
						if( revToBuild.getBranches().size() > 0 )
								listener.getLogger().println("Warning : There are multiple branch changesets here");
						
						for( Branch b : revToBuild.getBranches() )
						{
						    ObjectId lastRevWas = buildChooser.getLastBuiltRevisionOfBranch(b.getName());
						    
						    if( lastRevWas != null )
						    {
						    	// TODO: Inefficent string concat
						        changeLog += putChangelogDiffsIntoFile(git,  b.name, lastRevWas.name(), revToBuild.getSha1().name());    
						    }
						}
						
						buildChooser.revisionBuilt(revToBuild, true);
						
						// Fetch the diffs into the changelog file
						return new Object[]{changeLog, buildChooser.getData()};

					}
				});
				build.addAction((Action) returnData[1]);
				return changeLogResult((String) returnData[0], changelogFile);
			}
		}

		// No merge

		returnData = workspace.act(new FileCallable<Object[]>() {
			public Object[] invoke(File localWorkspace, VirtualChannel channel)
					throws IOException {
				IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener);                            
                IBuildChooser buildChooser = new BuildChooser(GitSCM.this,git,new GitUtils(listener,git), buildData );
                
				// Straight compile-the-branch
				listener.getLogger().println("Checking out " + revToBuild);
				git.checkout(revToBuild.getSha1().name());

				// if( compileSubmoduleCompares )
				if (doGenerateSubmoduleConfigurations) {
					SubmoduleCombinator combinator = new SubmoduleCombinator(
							git, listener, localWorkspace, submoduleCfg);
					combinator.createSubmoduleCombinations();
				}

				if (git.hasGitModules()) {
					git.submoduleInit();

					// Git submodule update will only 'fetch' from where it
					// regards as 'origin'. However,
					// it is possible that we are building from a
					// RemoteRepository with changes
					// that are not in 'origin' AND it may be a new module that
					// we've only just discovered.
					// So - try updating from all RRs, then use the submodule
					// Update to do the checkout

					for (RemoteConfig remoteRepository : getRepositories()) {
						fetchFrom(git, localWorkspace, listener, remoteRepository);
					}

					// Update to the correct checkout
					git.submoduleUpdate();

				}

				// Tag the successful merge
                git.tag(buildnumber, "Hudson Build #" + buildNumber);
                
                StringBuffer changeLog = new StringBuffer();
                
                int histories = 0;
                
                for( Branch b : revToBuild.getBranches() )
                {
                    ObjectId lastRevWas = buildChooser.getLastBuiltRevisionOfBranch(b.getName());
                    
                    if( lastRevWas != null )
                    {
                        listener.getLogger().println("Recording changes in branch " + b.getName());
                        changeLog.append(putChangelogDiffsIntoFile(git, b.name, lastRevWas.name(), revToBuild.getSha1().name()));
                        histories++;
                    } else {
                        listener.getLogger().println("No change to record in branch " + b.getName());
                    }
                }
                
                if( histories > 1 )
                    listener.getLogger().println("Warning : There are multiple branch changesets here");
                
                
                buildChooser.revisionBuilt(revToBuild, true);
                
                // Fetch the diffs into the changelog file
                return new Object[]{changeLog.toString(), buildChooser.getData()};

			}
		});
		build.addAction((Action) returnData[1]);
        return changeLogResult((String) returnData[0], changelogFile);

	}

	private String putChangelogDiffsIntoFile(IGitAPI git, String branchName, String revFrom,
			String revTo) throws IOException {
		ByteArrayOutputStream fos = new ByteArrayOutputStream();
		// fos.write("<data><![CDATA[".getBytes());
		String changeset = "Changes in branch " + branchName + ", between " + revFrom + " and " + revTo + "\n";
		fos.write(changeset.getBytes());
		
		git.changelog(revFrom, revTo, fos);
		// fos.write("]]></data>".getBytes());
		fos.close();
		return fos.toString();
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

		private DescriptorImpl() {
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
			if (gitExe == null)
				return "git";
			return gitExe;
		}

		public SCM newInstance(StaplerRequest req) throws FormException {
			List<RemoteConfig> remoteRepositories;
			File temp;
			
			try
            {
			    temp = File.createTempFile("tmp", "config");
			    
            }
            catch (IOException e1)
            {
            	 throw new GitException("Error creating repositories", e1);
            }
			
			RepositoryConfig repoConfig = new RepositoryConfig(null, temp);
			// Make up a repo config from the request parameters
			
			String[] names = req.getParameterValues("git.repo.name");
            String[] urls = req.getParameterValues("git.repo.url");
            String[] refs = req.getParameterValues("git.repo.refspec");
            if (names != null)
            {
                for (int i = 0; i < names.length; i++)
                {
                	if( refs[i] == null || refs[i].length() == 0 )
                	{
                		refs[i] = "+refs/heads/*:refs/remotes/" + names[i] + "/*";                 				
                	}
                	
                    repoConfig.setString("remote", names[i], "url", urls[i]);
                    repoConfig.setString("remote", names[i], "fetch", refs[i]);
                }
            }
			
			try
            {
				repoConfig.save();
                remoteRepositories = RemoteConfig.getAllRemoteConfigs(repoConfig);
            }
            catch (Exception e)
            {
                throw new GitException("Error creating repositories", e);
            }
			
            temp.delete();
            
            List<BranchSpec> branches = new ArrayList<BranchSpec>();
            String[] branchData = req.getParameterValues("git.branch");
            for( int i=0; i<branchData.length;i++ )
            {
                branches.add(new BranchSpec(branchData[i]));
            }
            
            PreBuildMergeOptions mergeOptions = new PreBuildMergeOptions();
            if( req.getParameterValues("git.merge") != null && req.getParameterValues("git.merge").length > 0 )
            {
            	mergeOptions.setMergeTarget(req.getParameterValues("git.merge")[0]);
            }

			Collection<SubmoduleConfig> submoduleCfg = new ArrayList<SubmoduleConfig>();
			/*
			String[] submoduleName = req
					.getParameterValues("git.submodule.name");
			String[] submoduleMatch = req
					.getParameterValues("git.submodule.match");

			if (submoduleName != null) {
				for (int i = 0; i < submoduleName.length; i++) {
					SubmoduleConfig cfg = new SubmoduleConfig();
					cfg.setSubmoduleName(submoduleName[i]);
					cfg.setBranches(submoduleMatch[i].split(","));
					submoduleCfg.add(cfg);
				}
			}
            */
			
            
			return new GitSCM(
					remoteRepositories,
					branches,
					mergeOptions,
				    req.getParameter("git.generate") != null, 
					submoduleCfg, RepositoryBrowsers
							.createInstance(GitWeb.class, req, "git.browser")
				);
		}

		public boolean configure(StaplerRequest req) throws FormException {
			gitExe = req.getParameter("git.gitExe");
			save();
			return true;
		}

		public void doGitExeCheck(StaplerRequest req, StaplerResponse rsp)
				throws IOException, ServletException {
			new FormFieldValidator.Executable(req, rsp) {
				protected void checkExecutable(File exe) throws IOException,
						ServletException {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					try {
						Proc proc = Hudson.getInstance().createLauncher(
								TaskListener.NULL).launch(
								new String[] { getGitExe(), "--version" },
								new String[0], baos, null);
						proc.join();

						// String result = baos.toString();

						ok();

					} catch (InterruptedException e) {
						error("Unable to check git version");
					} catch (RuntimeException e) {
						error("Unable to check git version");
					}

				}
			}.process();
		}
	}

	private static final long serialVersionUID = 1L;

	

	public boolean getDoGenerate() {
		return this.doGenerateSubmoduleConfigurations;
	}

    public List<BranchSpec> getBranches()
    {
        return branches;
    }
    
    public PreBuildMergeOptions getMergeOptions()
    {
        return mergeOptions;
    }

}
