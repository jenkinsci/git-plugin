package hudson.plugins.git;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.Util;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.plugins.git.browser.GitWeb;
import hudson.plugins.git.util.GitUtils;
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

	private final boolean doGenerateSubmoduleConfigurations;

	private final String mergeTarget;

	private GitWeb browser;

	private Collection<SubmoduleConfig> submoduleCfg;

	public Collection<SubmoduleConfig> getSubmoduleCfg() {
		return submoduleCfg;
	}

	public void setSubmoduleCfg(Collection<SubmoduleConfig> submoduleCfg) {
		this.submoduleCfg = submoduleCfg;
	}

	@DataBoundConstructor
	public GitSCM(String source, String branch, boolean doMerge,
			boolean doGenerateSubmoduleConfigurations, String mergeTarget,
			List<RemoteRepository> repositories,
			Collection<SubmoduleConfig> submoduleCfg, GitWeb browser) {
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
	 * Gets the source repository path. Either URL or local file path.
	 */
	public String getSource() {
		return source;
	}

	/**
	 * In-repository branch to follow. Null indicates "default".
	 */
	public String getBranch() {
		return branch == null ? "" : branch;
	}

	@Override
	public GitWeb getBrowser() {
		return browser;
	}

	public List<RemoteRepository> getRepositories() {
		// Handle null-value to ensure backwards-compatibility, ie project configuration missing the <repositories/> XML element
		if (repositories == null)
			return new ArrayList<RemoteRepository>();
		return repositories;
	}

	@Override
	public boolean pollChanges(AbstractProject project, Launcher launcher,
			FilePath workspace, final TaskListener listener)
			throws IOException, InterruptedException {

		final String gitExe = getDescriptor().getGitExe();

		boolean pollChangesResult = workspace.act(new FileCallable<Boolean>() {
			public Boolean invoke(File workspace, VirtualChannel channel)
					throws IOException {

				IGitAPI git = new GitAPI(gitExe, new FilePath(workspace), listener);

				listener.getLogger().println("Poll for changes");

				if (git.hasGitRepo()) {
					// Repo is there - do a fetch
					listener.getLogger().println("Update repository");
					// return true if there are changes, false if not

					// Get from 'Main' repo
					git.fetch();

					for (RemoteRepository remoteRepository : getRepositories()) {
						fetchFrom(git, workspace, listener, remoteRepository);
					}

					// Find out if there are any changes from there to now
					return branchesThatNeedBuilding(git, workspace, listener)
							.size() > 0;
				} else {
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
	 */
	private void fetchFrom(IGitAPI git, File workspace, TaskListener listener,
			RemoteRepository remoteRepository) {
		try {
			git.fetch(remoteRepository.getUrl(), remoteRepository.getRefspec());

			List<IndexEntry> submodules = new GitUtils(listener, git)
					.getSubmodules("HEAD");

			for (IndexEntry submodule : submodules) {
				try {
					RemoteRepository submoduleRemoteRepository = remoteRepository
							.getSubmoduleRepository(submodule.getFile());

					File subdir = new File(workspace, submodule.getFile());
					IGitAPI subGit = new GitAPI(git.getGitExe(), new FilePath(subdir),
							listener);

					subGit.fetch(submoduleRemoteRepository.getUrl(),
							submoduleRemoteRepository.getRefspec());
				} catch (GitException ex) {
					listener
							.getLogger()
							.println(
									"Problem fetching from "
											+ remoteRepository.getName()
											+ " - could be unavailable. Continuing anyway");
				}

			}
		} catch (GitException ex) {
			listener.getLogger().println(
					"Problem fetching from " + remoteRepository.getName()
							+ " / " + remoteRepository.getUrl()
							+ " - could be unavailable. Continuing anyway");
		}

	}

	private Collection<Revision> filterBranches(Collection<Revision> branches) {
		if (this.branch == null || this.branch.length() == 0)
			return branches;
		Set<Revision> interesting = new HashSet<Revision>();
		for (Revision r : branches) {
			for (Branch b : r.getBranches()) {
				if (!b.getName().equals(this.branch))
					interesting.add(r);
			}
		}
		return interesting;
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
	public boolean checkout(AbstractBuild build, Launcher launcher,
			FilePath workspace, final BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {

		final String projectName = build.getProject().getName();
		final int buildNumber = build.getNumber();
		final String gitExe = getDescriptor().getGitExe();

		final String buildnumber = "hudson-" + projectName + "-" + buildNumber;

		final Revision revToBuild = workspace.act(new FileCallable<Revision>() {
			public Revision invoke(File workspace, VirtualChannel channel)
					throws IOException {
				IGitAPI git = new GitAPI(gitExe, new FilePath(workspace), listener);

				if (git.hasGitRepo()) {
					// It's an update

					listener.getLogger().println("Checkout (update)");

					git.fetch();

					for (RemoteRepository remoteRepository : getRepositories()) {
						try {
							git.fetch(remoteRepository.getUrl(),
									remoteRepository.getRefspec());
						} catch (GitException ex) {
							listener
									.getLogger()
									.println(
											"Problem fetching from "
													+ remoteRepository
															.getName()
													+ " - could be unavailable. Continuing anyway");
						}
					}

				} else {
					listener.getLogger().println("Checkout (clone)");
					git.clone(source);
					if (git.hasGitModules()) {
						git.submoduleInit();
						git.submoduleUpdate();
					}
				}

				Set<Revision> toBeBuilt = branchesThatNeedBuilding(git,
						workspace, listener);

				String log = "Candidate revisions to be built: ";
				for (Revision b : toBeBuilt)
					log += b.toString() + "; ";
				listener.getLogger().println(log);

				Revision revToBuild = null;

				if (toBeBuilt.size() == 0) {
					revToBuild = new Revision(git.revParse("HEAD"));
					listener.getLogger().println(
							"Nothing to do (no unbuilt branches) - rebuilding HEAD "
									+ revToBuild);
				} else {
					revToBuild = toBeBuilt.iterator().next();
				}
				return revToBuild;
			}
		});

		String changeLog;

		if (doMerge) {
			if (!revToBuild.containsBranchName(getMergeTarget())) {
				changeLog = workspace.act(new FileCallable<String>() {
					public String invoke(File workspace, VirtualChannel channel)
							throws IOException {
						IGitAPI git = new GitAPI(gitExe, new FilePath(workspace), listener);

						// Do we need to merge this revision onto MergeTarget

						// Only merge if there's a branch to merge that isn't
						// us..
						listener.getLogger().println(
								"Merging " + revToBuild + " onto "
										+ getMergeTarget());

						// checkout origin/blah
						git.checkout(getRemoteMergeTarget());

						try {
							git.merge(revToBuild.getSha1());
						} catch (Exception ex) {
							listener
									.getLogger()
									.println(
											"Branch not suitable for integration as it does not merge cleanly");

							// We still need to tag something to prevent
							// repetitive builds from happening - tag the
							// candidate
							// branch.
							git.checkout(revToBuild.getSha1());

							git
									.tag(buildnumber, "Hudson Build #"
											+ buildNumber);

							return null;
						}

						if (git.hasGitModules()) {
							git.submoduleUpdate();
						}
						// Tag the successful merge
						git.tag(buildnumber, "Hudson Build #" + buildNumber);

						String lastRevWas = whenWasBranchLastBuilt(git,
								revToBuild.getSha1(), workspace, listener);

						// Fetch the diffs into the changelog file
						return putChangelogDiffsIntoFile(git, lastRevWas, git
								.revParse("HEAD"));

					}
				});

				return changeLogResult(changeLog, changelogFile);
			}
		}

		// No merge

		changeLog = workspace.act(new FileCallable<String>() {
			public String invoke(File workspace, VirtualChannel channel)
					throws IOException {
				IGitAPI git = new GitAPI(gitExe, new FilePath(workspace), listener);
				// Straight compile-the-branch
				listener.getLogger().println("Checking out " + revToBuild);
				git.checkout(revToBuild.getSha1());

				// if( compileSubmoduleCompares )
				if (doGenerateSubmoduleConfigurations) {
					SubmoduleCombinator combinator = new SubmoduleCombinator(
							git, listener, workspace, submoduleCfg);
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

					for (RemoteRepository remoteRepository : getRepositories()) {
						fetchFrom(git, workspace, listener, remoteRepository);
					}

					// Update to the correct checkout
					git.submoduleUpdate();

				}

				String lastRevWas = whenWasBranchLastBuilt(git, revToBuild
						.getSha1(), workspace, listener);

				git.tag(buildnumber, "Hudson Build #" + buildNumber);

				if (lastRevWas == null) {
					// No previous revision has been built - don't generate
					// every
					// single change in existence.
					return "First Time Build of Branch.";

				} else {
					// Fetch the diffs into the changelog file
					return putChangelogDiffsIntoFile(git, lastRevWas,
							revToBuild.getSha1());
				}

			}
		});

		return changeLogResult(changeLog, changelogFile);

	}

	/**
	 * Are there any branches that haven't been built?
	 * 
	 * @return SHA1 id of the branch that requires building, or NULL if none are
	 *         found.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private Set<Revision> branchesThatNeedBuilding(IGitAPI git, File workspace,
			TaskListener listener) throws IOException {

		// These are the set of things tagged that we have tried to build
		Set<String> setOfThingsBuilt = new HashSet<String>();
		Set<Revision> branchesThatNeedBuilding = new HashSet<Revision>();

		for (Tag tag : git.getTags()) {
			if (tag.getName().startsWith("hudson"))
				setOfThingsBuilt.add(tag.getCommitSHA1());
		}

		for (Revision revision : filterBranches(new GitUtils(listener, git)
				.getTipBranches())) {
			if (!setOfThingsBuilt.contains(revision.getSha1()))
				branchesThatNeedBuilding.add(revision);
		}

		// There could be a relationship between branches - so master may have
		// merged br1, so br1
		// hasn't been built, but master has.

		// If there's any other branch in the list needing building that
		// contains a branch, remove it

		return (branchesThatNeedBuilding);
	}

	private static String whenWasBranchLastBuilt(IGitAPI git, String branchId,
			File workspace, TaskListener listener) throws IOException {

		Set<String> setOfThingsBuilt = new HashSet<String>();

		for (Tag tag : git.getTags()) {
			if (tag.getName().startsWith("hudson"))
				setOfThingsBuilt.add(tag.getCommitSHA1());
		}

		if (setOfThingsBuilt.isEmpty()) {
			return null;
		}

		for (String rev : git.revListBranch(branchId)) {
			if (setOfThingsBuilt.contains(rev))
				return rev;
		}
		
		return null;
	}

	private String putChangelogDiffsIntoFile(IGitAPI git, String revFrom,
			String revTo) throws IOException {
		ByteArrayOutputStream fos = new ByteArrayOutputStream();
		// fos.write("<data><![CDATA[".getBytes());
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
			List<RemoteRepository> remoteRepositories = new ArrayList<RemoteRepository>();

			String[] names = req.getParameterValues("git.repo.name");
			String[] urls = req.getParameterValues("git.repo.url");
			String[] refs = req.getParameterValues("git.repo.refspec");
			if (names != null) {
				for (int i = 0; i < names.length; i++) {
					remoteRepositories.add(new RemoteRepository(names[i],
							urls[i], refs[i]));
				}
			}

			Collection<SubmoduleConfig> submoduleCfg = new ArrayList<SubmoduleConfig>();
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

			return new GitSCM(req.getParameter("git.source"), req
					.getParameter("git.branch"),
					req.getParameter("git.merge") != null, req
							.getParameter("git.generate") != null, req
							.getParameter("git.mergeTarget"),
					remoteRepositories, submoduleCfg, RepositoryBrowsers
							.createInstance(GitWeb.class, req, "git.browser"));
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

	public boolean getDoMerge() {
		return doMerge;
	}

	public boolean getDoGenerate() {
		return this.doGenerateSubmoduleConfigurations;
	}

	public String getMergeTarget() {
		return mergeTarget;
	}

	public String getRemoteMergeTarget() {
		return "origin/" + mergeTarget;
	}

}
