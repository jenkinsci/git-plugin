package hudson.plugins.git;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.FilePath.FileCallable;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixRun;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.opt.PreBuildMergeOptions;
import hudson.plugins.git.util.*;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.FormFieldValidator;
import hudson.util.FormValidation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

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

	// old fields are left so that old config data can be read in, but
   // they are deprecated. transient so that they won't show up in XML
   // when writing back
	@Deprecated transient String source;
	@Deprecated transient String branch;

	/**
	 * Store a config version so we're able to migrate config on various
	 * functionality upgrades.
	 */
	private Long configVersion;

    /**
     * All the remote repositories that we know about.
     */
	private List<RemoteConfig> remoteRepositories;

	/**
	 * All the branches that we wish to care about building.
	 */
	private List<BranchSpec> branches;

    /**
	 * Options for merging before a build.
	 */
	private PreBuildMergeOptions mergeOptions;

    private boolean doGenerateSubmoduleConfigurations;

	private boolean clean;

    private String choosingStrategy = DEFAULT;
    public static final String DEFAULT = "Default";
    public static final String GERRIT = "Gerrit";

    private GitWeb browser;

	private Collection<SubmoduleConfig> submoduleCfg;

    public static final String GIT_BRANCH = "GIT_BRANCH";

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
            Collection<SubmoduleConfig> submoduleCfg,
            boolean clean,
            String choosingStrategy, GitWeb browser) {

		// normalization
	    this.branches = branches;

		this.remoteRepositories = repositories;
		this.browser = browser;

		this.mergeOptions = mergeOptions;

		this.doGenerateSubmoduleConfigurations = doGenerateSubmoduleConfigurations;
		this.submoduleCfg = submoduleCfg;

		this.clean = clean;
        this.choosingStrategy = choosingStrategy;
		this.configVersion = 1L;
	}

   public Object readResolve()  {
	    // Migrate data

      // Default unspecified to v0
      if( configVersion == null )
         configVersion = 0L;


       if(source!=null)
       {
    	   remoteRepositories = new ArrayList<RemoteConfig>();
   			branches = new ArrayList<BranchSpec>();
   			doGenerateSubmoduleConfigurations = false;
   			mergeOptions = new PreBuildMergeOptions();


    	   try {
			remoteRepositories.add(newRemoteConfig("origin", source, new RefSpec("+refs/heads/*:refs/remotes/origin/*") ));
			} catch (URISyntaxException e) {
				// We gave it our best shot
			}

		   if( branch != null )
	       {
	    	   branches.add(new BranchSpec(branch));
	       }
	       else
	       {
	    	   branches.add(new BranchSpec("*/master"));
	       }

       }


       if( configVersion < 1 && branches != null )
       {
          // Migrate the branch specs from
          // single * wildcard, to ** wildcard.
          for( BranchSpec branchSpec : branches )
          {
             String name = branchSpec.getName();
             name = name.replace("*", "**");
             branchSpec.setName(name);
          }
       }

       if( mergeOptions.doMerge() && mergeOptions.getMergeRemote() == null )
       {
           mergeOptions.setMergeRemote(remoteRepositories.get(0));
       }

       return this;
   }

	@Override
	public GitWeb getBrowser() {
		return browser;
	}

	public boolean getClean() {
		return this.clean;
	}

    public String getChoosingStrategy() {
        return choosingStrategy;
    }
	public List<RemoteConfig> getRepositories() {
		// Handle null-value to ensure backwards-compatibility, ie project configuration missing the <repositories/> XML element
		if (remoteRepositories == null)
			return new ArrayList<RemoteConfig>();
		return remoteRepositories;
	}

    private String getSingleBranch(AbstractBuild<?, ?> build) {
        // if we have multiple branches skip to advanced usecase
        if (getBranches().size() != 1 || getRepositories().size() != 1)
            return null;

        String branch = getBranches().get(0).getName();
        String repository = getRepositories().get(0).getName();

        // replace repository wildcard with repository name
        if (branch.startsWith("*/"))
            branch = repository + branch.substring(1);

        // if the branch name contains more wildcards then the simple usecase
        // does not apply and we need to skip to the advanced usecase
        if (branch.contains("*"))
            return null;

        // substitute build parameters if available
        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters != null)
            branch = parameters.substitute(build, branch);
        return branch;
    }


	@Override
	public boolean pollChanges(final AbstractProject project, Launcher launcher,
			final FilePath workspace, final TaskListener listener)
			throws IOException, InterruptedException
	{
	    // Poll for changes. Are there any unbuilt revisions that Hudson ought to build ?

		final String gitExe = getDescriptor().getGitExe();
        listener.getLogger().println("Using strategy: " + choosingStrategy);

		AbstractBuild lastBuild = (AbstractBuild)project.getLastBuild();

        if( lastBuild != null )
        {
            listener.getLogger().println("[poll] Last Build : #" + lastBuild.getNumber() );
        }

        final BuildData buildData = getBuildData(lastBuild, false);

        if( buildData != null && buildData.lastBuild != null)
        {
            listener.getLogger().println("[poll] Last Built Revision: " + buildData.lastBuild.revision );
        }

        final String singleBranch = getSingleBranch(lastBuild);

		boolean pollChangesResult = workspace.act(new FileCallable<Boolean>() {
			private static final long serialVersionUID = 1L;

			public Boolean invoke(File localWorkspace, VirtualChannel channel) throws IOException {
                EnvVars environment = new EnvVars(System.getenv());

                IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment);


                IBuildChooser buildChooser = createBuildChooser(git, listener, buildData);

                if (git.hasGitRepo()) {
					// Repo is there - do a fetch
					listener.getLogger().println("Fetching changes from the remote Git repositories");

					// Fetch updates
					for (RemoteConfig remoteRepository : getRepositories()) {
						fetchFrom(git, localWorkspace, listener, remoteRepository);
					}

					listener.getLogger().println("Polling for changes in");

					Collection<Revision> candidates = buildChooser.getCandidateRevisions(true, singleBranch);

					return (candidates.size() > 0);
				} else {
					listener.getLogger().println("No Git repository yet, an initial checkout is required");
					return true;
				}
			}
		});

		return pollChangesResult;
	}

    private IBuildChooser createBuildChooser(IGitAPI git, TaskListener listener, BuildData buildData) {
        if(this.choosingStrategy != null && GERRIT.equals(this.choosingStrategy)) {
            return new GerritBuildChooser(this,git,new GitUtils(listener,git), buildData );
        } else
        {
            return new BuildChooser(this, git, new GitUtils(listener, git), buildData);
        }
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
							listener, git.getEnvironment());

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
        listener.getLogger().println("Using strategy: " + choosingStrategy);

		final String projectName = build.getProject().getName();
		final int buildNumber = build.getNumber();
		final String gitExe = getDescriptor().getGitExe();

		final String buildnumber = "hudson-" + projectName + "-" + buildNumber;

		final BuildData buildData = getBuildData(build.getPreviousBuild(), true);

		if( buildData != null && buildData.lastBuild != null)
		{
		    listener.getLogger().println("Last Built Revision: " + buildData.lastBuild.revision );
		}

        final EnvVars environment = build.getEnvironment(listener);

        final String singleBranch = getSingleBranch(build);

        Revision tempParentLastBuiltRev = null;

        if (build instanceof MatrixRun) {
            MatrixBuild parentBuild = ((MatrixRun)build).getParentBuild();
            if (parentBuild != null) {
                BuildData parentBuildData = parentBuild.getAction(BuildData.class);
                if (parentBuildData != null) {
                    tempParentLastBuiltRev = parentBuildData.getLastBuiltRevision();
                }
            }
        }

        final Revision parentLastBuiltRev = tempParentLastBuiltRev;

		final Revision revToBuild = workspace.act(new FileCallable<Revision>() {
			private static final long serialVersionUID = 1L;
			public Revision invoke(File localWorkspace, VirtualChannel channel)
					throws IOException {
			    FilePath ws = new FilePath(localWorkspace);
			    listener.getLogger().println("Checkout:" + ws.getName() + " / " + ws.getRemote() + " - " + ws.getChannel());

                IGitAPI git = new GitAPI(gitExe, ws, listener, environment);

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
                            listener.error("Error cloning remote repo '%s' : %s", rc.getName(), ex.getMessage());
                            if(ex.getCause() != null) {
                                listener.error("Cause: %s", ex.getCause().getMessage());
                            }
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

                if (parentLastBuiltRev != null)
                    return parentLastBuiltRev;

                IBuildChooser buildChooser = createBuildChooser(git, listener, buildData);

                Collection<Revision> candidates = buildChooser.getCandidateRevisions(false, singleBranch);
				if( candidates.size() == 0 )
					return null;
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
			if (!revToBuild.containsBranchName(mergeOptions.getRemoteBranchName())) {
				returnData = workspace.act(new FileCallable<Object[]>() {
					private static final long serialVersionUID = 1L;
					public Object[] invoke(File localWorkspace, VirtualChannel channel)
							throws IOException {
                        IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment);

                        IBuildChooser buildChooser = createBuildChooser(git, listener, buildData);

                        // Do we need to merge this revision onto MergeTarget

						// Only merge if there's a branch to merge that isn't
						// us..
						listener.getLogger().println(
								"Merging " + revToBuild + " onto "
										+ mergeOptions.getMergeTarget());

						// checkout origin/blah
						ObjectId target = git.revParse(mergeOptions.getRemoteBranchName());
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



							buildChooser.revisionBuilt(revToBuild, buildNumber, Result.FAILURE);

							return new Object[]{null, buildChooser.getData()};
						}

						if (git.hasGitModules()) {
							git.submoduleUpdate();
						}

						// Tag the successful merge
						git.tag(buildnumber, "Hudson Build #" + buildNumber);

						StringBuilder changeLog = new StringBuilder();

						if( revToBuild.getBranches().size() > 0 )
								listener.getLogger().println("Warning : There are multiple branch changesets here");

						try {
							for( Branch b : revToBuild.getBranches() )
							{
							    Build lastRevWas = buildData==null?null:buildData.getLastBuildOfBranch(b.getName());
							    if( lastRevWas != null ) {
							        changeLog.append(putChangelogDiffsIntoFile(git,  b.name, lastRevWas.getSHA1().name(), revToBuild.getSha1().name()));
							    }
							}
						} catch (GitException ge) {
							changeLog.append("Unable to retrieve changeset");
						}

						Build buildData = buildChooser.revisionBuilt(revToBuild, buildNumber, null);
						GitUtils gu = new GitUtils(listener,git);
						buildData.mergeRevision = gu.getRevisionForSHA1(target);

						// Fetch the diffs into the changelog file
						return new Object[]{changeLog.toString(), buildChooser.getData()};
					}
				});
				BuildData returningBuildData = (BuildData)returnData[1];
				build.addAction(returningBuildData);
				return changeLogResult((String) returnData[0], changelogFile);
			}
		}

		// No merge

		returnData = workspace.act(new FileCallable<Object[]>() {
			private static final long serialVersionUID = 1L;
			public Object[] invoke(File localWorkspace, VirtualChannel channel)
					throws IOException {
                IGitAPI git = new GitAPI(gitExe, new FilePath(localWorkspace), listener, environment);
                IBuildChooser buildChooser = createBuildChooser(git, listener, buildData);

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

                StringBuilder changeLog = new StringBuilder();

                int histories = 0;

                try {
	                for( Branch b : revToBuild.getBranches() )
	                {
	                    Build lastRevWas = buildData==null?null:buildData.getLastBuildOfBranch(b.getName());

	                    if( lastRevWas != null )
	                    {
	                        listener.getLogger().println("Recording changes in branch " + b.getName());
	                        changeLog.append(putChangelogDiffsIntoFile(git, b.name, lastRevWas.getSHA1().name(), revToBuild.getSha1().name()));
	                        histories++;
	                    } else {
	                        listener.getLogger().println("No change to record in branch " + b.getName());
	                    }
	                }
                } catch (GitException ge) {
					changeLog.append("Unable to retrieve changeset");
                }

                if( histories > 1 )
                    listener.getLogger().println("Warning : There are multiple branch changesets here");


                buildChooser.revisionBuilt(revToBuild, buildNumber, null);

                if (getClean()) {
    				listener.getLogger().println("Cleaning workspace");
                    git.clean();
                }

                // Fetch the diffs into the changelog file
                return new Object[]{changeLog.toString(), buildChooser.getData()};

			}
		});
		build.addAction((Action) returnData[1]);

        return changeLogResult((String) returnData[0], changelogFile);

	}

    public void buildEnvVars(AbstractBuild build, java.util.Map<String, String> env) {
        super.buildEnvVars(build, env);
        env.put(GIT_BRANCH, getSingleBranch(build));
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
		return (DescriptorImpl) super.getDescriptor();
	}

    @Extension
	public static final class DescriptorImpl extends SCMDescriptor<GitSCM> {

		private String gitExe;

		public DescriptorImpl() {
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

			String[] urls = req.getParameterValues("git.repo.url");
			String[] names = req.getParameterValues("git.repo.name");

			names = GitUtils.fixupNames(names, urls);

            String[] refs = req.getParameterValues("git.repo.refspec");
            if (names != null)
            {
                for (int i = 0; i < names.length; i++)
                {
                	String name = names[i];
                	name = name.replace(' ', '_');

                	if( refs[i] == null || refs[i].length() == 0 )
                	{
                		refs[i] = "+refs/heads/*:refs/remotes/" + name + "/*";
                	}

                    repoConfig.setString("remote", name, "url", urls[i]);
                    repoConfig.setString("remote", name, "fetch", refs[i]);
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

            if( branches.size() == 0 )
            {
            	branches.add(new BranchSpec("*/master"));
            }

            PreBuildMergeOptions mergeOptions = new PreBuildMergeOptions();
            if( req.getParameter("git.doMerge") != null && req.getParameter("git.doMerge").trim().length()  > 0 )
            {
                RemoteConfig mergeRemote = null;
                String mergeRemoteName = req.getParameter("git.mergeRemote").trim();
                if (mergeRemoteName.length() == 0)
                    mergeRemote = remoteRepositories.get(0);
                else
                    for (RemoteConfig remote : remoteRepositories)
                    {
                        if (remote.getName().equals(mergeRemoteName))
                        {
                            mergeRemote = remote;
                            break;
                        }
                    }
                if (mergeRemote==null)
                     throw new FormException("No remote repository configured with name '" + mergeRemoteName + "'", "git.mergeRemote");
                mergeOptions.setMergeRemote(mergeRemote);

            	mergeOptions.setMergeTarget(req.getParameter("git.mergeTarget"));
            }

			Collection<SubmoduleConfig> submoduleCfg = new ArrayList<SubmoduleConfig>();

			GitWeb gitWeb = null;
			String gitWebUrl = req.getParameter("gitweb.url");
			if (gitWebUrl != null && gitWebUrl.length() > 0)
			{
				try
				{
					gitWeb = new GitWeb(gitWebUrl);
				}
				catch (MalformedURLException e)
				{
					throw new GitException("Error creating GitWeb", e);
				}
			}

			return new GitSCM(
					remoteRepositories,
					branches,
					mergeOptions,
				    req.getParameter("git.generate") != null,
					submoduleCfg,
					req.getParameter("git.clean") != null,
                    req.getParameter("git.choosing_strategy"),
					gitWeb);
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

		public FormValidation doGitRemoteNameCheck(StaplerRequest req, StaplerResponse rsp)
				throws IOException, ServletException {
			String mergeRemoteName = req.getParameter("value");

			if (mergeRemoteName.length() == 0)
				return FormValidation.ok();

			String[] urls = req.getParameterValues("git.repo.url");
			String[] names = req.getParameterValues("git.repo.name");

			names = GitUtils.fixupNames(names, urls);

			for (String name : names)
			{
				if (name.equals(mergeRemoteName))
					return FormValidation.ok();
			}

			return FormValidation.error("No remote repository configured with name '" + mergeRemoteName + "'");
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

    /**
     * Look back as far as needed to find a valid BuildData.  BuildData
     * may not be recorded if an exception occurs in the plugin logic.
     * @param build
     * @param clone
     * @return the last recorded build data
     */
    public BuildData getBuildData(Run build, boolean clone)
    {
		BuildData buildData = null;
		while (build != null)
		{
			buildData = build.getAction(BuildData.class);
			if (buildData != null)
				break;
			build = build.getPreviousBuild();
		}

		if (buildData == null)
			return null;

		if (clone)
			return buildData.clone();
		else
			return buildData;
    }
}
