package hudson.plugins.git;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Functions;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.RemoteConfig;

public class GitAPI implements IGitAPI {

    Launcher launcher;
    FilePath workspace;
    TaskListener listener;
    String gitExe;
    EnvVars environment;
    String reference;

    public GitAPI(String gitExe, FilePath workspace,
                  TaskListener listener, EnvVars environment) {
        this(gitExe, workspace, listener, environment, null);
    }

    public GitAPI(String gitExe, FilePath workspace,
                  TaskListener listener, EnvVars environment, String reference) {

        //listener.getLogger().println("Git API @ " + workspace.getName() + " / " + workspace.getRemote() + " - " + workspace.getChannel());

        this.workspace = workspace;
        this.listener = listener;
        this.gitExe = gitExe;
        this.environment = environment;
        this.reference = reference;

        launcher = new LocalLauncher(GitSCM.VERBOSE?listener:TaskListener.NULL);
    }

    public String getGitExe() {
        return gitExe;
    }

    public EnvVars getEnvironment() {
        return environment;
    }

    public String getReference() {
        return reference;
    }

    private int[] getGitVersion() {
        int minorVer = 1;
        int majorVer = 6;

        try {
            String v = firstLine(launchCommand("--version")).trim();
            Pattern p = Pattern.compile("git version ([0-9]+)\\.([0-9+])\\..*");
            Matcher m = p.matcher(v);
            if (m.matches() && m.groupCount() >= 2) {
                try {
                    majorVer = Integer.parseInt(m.group(1));
                    minorVer = Integer.parseInt(m.group(2));
                } catch (NumberFormatException e) { }
            }
        } catch(GitException ex) {
            listener.getLogger().println("Error trying to determine the git version: " + ex.getMessage());
            listener.getLogger().println("Assuming 1.6");
        }

        return new int[]{majorVer,minorVer};
    }

    public void init() throws GitException {
        if (hasGitRepo()) {
            throw new GitException(".git directory already exists! Has it already been initialised?");
        }
        try {
			final Repository repo = new FileRepository(new File(workspace
					.child(Constants.DOT_GIT).getRemote()));
            repo.create();
        } catch (IOException ioe) {
            throw new GitException("Error initiating git repo.", ioe);
        }
    }

    public boolean hasGitRepo() throws GitException {
        if( hasGitRepo(".git") )
        {
            // Check if this is actually a valid git repo by parsing the HEAD revision. If it's duff, this will
            // fail.
            try
            {
                validateRevision("HEAD");
            }
            catch(Exception ex)
            {
                ex.printStackTrace(listener.error("Workspace has a .git repository, but it appears to be corrupt."));
                return false;
            }
            return true;
        }

        return false;
    }

    public boolean hasGitRepo( String GIT_DIR ) throws GitException {
        try {

            FilePath dotGit = workspace.child(GIT_DIR);

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

    public List<IndexEntry> getSubmodules( String treeIsh ) throws GitException {
        List<IndexEntry> submodules = lsTree(treeIsh);

        // Remove anything that isn't a submodule
        for (Iterator<IndexEntry> it = submodules.iterator(); it.hasNext();) {
            if (!it.next().getMode().equals("160000")) {
                it.remove();
            }
        }
        return submodules;
    }

    public boolean hasGitModules( String treeIsh ) throws GitException {
        return hasGitModules() && ( getSubmodules(treeIsh).size() > 0 );
    }

    public void fetch(String repository, String refspec) throws GitException {
        listener.getLogger().println(
                                     "Fetching upstream changes"
                                     + (repository != null ? " from " + repository : ""));

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("fetch", "-t");

        if (repository != null) {
            args.add(repository);
            if (refspec != null)
                args.add(refspec);
        }

        launchCommand(args);
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
        final int[] gitVer = getGitVersion();

        // TODO: Not here!
        try {
            workspace.deleteRecursive();
        } catch (Exception e) {
            e.printStackTrace(listener.error("Failed to clean the workspace"));
            throw new GitException("Failed to delete workspace", e);
        }

        // Assume only 1 URL for this repository
        final String source = remoteConfig.getURIs().get(0).toPrivateString();

        try {
            workspace.act(new FileCallable<String>() {

                    private static final long serialVersionUID = 1L;

                    public String invoke(File workspace,
                                         VirtualChannel channel) throws IOException {
                        final ArgumentListBuilder args = new ArgumentListBuilder();
                        args.add("clone");
                        if ((gitVer[0] >= 1) && (gitVer[1] >= 7)) {
                            args.add("--progress");
                        }
                        if (reference != null) {
                            File referencePath = new File(reference);
                            if (referencePath.exists() && referencePath.isDirectory()) {
                                args.add("--reference", reference);
                            }
                        }
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
        /*
            On Windows command prompt, '^' is an escape character (http://en.wikipedia.org/wiki/Escape_character#Windows_Command_Prompt)
            This isn't a problem if 'git' we are executing is git.exe, because '^' is a special character only for the command processor,
            but if 'git' we are executing is git.cmd (which is the case of msysgit), then the arguments we pass in here ends up getting
            processed by the command processor, and so 'xyz^{commit}' becomes 'xyz{commit}' and fails.

            Since we can't really tell if we are calling into git.exe or git.cmd, the best we can do for Windows
            is not to use '^{commit}'. This reverts 13f6038acc4fa5b5a62413155da6fc8cfcad3fe0
            and it will not dereference tags, but it's far better than having this method completely broken.

            See JENKINS-13007 where this blew up on Windows users.

            I filed https://github.com/msysgit/msysgit/issues/36 as a bug in msysgit.
         */
        String rpCommit = Functions.isWindows() ? "" : "^{commit}";
        String result = launchCommand("rev-parse", revName + rpCommit);
        return ObjectId.fromString(firstLine(result).trim());
    }

    public ObjectId validateRevision(String revName) throws GitException {
        String result = launchCommand("rev-parse", "--verify", revName);
        return ObjectId.fromString(firstLine(result).trim());
    }

    public String describe(String commitIsh) throws GitException {
        String result = launchCommand("describe", "--tags", commitIsh);
        return firstLine(result).trim();
    }

    public void prune(RemoteConfig repository) throws GitException {
        if (getRemoteUrl(repository.getName()) != null &&
            !getRemoteUrl(repository.getName()).equals("")) {
            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add("remote", "prune", repository.getName());

            launchCommand(args);
        }
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
            result = launchCommand("whatchanged", "--no-abbrev", "-M", "-m", "--pretty=raw", "-1", revName);
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

    /**
     * Get submodule URL
     *
     * @param name The name of the submodule
     *
     * @throws GitException if executing the git command fails
     */
    public String getSubmoduleUrl(String name) throws GitException {
        String result = launchCommand( "config", "--get", "submodule."+name+".url" );
        return firstLine(result).trim();
    }

    /**
     * Set submodule URL
     *
     * @param name The name of the submodule
     *
     * @param url The new value of the submodule's URL
     *
     * @throws GitException if executing the git command fails
     */
    public void setSubmoduleUrl(String name, String url) throws GitException {
        launchCommand( "config", "submodule."+name+".url", url );
    }

    /**
     * Get a remote's URL
     *
     * @param name The name of the remote (e.g. origin)
     *
     * @throws GitException if executing the git command fails
     */
    public String getRemoteUrl(String name) throws GitException {
        String result = launchCommand( "config", "--get", "remote."+name+".url" );
        return firstLine(result).trim();
    }

    /**
     * Set a remote's URL
     *
     * @param name The name of the remote (e.g. origin)
     *
     * @param url The new value of the remote's URL
     *
     * @throws GitException if executing the git command fails
     */
    public void setRemoteUrl(String name, String url) throws GitException {
        launchCommand( "config", "remote."+name+".url", url );
    }

    /**
     * From a given repository, get a remote's URL
     *
     * @param name The name of the remote (e.g. origin)
     *
     * @param GIT_DIR The path to the repository (must be to .git dir)
     *
     * @throws GitException if executing the git command fails
     */
    public String getRemoteUrl(String name, String GIT_DIR) throws GitException {
        String result
            = launchCommand( "--git-dir=" + GIT_DIR,
                             "config", "--get", "remote."+name+".url" );
        return firstLine(result).trim();
    }

    /**
     * For a given repository, set a remote's URL
     *
     * @param name The name of the remote (e.g. origin)
     *
     * @param url The new value of the remote's URL
     *
     * @param GIT_DIR The path to the repository (must be to .git dir)
     *
     * @throws GitException if executing the git command fails
     */
    public void setRemoteUrl(String name, String url, String GIT_DIR ) throws GitException {
        launchCommand( "--git-dir=" + GIT_DIR,
                       "config", "remote."+name+".url", url );
    }

    /**
     * Get the default remote.
     *
     * @param _default_ The default remote to use if more than one exists.
     *
     * @return _default_ if it exists, otherwise return the first remote.
     *
     * @throws GitException if executing the git command fails
     */
    public String getDefaultRemote( String _default_ ) throws GitException {
        BufferedReader rdr =
            new BufferedReader(
                new StringReader( launchCommand( "remote" ) )
            );

        List<String> remotes = new ArrayList<String>();

        String line;
        try {
            while ((line = rdr.readLine()) != null) {
                remotes.add(line);
            }
        } catch (IOException e) {
            throw new GitException("Error parsing remotes", e);
        }

        if        ( remotes.contains(_default_) ) {
            return _default_;
        } else if ( remotes.size() >= 1 ) {
            return remotes.get(0);
        } else {
            throw new GitException("No remotes found!");
        }
    }

    /**
     * Get the default remote.
     *
     * @return "origin" if it exists, otherwise return the first remote.
     *
     * @throws GitException if executing the git command fails
     */
    public String getDefaultRemote() throws GitException {
        return getDefaultRemote("origin");
    }

    /**
     * Detect whether a repository is bare or not.
     *
     * @throws GitException
     */
    public boolean isBareRepository() throws GitException {
        return isBareRepository("");
    }

    /**
     * Detect whether a repository at the given path is bare or not.
     *
     * @param GIT_DIR The path to the repository (must be to .git dir).
     *
     * @throws GitException
     */
    public boolean isBareRepository(String GIT_DIR) throws GitException {
        String ret = null;
        if ( "".equals(GIT_DIR) )
            ret = launchCommand(        "rev-parse", "--is-bare-repository");
        else {
            String gitDir = "--git-dir=" + GIT_DIR;
            ret = launchCommand(gitDir, "rev-parse", "--is-bare-repository");
        }

        if ( "false".equals( firstLine(ret).trim() ) )
            return false;
        else
            return true;
    }

    private String pathJoin( String a, String b ) {
        return new File(a, b).toString();
    }

    /**
     * Fixes urls for submodule as stored in .git/config and
     * $SUBMODULE/.git/config for when the remote repo is NOT a bare repository.
     * It is only really possible to detect whether a repository is bare if we
     * have local access to the repository.  If the repository is remote, we
     * therefore must default to believing that it is either bare or NON-bare.
     * The defaults are according to the ending of the super-project
     * remote.origin.url:
     *  - Ends with "/.git":  default is NON-bare
     *  -         otherwise:  default is bare
     *  .
     *
     * @param listener The task listener.
     *
     * @throws GitException if executing the git command fails
     */
    public void fixSubmoduleUrls( String remote,
                                  TaskListener listener ) throws GitException {
        boolean is_bare = true;

        URI origin;
        try {
            String url = getRemoteUrl(remote);

            // ensure that any /.git ending is removed
            String gitEnd = pathJoin("", ".git");
            if ( url.endsWith( gitEnd ) ) {
                url = url.substring(0, url.length() - gitEnd.length() );
                // change the default detection value to NON-bare
                is_bare = false;
            }

            origin = new URI( url );
        } catch (URISyntaxException e) {
            // Sometimes the URI is of a form that we can't parse; like
            //   user@git.somehost.com:repository
            // In these cases, origin is null and it's best to just exit early.
            return;
        } catch (Exception e) {
            throw new GitException("Could determine remote.origin.url", e);
        }

        if ( origin.getScheme() == null ||
             ( "file".equalsIgnoreCase( origin.getScheme() ) &&
               ( origin.getHost() == null || "".equals( origin.getHost() ) )
             )
           ) {
            // The uri is a local path, so we will test to see if it is a bare
            // repository...
            List<String> paths = new ArrayList<String>();
            paths.add( origin.getPath() );
            paths.add( pathJoin( origin.getPath(), ".git" ) );

            for ( String path : paths ) {
                try {
                    is_bare = isBareRepository(path);
                    break;// we can break already if we don't have an exception
                } catch (GitException e) { }
            }
        }

        if ( ! is_bare ) {
            try {
                List<IndexEntry> submodules = getSubmodules("HEAD");

                for (IndexEntry submodule : submodules) {
                    // First fix the URL to the submodule inside the super-project
                    String sUrl = pathJoin( origin.getPath(), submodule.getFile() );
                    setSubmoduleUrl( submodule.getFile(), sUrl );

                    // Second, if the submodule already has been cloned, fix its own
                    // url...
                    String subGitDir = pathJoin( submodule.getFile(), ".git" );

                    /* it is possible that the submodule does not exist yet
                     * since we wait until after checkout to do 'submodule
                     * udpate' */
                    if ( hasGitRepo( subGitDir ) ) {
                        if (! "".equals( getRemoteUrl("origin", subGitDir) )) {
                            setRemoteUrl("origin", sUrl, subGitDir);
                        }
                    }
                }
            } catch (GitException e) {
                // this can fail for example HEAD doesn't exist yet
            }
        } else {
           // we've made a reasonable attempt to detect whether the origin is
           // non-bare, so we'll just assume it is bare from here on out and
           // thus the URLs are correct as given by (which is default behavior)
           //    git config --get submodule.NAME.url
        }
    }

    /**
     * Set up submodule URLs so that they correspond to the remote pertaining to
     * the revision that has been checked out.
     */
    public void setupSubmoduleUrls( Revision rev, TaskListener listener ) throws GitException {
        String remote = null;

        // try to locate the remote repository from where this commit came from
        // (by using the heuristics that the branch name, if available, contains the remote name)
        // if we can figure out the remote, the other setupSubmoduleUrls method
        // look at its URL, and if it's a non-bare repository, we attempt to retrieve modules
        // from this checked out copy.
        //
        // the idea is that you have something like tree-structured repositories: at the root you have corporate central repositories that you
        // ultimately push to, which all .gitmodules point to, then you have intermediate team local repository,
        // which is assumed to be a non-bare repository (say, a checked out copy on a shared server accessed via SSH)
        //
        // the abovementioned behaviour of the Git plugin makes it pick up submodules from this team local repository,
        // not the corporate central.
        //
        // (Kohsuke: I have a bit of hesitation/doubt about such a behaviour change triggered by seemingly indirect
        // evidence of whether the upstream is bare or not (not to mention the fact that you can't reliably
        // figure out if the repository is bare or not just from the URL), but that's what apparently has been implemented
        // and we care about the backward compatibility.)
        //
        // note that "figuring out which remote repository the commit came from" isn't a well-defined
        // question, and this is really a heuristics. The user might be telling us to build a specific SHA1.
        // or maybe someone pushed directly to the workspace and so it may not correspond to any remote branch.
        // so if we fail to figure this out, we back out and avoid being too clever. See JENKINS-10060 as an example
        // of where our trying to be too clever here is breaking stuff for people.
        for (Branch br : rev.getBranches()) {
            String b = br.getName();
            if (b != null) {
                int slash = b.indexOf('/');

                if ( slash != -1 )
                    remote = getDefaultRemote( b.substring(0,slash) );
            }

            if (remote!=null)   break;
        }

        if (remote==null)
            remote = getDefaultRemote();

        if (remote!=null)
            setupSubmoduleUrls( remote, listener );
    }

    public void setupSubmoduleUrls( String remote, TaskListener listener ) throws GitException {
        // This is to make sure that we don't miss any new submodules or
        // changes in submodule origin paths...
        submoduleInit();
        submoduleSync();
        // This allows us to seamlessly use bare and non-bare superproject
        // repositories.
        fixSubmoduleUrls( remote, listener );
    }

    public void tag(String tagName, String comment) throws GitException {
        tagName = tagName.replace(' ', '_');
        try {
            launchCommand("tag", "-a", "-f", "-m", comment, tagName);
        } catch (GitException e) {
            throw new GitException("Could not apply tag " + tagName, e);
        }
    }

    public void appendNote(String note, String namespace ) throws GitException {
        try {
        	launchCommand("notes", "--ref="+namespace, "append", "-m", "\'"+note+"\'" );
        } catch (GitException e) {
            throw new GitException("Could not apply note " + note, e);
        }

    }

    public void addNote(String note, String namespace ) throws GitException {
        try {
            launchCommand("notes", "--ref="+namespace,"add", "-m", "\'"+note+"\'" );
        } catch (GitException e) {
            throw new GitException("Could not apply note " + note, e);
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
        // JENKINS-13356: capture the output of stderr separately
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try {
            args.prepend(getGitExe());
            int status = launcher.launch().cmds(args.toCommandArray()).
                envs(environment).stdout(fos).stderr(err).pwd(workDir).join();

            String result = fos.toString();
            
            // JENKINS-13356: do not return the output of stderr, but at least print it somewhere
            // (disabled again, creates too much unwanted output, for example when cloning a repository)
//            if (err.size() > 0) {
//                listener.getLogger().print("Command \""+StringUtils.join(args.toCommandArray(), " ")+"printed on stderr: "+err.toString());
//            }

            if (status != 0) {
                throw new GitException("Command \""+StringUtils.join(args.toCommandArray(), " ")+"\" returned status code " + status + ":\nstdout: " + result + "\nstderr: "+ err.toString());
            }

            return result;
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Error performing command: " + StringUtils.join(args.toCommandArray()," "), e);
        }
    }

    public void push(RemoteConfig repository, String refspec) throws GitException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("push", repository.getURIs().get(0).toPrivateString());

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

    public void checkout(String commitish) throws GitException {
        checkoutBranch(null,commitish);
    }

    public void checkoutBranch(String branch, String commitish) throws GitException {
        try {
            // First, checkout to detached HEAD, so we can delete the branch.
            launchCommand("checkout", "-f", commitish);

            if (branch!=null) {
                // Second, check to see if the branch actually exists, and then delete it if it does.
                for (Branch b : getBranches()) {
                    if (b.name.equals(branch)) {
                        deleteBranch(branch);
                    }
                }
                // Lastly, checkout the branch, creating it in the process, using commitish as the start point.
                launchCommand("checkout", "-b", branch, commitish);
            }
        } catch (GitException e) {
            throw new GitException("Could not checkout " + branch + " with start point " + commitish, e);
        }
    }

    public boolean tagExists(String tagName) throws GitException {
        tagName = tagName.replace(' ', '_');

        return launchCommand("tag", "-l", tagName).trim().equals(tagName);
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

    public boolean isCommitInRepo(String sha1) {
        try {
            List<ObjectId> revs = revList(sha1);

            if (revs.size() == 0) {
                return false;
            } else {
                return true;
            }
        } catch (GitException e) {
            return false;
        }
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
        fetch(remoteRepository.getURIs().get(0).toPrivateString(), remoteRepository.getFetchRefSpecs().get(0).toString());

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

    public Repository getRepository() throws IOException {
        return new FileRepository(new File(workspace.getRemote(), Constants.DOT_GIT));
    }

    public List<Tag> getTagsOnCommit(final String revName) throws GitException,
            IOException {
        final Repository db = getRepository();
        final ObjectId commit = db.resolve(revName);
        final List<Tag> ret = new ArrayList<Tag>();

        for (final Map.Entry<String, Ref> tag : db.getTags().entrySet()) {
            final ObjectId tagId = tag.getValue().getObjectId();
            if (commit.equals(tagId))
                ret.add(new Tag(tag.getKey(), tagId));
        }
        return ret;
    }

    public Set<String> getTagNames(String tagPattern) throws GitException {
        try {
            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add("tag", "-l", tagPattern);

            String result = launchCommandIn(args, workspace);

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

    public String getHeadRev(String remoteRepoUrl, String branch) throws GitException {
        String[] branchExploded = branch.split("/");
        branch = branchExploded[branchExploded.length-1];
        ArgumentListBuilder args = new ArgumentListBuilder("ls-remote");
        args.add("-h");
        args.add(remoteRepoUrl);
        args.add(branch);
        String result = launchCommand(args);
        return result.length()>=40 ? result.substring(0,40) : "";
    }
}
