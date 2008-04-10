package hudson.plugins.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import hudson.FilePath;
import hudson.Launcher;
import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;

public class GitAPI implements IGitAPI {
	Launcher launcher;
	FilePath workspace;
	TaskListener listener;
	String gitExe;

	public GitAPI(String gitExe, Launcher launcher, FilePath workspace,
			TaskListener listener) {
		this.launcher = launcher;
		this.workspace = workspace;
		this.listener = listener;
		this.gitExe = gitExe;
	}

	public String getGitExe() {
		return gitExe;
	}

	public boolean hasGitRepo() throws GitException
	{
		try {
			return workspace.act(new FileCallable<Boolean>() {
			    public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
			        
			    	File dotGit = new File(ws, ".git");
			    	
			    	return dotGit.exists();
			    }
			});
		} catch (Exception e) {
			throw new GitException("Couldn't check for .git");
		}
	}
	
	public boolean hasGitModules() throws GitException
	{
		try {
			return workspace.act(new FileCallable<Boolean>() {
			    public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
			        
			    	File dotGit = new File(ws, ".gitmodules");
			    	
			    	return dotGit.exists();
			    }
			});
		} catch (Exception e) {
			throw new GitException("Couldn't check for .gitmodules");
		}
	}
	
	public void fetch() throws GitException {
		listener.getLogger().println("Fetching upstream changes");

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "fetch");

		try {
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(),
					listener.getLogger(), workspace).join() != 0) {
				throw new GitException("Failed to fetch");
			}
		} catch (Exception e) {
			throw new GitException("Failed to fetch", e);
		}
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

		args.add(source, workspace.getRemote());
		try {
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(),
					listener.getLogger(), null).join() != 0) {
				throw new GitException("Clone returned an error code");
			}
		} catch (Exception e) {
			throw new GitException("Failed to clone " + source);

		}

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

	public void log(OutputStream fos) throws GitException {
		// git log --numstat -M --summary --pretty=raw HEAD..origin

		// Find the changes between our current working copy and now
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "log", "--numstat", "-M", "--summary",
				"--pretty=raw", "HEAD..origin");

		try {

			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error launching git log");
			}

		} catch (Exception e) {
			throw new GitException("Error performing git log", e);
		}
	}

	/**
	 * Merge any changes into the head.
	 */
	public void merge() throws GitException {

		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "merge", "origin");

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

		launch(args.toCommandArray(), "Error in submodule init");
	}

	protected final Map<String, String> createEnvVarMap() {
		Map<String, String> env = new HashMap<String, String>();

		return env;
	}

}
