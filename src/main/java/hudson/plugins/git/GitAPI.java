package hudson.plugins.git;

import hudson.FilePath;
import hudson.Launcher;
import hudson.FilePath.FileCallable;
import hudson.Launcher.LocalLauncher;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

public class GitAPI implements IGitAPI {
	Launcher launcher;
	FilePath workspace;
	TaskListener listener;
	String gitExe;

	public GitAPI(String gitExe, FilePath workspace,
			TaskListener listener) {
		
		this.workspace = workspace;
		this.listener = listener;
		this.gitExe = gitExe;
		
		launcher = new LocalLauncher(listener);
		
	}
	
	public String getGitExe() {
		return gitExe;
	}

	public boolean hasGitRepo() throws GitException {
		try {

			FilePath dotGit = workspace.child(".git");

			return dotGit.exists();

		} catch (SecurityException ex) {
			throw new GitException(
					"Security error when trying to check for .git. Are you sure you have correct permissions?",
					ex);
		} catch (Exception e) {
			throw new GitException("Couldn't check for .git", e);
		}
	}

	public boolean hasGitModules() throws GitException {
		try {

			FilePath dotGit = workspace.child(".gitmodules");

			return dotGit.exists();

		} catch (SecurityException ex) {
			throw new GitException(
					"Security error when trying to check for .gitmodules. Are you sure you have correct permissions?",
					ex);
		} catch (Exception e) {
			throw new GitException("Couldn't check for .gitmodules");
		}
	}

	public void fetch(String repository, String refspec) throws GitException {
		listener.getLogger().println(
				"Fetching upstream changes"
						+ (repository != null ? " from " + repository : ""));

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "fetch");

		if (repository != null) {
			args.add(repository);
			if (refspec != null)
				args.add(refspec);
		}

		try {
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(),
					listener.getLogger(), workspace).join() != 0) {
				throw new GitException("Failed to fetch");
			}
		} catch (IOException e) {
			throw new GitException("Failed to fetch", e);
		} catch (InterruptedException e) {
			throw new GitException("Failed to fetch", e);
		}

	}

	public void fetch() throws GitException {
		fetch(null, null);
	}

	/**
	 * Start from scratch and clone the whole repository. Cloning into an
	 * existing directory is not allowed, so the workspace is first deleted
	 * entirely, then <tt>git clone</tt> is performed.
	 */
	public void clone(final String source) throws GitException {
		listener.getLogger().println("Cloning repository " + source);

		// TODO: Not here!
		try {
			workspace.deleteRecursive();
		} catch (Exception e) {
			e.printStackTrace(listener.error("Failed to clean the workspace"));
			throw new GitException("Failed to delete workspace", e);
		}

		try {
			workspace.act(new FileCallable<String>() {
				public String invoke(File workspace,
						VirtualChannel channel) throws IOException {
					final ArgumentListBuilder args = new ArgumentListBuilder();
					args.add(getGitExe(), "clone");
					args.add(source);
					args.add(workspace.toString());
					return launchCommandIn(args.toCommandArray(), null);
				}
			});
		} catch (Exception e) {
			throw new GitException("Could not clone " + source, e);
		}
	}

	public String revParse(String revName) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "rev-parse", revName);
		String result = launchCommand(args.toCommandArray());
		return firstLine(result).trim();
	}
	
	private String firstLine(String result) {
		BufferedReader reader = new BufferedReader(new StringReader(result));
		String line;
		try {
			line = reader.readLine();
			if (line == null)
				return null;
			if (reader.readLine() != null)
				throw new GitException("Result has multiple lines");
		} catch (IOException e) {
			throw new GitException("Error parsing result");
		}
		
		return line;
	}

	private void log(String revFrom, String revTo, OutputStream fos, String...extraargs)
	throws GitException {
		String revSpec;
		if (revFrom == null) {
			revSpec = revTo;
		} else {
			revSpec = revFrom + ".." + revTo;
		}
		// Find the changes between our current working copy and now
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "log");
		args.add(extraargs);
		args.add(revSpec);

		try {

			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error launching git log");
			}

		} catch (Exception e) {
			throw new GitException("Error performing git log", e);
		}
	}

	public void changelog(String revFrom, String revTo, OutputStream fos)
			throws GitException {
		// git log --numstat -M --summary --pretty=raw HEAD..origin
		log(revFrom, revTo, fos, "--numstat", "-M", "--summary", "--pretty=raw");
	}

	/**
	 * Merge any changes into the head.
	 */
	public void merge(String revSpec) throws GitException {

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "merge", revSpec);

		try {
			launchCommand(args.toCommandArray());
		} catch (GitException e) {
			throw new GitException("Could not merge " + revSpec, e);
		}
	}

	/**
	 * Init submodules.
	 */
	public void submoduleInit() throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "submodule", "init");

		launchCommand(args.toCommandArray());
	}

	/**
	 * Update submodules.
	 */
	public void submoduleUpdate() throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "submodule", "update");

		launchCommand(args.toCommandArray());
	}

	protected final Map<String, String> createEnvVarMap() {
		Map<String, String> env = new HashMap<String, String>();

		return env;
	}

	public void tag(String tagName, String comment) throws GitException {
		tagName = tagName.replace(' ', '_');
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "tag", "-a", "-f", tagName, "-m", comment);

		try {
			launchCommand(args.toCommandArray());
		} catch (GitException e) {
			throw new GitException("Could not apply tag " + tagName, e);
		}
	}

	/**
	 * Launch command using the workspace as working directory
	 * @param args
	 * @return command output
	 * @throws GitException
	 */
	private String launchCommand(String[] args) throws GitException {
		return launchCommandIn(args, workspace);
	}

	/**
	 * @param args
	 * @param workDir
	 * @return command output
	 * @throws GitException
	 */
	private String launchCommandIn(String[] args, FilePath workDir) throws GitException {
		ByteArrayOutputStream fos = new ByteArrayOutputStream();

		try {
			int status = launcher.launch(args,
					createEnvVarMap(), fos, workDir).join();
	
			String result = fos.toString();

			if (status != 0) {
				throw new GitException("Command returned status code " + status + ": " + result);
			}

			return result;
		} catch (Exception e) {
			throw new GitException("Error performing " + StringUtils.join(args, " "), e);
		}
	}

	public void push(String refspec) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "push", "--tags", "origin");

		if (refspec != null)
			args.add(refspec);

		launchCommand(args.toCommandArray());
		// Ignore output for now as there's many different formats
		// That are possible.
	}

	private List<Branch> parseBranches(String fos) throws GitException {
		List<Branch> tags = new ArrayList<Branch>();

		BufferedReader rdr = new BufferedReader(new StringReader(fos));
		String line;
		try {
			while ((line = rdr.readLine()) != null) {
				// Ignore the 1st
				line = line.substring(2);
				// Ignore '(no branch)'
				if (!line.startsWith("(")) {
					tags.add(new Branch(line, revParse(line)));
				}
			}
		} catch (IOException e) {
			throw new GitException("Error parsing branches", e);
		}

		return tags;
	}

	public List<Branch> getBranches() throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "branch", "-a");
		return parseBranches(launchCommand(args.toCommandArray()));
	}

	public List<Branch> getBranchesContaining(String revspec)
			throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "branch", "-a", "--contains", revspec);
		return parseBranches(launchCommand(args.toCommandArray()));
	}

	public void checkout(String ref) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "checkout", "-f", ref);

		try {
			launchCommand(args.toCommandArray());
		} catch (GitException e) {
			throw new GitException("Could not checkout " + ref, e);
		}
	}

	public void deleteTag(String tagName) throws GitException {
		tagName = tagName.replace(' ', '_');
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "tag", "-d", tagName);

		try {
			launchCommand(args.toCommandArray());
		} catch (GitException e) {
			throw new GitException("Could not delete tag " + tagName, e);
		}
	}

	public List<IndexEntry> lsTree(String treeIsh) throws GitException {
		List<IndexEntry> entries = new ArrayList<IndexEntry>();
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "ls-tree", treeIsh);
		String result = launchCommand(args.toCommandArray());

		BufferedReader rdr = new BufferedReader(new StringReader(result));
		String line;
		try {
			while ((line = rdr.readLine()) != null) {
				String[] entry = line.split("\\s+");
				entries.add(new IndexEntry(entry[0], entry[1], entry[2],
						entry[3]));
			}
		} catch (IOException e) {
			throw new GitException("Error parsing ls tree", e);
		}

		return entries;
	}

	public List<String> revListAll() throws GitException {
		return revList("--all");
	}

	public List<String> revListBranch(String branchId) throws GitException {
		return revList(branchId);
	}

	public List<String> revList(String...extraArgs) throws GitException {
		List<String> entries = new ArrayList<String>();
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "rev-list");
		args.add(extraArgs);
		String result = launchCommand(args.toCommandArray());
		BufferedReader rdr = new BufferedReader(new StringReader(result));
		String line;

		try {
			while ((line = rdr.readLine()) != null) {
				// Add the SHA1
				entries.add(line);
			}
		} catch (IOException e) {
			throw new GitException("Error parsing rev list", e);
		}

		return entries;
	}

	public void add(String filePattern) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "add", filePattern);

		try {
			launchCommand(args.toCommandArray());
		} catch (GitException e) {
			throw new GitException("Cannot add " + filePattern, e);
		}
	}

	public void branch(String name) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "branch", name);

		try {
			launchCommand(args.toCommandArray());
		} catch (GitException e) {
			throw new GitException("Cannot create branch " + name, e);
		}
	}

	public void commit(File f) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "commit", "-F", f.getAbsolutePath());

		try {
			launchCommand(args.toCommandArray());
		} catch (GitException e) {
			throw new GitException("Cannot commit " + f, e);
		}
	}
}
