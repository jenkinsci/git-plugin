package hudson.plugins.git;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.model.TaskListener;
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

			File dotGit = new File(workspace.toURI().getPath(), ".git");

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

			File dotGit = new File(workspace.toURI().getPath(), ".gitmodules");

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
	 * Start from scratch and clone the whole repository.
	 */
	public void clone(String source) throws GitException {
		listener.getLogger().println("Cloning repository " + source);

		// TODO: Not here!
		try {
			workspace.deleteRecursive();
		} catch (Exception e) {
			e.printStackTrace(listener.error("Failed to clean the workspace"));
			throw new GitException("Failed to delete workspace", e);
		}

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "clone");

		try {
			args.add(source, workspace.toURI().getPath());
		} catch (IOException e1) {
			throw new GitException(e1);
		} catch (InterruptedException e1) {
			throw new GitException(e1);
		}
		int processResult = 0;

		try {
			processResult = launcher.launch(args.toCommandArray(),
					createEnvVarMap(), listener.getLogger(), null).join();
		} catch (IOException e) {
			throw new GitException("Error running clone", e);
		} catch (InterruptedException e) {
			throw new GitException("Error running clone", e);
		}

		if (processResult != 0) {
			throw new GitException("Clone returned an error code");
		}
	}

	public String revParse(String revName) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "rev-parse", revName);
		String result;

		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			int status = launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
				workspace).join();

			fos.close();
			result = fos.toString().trim();

			if (status != 0)
				throw new GitException("Error launching git rev-parse: " + result);

		} catch (Exception e) {
			throw new GitException("Error performing git rev-parse", e);
		}
		if (result.contains("fatal"))
			throw new GitException("Error fetching revision information "
					+ result);

		return result;
	}

	public void diff(OutputStream baos) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "diff", "--shortstat", "origin");

		try {

			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), baos,
					workspace).join() != 0) {
				throw new GitException("Failed to diff");
			}

		} catch (Exception e) {
			throw new GitException("Failed to diff", e);
		}

	}

	public void log(String revFrom, String revTo, OutputStream fos, String...extraargs)
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

		launch(args.toCommandArray(), "Error in merge");

	}

	private void launch(String[] args, String error) {
		try {
			if (launcher.launch(args, createEnvVarMap(), listener.getLogger(),
					workspace).join() != 0) {

				throw new GitException(error);
			}
		} catch (Exception e) {
			throw new GitException(error, e);
		}
	}

	/**
	 * Init submodules.
	 */
	public void submoduleInit() throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "submodule", "init");

		launch(args.toCommandArray(), "Error in submodule init");
	}

	/**
	 * Update submodules.
	 */
	public void submoduleUpdate() throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "submodule", "update");

		launch(args.toCommandArray(), "Error in submodule update");
	}

	protected final Map<String, String> createEnvVarMap() {
		Map<String, String> env = new HashMap<String, String>();

		return env;
	}

	public void tag(String tagName, String comment) throws GitException {
		tagName = tagName.replace(' ', '_');
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "tag", "-a", "-f", tagName, "-m", comment);

		launch(args.toCommandArray(), "Error in tag");
	}

	public List<Tag> getTags() throws GitException {
		List<Tag> tags = new ArrayList<Tag>();

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "tag", "-l");

		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				// Might not be any tags, so just return an empty set.
				return tags;
			}

			fos.close();
			BufferedReader rdr = new BufferedReader(new StringReader(fos
					.toString()));
			String line;
			while ((line = rdr.readLine()) != null) {
				Tag t = new Tag(line, revParse(line));
				t.setCommitSHA1(getTagCommit(t.getSHA1()));

				tags.add(t);
			}

			return tags;

		} catch (Exception e) {
			throw new GitException("Error performing git rev-parse", e);
		}
	}

	public void push(String refspec) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "push", "--tags", "origin");

		if (refspec != null)
			args.add(refspec);

		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error launching git push");
			}

			fos.close();
			// Ignore output for now as there's many different formats
			// That are possible.

		} catch (Exception e) {
			throw new GitException("Error performing git push", e);
		}
	}

	private List<Branch> parseBranches(String fos) throws IOException {
		List<Branch> tags = new ArrayList<Branch>();

		BufferedReader rdr = new BufferedReader(new StringReader(fos));
		String line;
		while ((line = rdr.readLine()) != null) {
			// Ignore the 1st
			line = line.substring(2);
			// Ignore '(no branch)'
			if (!line.startsWith("(")) {
				tags.add(new Branch(line, revParse(line)));
			}
		}

		return tags;
	}

	public List<Branch> getBranches() throws GitException {

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "branch", "-a");

		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error launching git branch");
			}

			fos.close();

			return parseBranches(fos.toString());

		} catch (Exception e) {
			throw new GitException("Error performing git branch", e);
		}
	}

	public List<Branch> getBranchesContaining(String revspec)
			throws GitException {
		List<Branch> tags = new ArrayList<Branch>();

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "branch", "-a", "--contains", revspec);

		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				// No items : return empty set
				return tags;
			}

			fos.close();

			return parseBranches(fos.toString());

		} catch (Exception e) {
			throw new GitException("Error performing git branch", e);
		}
	}

	public void checkout(String ref) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "checkout", "-f", ref);

		launch(args.toCommandArray(), "Error in checkout");

	}

	public void deleteTag(String tagName) throws GitException {
		tagName = tagName.replace(' ', '_');
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "tag", "-d", tagName);

		launch(args.toCommandArray(), "Error in deleteTag");

	}

	private String getTagCommit(String tagName) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "cat-file", "-p", tagName);

		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error executing cat-file");
			}

			fos.close();
			BufferedReader rdr = new BufferedReader(new StringReader(fos
					.toString()));
			String line;
			while ((line = rdr.readLine()) != null) {
				if (line.startsWith("object"))
					return line.substring(7);
			}

			return null;

		} catch (Exception e) {
			throw new GitException("Error performing git cat-file", e);
		}
	}

	public List<IndexEntry> lsTree(String treeIsh) throws GitException {
		List<IndexEntry> entries = new ArrayList<IndexEntry>();
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "ls-tree", treeIsh);

		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error executing ls-tree");
			}

			fos.close();
			BufferedReader rdr = new BufferedReader(new StringReader(fos
					.toString()));
			String line;
			while ((line = rdr.readLine()) != null) {
				String[] entry = line.split("\\s+");
				entries.add(new IndexEntry(entry[0], entry[1], entry[2],
						entry[3]));
			}

			return entries;

		} catch (Exception e) {
			throw new GitException("Error performing git ls-tree", e);
		}
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

		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error executing rev-list");
			}

			fos.close();
			BufferedReader rdr = new BufferedReader(new StringReader(fos
					.toString()));
			String line;
			while ((line = rdr.readLine()) != null) {
				// Add the SHA1
				entries.add(line);

			}

			return entries;

		} catch (Exception e) {
			throw new GitException("Error performing git rev-list", e);
		}
	}

	public void add(String filePattern) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "add", filePattern);

		launch(args.toCommandArray(), "Error in add");

	}

	public void branch(String name) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "branch", name);

		launch(args.toCommandArray(), "Error in branch");

	}

	public void commit(String comment) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "commit", "-m", comment);

		launch(args.toCommandArray(), "Error in commit");

	}

	public void commit(File f) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "commit", "-F", f.getAbsolutePath());

		launch(args.toCommandArray(), "Error in commit");

	}

}
