package hudson.plugins.git;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

	public String revParse(String revName) throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "rev-parse", revName);
		String result;
		
		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error launching git rev-parse");
			}

			fos.close();
			result = fos.toString().trim();
			
		} catch (Exception e) {
			throw new GitException("Error performing git rev-parse", e);
		}
		if( result.contains("fatal") )
			throw new GitException("Error fetching revision information " + result);
		
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

	public void log(String revFrom, String revTo, OutputStream fos) throws GitException {
		// git log --numstat -M --summary --pretty=raw HEAD..origin

		String revSpec;
		if( revFrom == null )
		{
			revSpec = revTo;
		}
		else
		{
			revSpec = revFrom + ".." + revTo;
		}
		// Find the changes between our current working copy and now
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "log", "--numstat", "-M", "--summary",
				"--pretty=raw", revSpec);

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

	public void tag(String tagName, String comment) throws GitException
	{
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
				throw new GitException("Error launching git rev-parse");
			}

			fos.close();
			BufferedReader rdr = new BufferedReader(new StringReader(fos.toString()));
			String line;
			while((line = rdr.readLine()) != null)
			{
				Tag t = new Tag(line, revParse(line));
				t.setCommitSHA1(getTagCommit(t.getSHA1()));
				
				tags.add( t );
			}
			
			return tags;
			
		} catch (Exception e) {
			throw new GitException("Error performing git rev-parse", e);
		}
	}

	public void push() throws GitException {
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "push", "--tags");
		
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

	public List<Branch> getBranches() throws GitException {
		List<Branch> tags = new ArrayList<Branch>();
		
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "branch", "-a");
		
		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error launching git branch");
			}

			fos.close();
			BufferedReader rdr = new BufferedReader(new StringReader(fos.toString()));
			String line;
			while((line = rdr.readLine()) != null)
			{
				// Ignore the 1st 
				line = line.substring(2);
				// Ignore '(no branch)'
				if( !line.startsWith("(") )
				{
					tags.add( new Branch(line, revParse(line)));
				}
			}
			
			return tags;
			
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
	
	private String getTagCommit(String tagName) throws GitException 
	{
		ArgumentListBuilder args = new ArgumentListBuilder();
		args.add(getGitExe(), "cat-file", "-p", tagName);
		
		try {
			ByteArrayOutputStream fos = new ByteArrayOutputStream();
			if (launcher.launch(args.toCommandArray(), createEnvVarMap(), fos,
					workspace).join() != 0) {
				throw new GitException("Error executing cat-file");
			}

			fos.close();
			BufferedReader rdr = new BufferedReader(new StringReader(fos.toString()));
			String line;
			while((line = rdr.readLine()) != null)
			{
				if( line.startsWith("object") )
					return line.substring(7);
			}
			
			return null;
			
		} catch (Exception e) {
			throw new GitException("Error performing git cat-file", e);
		}
	}

}
