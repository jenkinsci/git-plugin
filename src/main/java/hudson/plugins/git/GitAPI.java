package hudson.plugins.git;

import hudson.EnvVars;
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
import java.io.PrintStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.Tag;
import org.spearce.jgit.transport.RemoteConfig;

public class GitAPI implements IGitAPI {

    Launcher launcher;
    FilePath workspace;
    TaskListener listener;
    String gitExe;
    EnvVars environment;

    public GitAPI(String gitExe, FilePath workspace,
                  TaskListener listener, EnvVars environment) {

        //listener.getLogger().println("Git API @ " + workspace.getName() + " / " + workspace.getRemote() + " - " + workspace.getChannel());

        this.workspace = workspace;
        this.listener = listener;
        this.gitExe = gitExe;
        this.environment = environment;
        PrintStream log = listener.getLogger();
        log.println("GitAPI created");
        for (Map.Entry<String, String> ent : environment.entrySet()) {
            //log.println("Env: " + ent.getKey() + "=" + ent.getValue());
        }

        launcher = new LocalLauncher(listener);

    }

    public String getGitExe() {
        return gitExe;
    }

    public EnvVars getEnvironment() {
        return environment;
    }

    public void init() throws GitException {
        if (hasGitRepo()) {
            throw new GitException(".git directory already exists! Has it already been initialised?");
        }
        try {
            final Repository repo = new Repository(new File(workspace.child(".git").getRemote()));
            repo.create();
        } catch (IOException ioe) {
            throw new GitException("Error initiating git repo.", ioe);
        }
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
            throw new GitException("Couldn't check for .gitmodules", e);
        }
    }

    public void fetch(String repository, String refspec) throws GitException {
        listener.getLogger().println(
                                     "Fetching upstream changes"
                                     + (repository != null ? " from " + repository : ""));

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getGitExe(), "fetch", "-t");

        if (repository != null) {
            args.add(repository);
            if (refspec != null)
                args.add(refspec);
        }

        try {
            if (launcher.launch().cmds(args).
                envs(environment).stdout(listener.getLogger()).pwd(workspace).join() != 0) {
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
     *
     * @param remoteConfig remote config
     * @throws GitException if deleting or cloning the workspace fails
     */
    public void clone(final RemoteConfig remoteConfig) throws GitException {
        listener.getLogger().println("Cloning repository " + remoteConfig.getName());

        // TODO: Not here!
        try {
            workspace.deleteRecursive();
        } catch (Exception e) {
            e.printStackTrace(listener.error("Failed to clean the workspace"));
            throw new GitException("Failed to delete workspace", e);
        }

        // Assume only 1 URL for this repository
        final String source = remoteConfig.getURIs().get(0).toString();

        try {
            workspace.act(new FileCallable<String>() {

                    private static final long serialVersionUID = 1L;

                    public String invoke(File workspace,
                                         VirtualChannel channel) throws IOException {
                        final ArgumentListBuilder args = new ArgumentListBuilder();
                        args.add("clone");
                        args.add("-o", remoteConfig.getName());
                        args.add(source);
                        args.add(workspace.getAbsolutePath());
                        return launchCommandIn(args, null);
                    }
                });
        } catch (Exception e) {
            throw new GitException("Could not clone " + source, e);
        }
    }

    public void clean() throws GitException {
        launchCommand("clean", "-fdx");
    }

    public ObjectId revParse(String revName) throws GitException {
        String result = launchCommand("rev-parse", revName);
        return ObjectId.fromString(firstLine(result).trim());
    }

    public String describe(String commitIsh) throws GitException {
        String result = launchCommand("describe", "--tags", commitIsh);
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
            throw new GitException("Error parsing result", e);
        }

        return line;
    }

    public void changelog(String revFrom, String revTo, OutputStream outputStream) throws GitException {
        whatchanged(revFrom, revTo, outputStream, "--no-abbrev", "-M", "--pretty=raw");
    }

    private void whatchanged(String revFrom, String revTo, OutputStream outputStream, String... extraargs) throws GitException {
        String revSpec = revFrom + ".." + revTo;

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(getGitExe(), "whatchanged");
        args.add(extraargs);
        args.add(revSpec);

        try {
            if (launcher.launch().cmds(args).envs(environment).stdout(
                    outputStream).pwd(workspace).join() != 0) {
                throw new GitException("Error launching git whatchanged");
            }
        } catch (Exception e) {
            throw new GitException("Error performing git whatchanged", e);
        }
    }

    /**
     * Given a Revision, show it as if it were an entry from git whatchanged, so that it
     * can be parsed by GitChangeLogParser.
     *
     * @param r The Revision object
     * @return The git show output, in List form.
     * @throws GitException if errors were encountered running git show.
     */
    public List<String> showRevision(Revision r) throws GitException {
        String revName = r.getSha1String();
        String result = "";

        if (revName != null) {
            result = launchCommand("show", "--no-abbrev", "--format=raw", "-M", "--raw", revName);
        }

        List<String> revShow = new ArrayList<String>();

        if (result != null) {
            revShow = new ArrayList<String>(Arrays.asList(result.split("\\n")));
        }

        return revShow;
    }
    
    /**
     * Merge any changes into the head.
     *
     * @param revSpec the revision
     * @throws GitException if the emrge fails
     */
    public void merge(String revSpec) throws GitException {
        try {
            launchCommand("merge", revSpec);
        } catch (GitException e) {
            throw new GitException("Could not merge " + revSpec, e);
        }
    }

    /**
     * Init submodules.
     *
     * @throws GitException if executing the Git command fails
     */
    public void submoduleInit() throws GitException {
        launchCommand("submodule", "init");
    }

    /**
     * Sync submodule URLs
     */
    public void submoduleSync() throws GitException {
        // Check if git submodule has sync support.
        // Only available in git 1.6.1 and above
        launchCommand("submodule", "sync");
    }

    
    /**
     * Update submodules.
     *
     * @param recursive if true, will recursively update submodules (requires git>=1.6.5)
     * 
     * @throws GitException if executing the Git command fails
     */
    public void submoduleUpdate(boolean recursive) throws GitException {
    	ArgumentListBuilder args = new ArgumentListBuilder();
    	args.add("submodule", "update");
    	if (recursive) {
            args.add("--init", "--recursive");
        }
        
        launchCommand(args);
    }

    /**
     * Cleans submodules
     *
     * @param recursive if true, will recursively clean submodules (requres git>=1.6.5)
     *
     * @throws GitException if executing the git command fails
     */
    public void submoduleClean(boolean recursive) throws GitException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("submodule", "foreach");
    	if (recursive) {
            args.add("--recursive");
    	}
    	args.add("git clean -fdx");
    	
    	launchCommand(args);
    }

    public void tag(String tagName, String comment) throws GitException {
        tagName = tagName.replace(' ', '_');
        try {
            launchCommand("tag", "-a", "-f", "-m", comment, tagName);
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
    public String launchCommand(ArgumentListBuilder args) throws GitException {
        return launchCommandIn(args, workspace);
    }

    /**
     * Launch command using the workspace as working directory
     * @param args
     * @return command output
     * @throws GitException
     */
    public String launchCommand(String... args) throws GitException {
        return launchCommand(new ArgumentListBuilder(args));
    }

    /**
     * @param args
     * @param workDir
     * @return command output
     * @throws GitException
     */
    private String launchCommandIn(ArgumentListBuilder args, FilePath workDir) throws GitException {
        ByteArrayOutputStream fos = new ByteArrayOutputStream();

        try {
            args.prepend(getGitExe());
            int status = launcher.launch().cmds(args.toCommandArray()).
                envs(environment).stdout(fos).pwd(workDir).join();

            String result = fos.toString();

            if (status != 0) {
                throw new GitException("Command returned status code " + status + ": " + result);
            }

            return result;
        } catch (Exception e) {
            throw new GitException("Error performing " + StringUtils.join(args.toCommandArray(), " ")
                                   + "\n" + e.getMessage(), e);
        }
    }

    public void push(RemoteConfig repository, String refspec) throws GitException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("push", repository.getURIs().get(0).toString());

        if (refspec != null)
            args.add(refspec);

        launchCommand(args);
        // Ignore output for now as there's many different formats
        // That are possible.
    }

    private List<Branch> parseBranches(String fos) throws GitException {
        // TODO: git branch -a -v --abbrev=0 would do this in one shot..

        List<Branch> tags = new ArrayList<Branch>();

        BufferedReader rdr = new BufferedReader(new StringReader(fos));
        String line;
        try {
            while ((line = rdr.readLine()) != null) {
                // Ignore the 1st
                line = line.substring(2);
                // Ignore '(no branch)' or anything with " -> ", since I think
                // that's just noise
                if ((!line.startsWith("("))
                    && (line.indexOf(" -> ") == -1)) {
                    tags.add(new Branch(line, revParse(line)));
                }
            }
        } catch (IOException e) {
            throw new GitException("Error parsing branches", e);
        }

        return tags;
    }

    public List<Branch> getBranches() throws GitException {
        return parseBranches(launchCommand("branch", "-a"));
    }

    public List<Branch> getRemoteBranches() throws GitException, IOException {
        Repository db = getRepository();
        Map<String, Ref> refs = db.getAllRefs();
        List<Branch> branches = new ArrayList<Branch>();

        for(Ref candidate : refs.values()) {
            if(candidate.getName().startsWith(Constants.R_REMOTES)) {
                Branch buildBranch = new Branch(candidate);
                listener.getLogger().println("Seen branch in repository " + buildBranch.getName());
                branches.add(buildBranch);
            }
        }

        return branches;
    }

    public List<Branch> getBranchesContaining(String revspec)
        throws GitException {
        return parseBranches(launchCommand("branch", "-a", "--contains", revspec));
    }

    public void checkout(String ref) throws GitException {
        try {
            launchCommand("checkout", "-f", ref.toString());
        } catch (GitException e) {
            throw new GitException("Could not checkout " + ref, e);
        }
    }

    public void checkoutBranch(String branch, String ref) throws GitException {
        try {
            // First, checkout to detached HEAD, so we can delete the branch.
            checkout(ref);
            // Second, check to see if the branch actually exists, and then delete it if it does.
            for (Branch b : getBranches()) {
                if (b.name.equals(branch)) {
                    deleteBranch(branch);
                }
            }
            // Lastly, checkout the branch, creating it in the process, using ref as the start point.
            launchCommand("checkout", "-b", branch, ref.toString());
        } catch (GitException e) {
            throw new GitException("Could not checkout " + branch + " with start point " + ref, e);
        }
    }
    
    public boolean tagExists(String tagName) throws GitException {
        tagName = tagName.replace(' ', '_');

        if (launchCommand("tag", "-l", tagName).trim().equals(tagName)) {
            return true;
        }

        return false;
    }

    public void deleteBranch(String name) throws GitException {
        try {
            launchCommand("branch", "-D", name);
        } catch (GitException e) {
            throw new GitException("Could not delete branch " + name, e);
        }

    }
    
    public void deleteTag(String tagName) throws GitException {
        tagName = tagName.replace(' ', '_');
        try {
            launchCommand("tag", "-d", tagName);
        } catch (GitException e) {
            throw new GitException("Could not delete tag " + tagName, e);
        }
    }

    public List<IndexEntry> lsTree(String treeIsh) throws GitException {
        List<IndexEntry> entries = new ArrayList<IndexEntry>();
        String result = launchCommand("ls-tree", treeIsh);

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

    public List<ObjectId> revListAll() throws GitException {
        return revList("--all");
    }

    public List<ObjectId> revListBranch(String branchId) throws GitException {
        return revList(branchId);
    }

    public List<ObjectId> revList(String... extraArgs) throws GitException {
        List<ObjectId> entries = new ArrayList<ObjectId>();
        ArgumentListBuilder args = new ArgumentListBuilder("rev-list");
        args.add(extraArgs);
        String result = launchCommand(args);
        BufferedReader rdr = new BufferedReader(new StringReader(result));
        String line;

        try {
            while ((line = rdr.readLine()) != null) {
                // Add the SHA1
                entries.add(ObjectId.fromString(line));
            }
        } catch (IOException e) {
            throw new GitException("Error parsing rev list", e);
        }

        return entries;
    }

    public void add(String filePattern) throws GitException {
        try {
            launchCommand("add", filePattern);
        } catch (GitException e) {
            throw new GitException("Cannot add " + filePattern, e);
        }
    }

    public void branch(String name) throws GitException {
        try {
            launchCommand("branch", name);
        } catch (GitException e) {
            throw new GitException("Cannot create branch " + name, e);
        }
    }

    public void commit(File f) throws GitException {
        try {
            launchCommand("commit", "-F", f.getAbsolutePath());
        } catch (GitException e) {
            throw new GitException("Cannot commit " + f, e);
        }
    }

    public void fetch(RemoteConfig remoteRepository) throws GitException {
        // Assume there is only 1 URL / refspec for simplicity
        fetch(remoteRepository.getURIs().get(0).toString(), remoteRepository.getFetchRefSpecs().get(0).toString());

    }

    public ObjectId mergeBase(ObjectId id1, ObjectId id2) {
        try {
            String result;
            try {
                result = launchCommand("merge-base", id1.name(), id2.name());
            } catch (GitException ge) {
                return null;
            }


            BufferedReader rdr = new BufferedReader(new StringReader(result));
            String line;

            while ((line = rdr.readLine()) != null) {
                // Add the SHA1
                return ObjectId.fromString(line);
            }
        } catch (Exception e) {
            throw new GitException("Error parsing merge base", e);
        }

        return null;
    }

    public String getAllLogEntries(String branch) {
        return launchCommand("log", "--all", "--pretty=format:'%H#%ct'", branch);
    }

    private Repository getRepository() throws IOException {
        return new Repository(new File(workspace.getRemote(), ".git"));
    }

    public List<Tag> getTagsOnCommit(String revName) throws GitException, IOException {
        Repository db = getRepository();
        ObjectId commit = db.resolve(revName);
        List<Tag> ret = new ArrayList<Tag>();

        for (final Map.Entry<String, Ref> tag : db.getTags().entrySet()) {

            Tag ttag = db.mapTag(tag.getKey());
            if(ttag.getObjId().equals(commit)) {
                ret.add(ttag);
            }
        }
        return ret;

    }

    public Set<String> getTagNames(String tagPattern) throws GitException {
        try {
            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add(getGitExe(), "tag", "-l", tagPattern);

            ByteArrayOutputStream fos = new ByteArrayOutputStream();
            int status = launcher.launch().cmds(args).
                envs(environment).stdout(fos).pwd(workspace).join();
            String result = fos.toString();

            if (status != 0) {
                throw new GitException("Error retrieving tag names");
            }

            Set<String> tags = new HashSet<String>();
            BufferedReader rdr = new BufferedReader(new StringReader(result));
            String tag;
            while ((tag = rdr.readLine()) != null) {
                // Add the SHA1
                tags.add(tag);
            }
            return tags;
        } catch (Exception e) {
            throw new GitException("Error retrieving tag names", e);
        }
    }
}
