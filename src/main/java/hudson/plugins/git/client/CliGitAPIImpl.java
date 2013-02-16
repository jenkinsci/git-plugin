package hudson.plugins.git.client;

import hudson.EnvVars;
import hudson.Functions;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.plugins.git.*;
import hudson.util.ArgumentListBuilder;

import java.io.*;
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
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;

public class CliGitAPIImpl implements IGitAPI {

    Launcher launcher;
    File workspace;
    TaskListener listener;
    String gitExe;
    EnvVars environment;
    String reference;

    public CliGitAPIImpl(String gitExe, File workspace,
                         TaskListener listener, EnvVars environment) {
        this(gitExe, workspace, listener, environment, null);
    }

    public CliGitAPIImpl(String gitExe, File workspace,
                         TaskListener listener, EnvVars environment, String reference) {

        //listener.getLogger().println("Git API @ " + workspace.getName() + " / " + workspace.getRemote() + " - " + workspace.getChannel());

        this.workspace = workspace;
        this.listener = listener;
        this.gitExe = gitExe;
        this.environment = environment;
        this.reference = reference;

        launcher = new LocalLauncher(GitSCM.VERBOSE?listener:TaskListener.NULL);
    }

    public IGitAPI subGit(String subdir) {
        return new CliGitAPIImpl(gitExe, new File(workspace, subdir), listener, environment, reference);
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
            listener.getLogger().println("git --version\n" + v);
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
			final Repository repo = getRepository();
            repo.create();
        } catch (IOException ioe) {
            throw new GitException("Error initiating git repo.", ioe);
        }
    }

    public boolean hasGitRepo() throws GitException {
        if( hasGitRepo(".git") )
        {
            // Check if this is actually a valid git repo by checking ls-files. If it's duff, this will
            // fail. HEAD is not guaranteed to be valid (e.g. new repo).
            try
            {
            	launchCommand("ls-files");
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

            File dotGit = new File(workspace, GIT_DIR);

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

            File dotGit = new File(workspace, ".gitmodules");

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

    public void fetch(String remote, RefSpec refspec) throws GitException {
        listener.getLogger().println(
                                     "Fetching upstream changes"
                                     + (remote != null ? " from " + remote : ""));

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("fetch", "-t");

        if (remote != null) {
            args.add(remote);
            if (refspec != null)
                args.add(refspec.toString());
        }

        launchCommand(args);
    }

    public void fetch() throws GitException {
        fetch(null, null);
    }

    public void reset(boolean hard) throws GitException {
    	try {
    		validateRevision("HEAD");
    	} catch (GitException e) {
    		listener.getLogger().println("No valid HEAD. Skipping the resetting");
    		return;
    	}
        listener.getLogger().println("Resetting working tree");

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("reset");
        if (hard) {
            args.add("--hard");
        }

        launchCommand(args);
    }

    public void reset() throws GitException {
        reset(false);
    }

    /**
     * Start from scratch and clone the whole repository. Cloning into an
     * existing directory is not allowed, so the workspace is first deleted
     * entirely, then <tt>git clone</tt> is performed.
     *
     *
     *
     * @param url
     * @param origin
     * @throws GitException if deleting or cloning the workspace fails
     */
    public void clone(String url, String origin, final boolean useShallowClone) throws GitException {
        listener.getLogger().println("Cloning repository " + url);
        final int[] gitVer = getGitVersion();

        // TODO: Not here!
        try {
            Util.deleteRecursive(workspace);
        } catch (Exception e) {
            e.printStackTrace(listener.error("Failed to clean the workspace"));
            throw new GitException("Failed to delete workspace", e);
        }

        try {
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
            args.add("-o", origin);
            if(useShallowClone) args.add("--depth", "1");
            args.add(url);
            args.add(workspace.getAbsolutePath());
            launchCommandIn(args, null);
        } catch (Exception e) {
            throw new GitException("Could not clone " + url, e);
        }
    }
    
    public void clean() throws GitException {
        reset(true);
        launchCommand("clean", "-fdx");
    }

    public ObjectId revParse(String revName) throws GitException {
        /*
            On Windows command prompt, '^' is an escape character (http://en.wikipedia.org/wiki/Escape_character#Windows_Command_Prompt)
            This isn't a problem if 'git' we are executing is git.exe, because '^' is a special character only for the command processor,
            but if 'git' we are executing is git.cmd (which is the case of msysgit), then the arguments we pass in here ends up getting
            processed by the command processor, and so 'xyz^{commit}' becomes 'xyz{commit}' and fails.

            We work around this problem by surrounding this with double-quote on Windows.
            Unlike POSIX, where the arguments of a process is modeled as String[], Win32 API models the
            arguments of a process as a single string (see CreateProcess). When we surround one argument with a quote,
            java.lang.ProcessImpl on Windows preserve as-is and generate a single string like the following to pass to CreateProcess:

                git rev-parse "tag^{commit}"

            If we invoke git.exe, MSVCRT startup code in git.exe will handle escape and executes it as we expect.
            If we invoke git.cmd, cmd.exe will not eats this ^ that's in double-quote. So it works on both cases.

            Note that this is a borderline-buggy behaviour arguably. If I were implementing ProcessImpl for Windows
            in JDK, My passing a string with double-quotes around it to be expanded to the following:

               git rev-parse "\"tag^{commit}\""

            So this work around that we are doing for Windows relies on the assumption that Java runtime will not
            change this behaviour.

            Also note that on Unix we cannot do this. Similarly, other ways of quoting (like using '^^' instead of '^'
            that you do on interactive command prompt) do not work either, because MSVCRT startup won't handle
            those in the same way cmd.exe does.

            See JENKINS-13007 where this blew up on Windows users.
            See https://github.com/msysgit/msysgit/issues/36 where I filed this as a bug to msysgit.
         */
        String arg = revName + "^{commit}";
        if (Functions.isWindows())
            arg = '"'+arg+'"';
        String result = launchCommand("rev-parse", arg);
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
        String revSpec = revFrom + ".." + revTo;

        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add(gitExe, "whatchanged");
        args.add("--no-abbrev", "-M", "--pretty=raw");
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

    public List<String> showRevision(ObjectId from, ObjectId to) throws GitException {
    	StringWriter writer = new StringWriter();

    	if (from != null){
    		writer.write(launchCommand("show", "--no-abbrev", "--format=raw", "-M", "--raw",
                    from.name() + ".." + to.name()));
    		writer.write("\\n");
    	}
    	
    	writer.write(launchCommand("whatchanged", "--no-abbrev", "-M", "-m", "--pretty=raw", "-1", to.name()));

        String result = writer.toString();
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
    public void merge(ObjectId revSpec) throws GitException {
        try {
            launchCommand("merge", revSpec.name());
        } catch (GitException e) {
            throw new GitException("Could not merge " + revSpec, e);
        }
    }

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
     * Reset submodules
     *
     * @param recursive if true, will recursively reset submodules (requres git>=1.6.5)
     *
     * @throws GitException if executing the git command fails
     */
    public void submoduleReset(boolean recursive, boolean hard) throws GitException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("submodule", "foreach");
        if (recursive) {
            args.add("--recursive");
        }
        args.add("git reset");
        if (hard) {
            args.add("--hard");
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
        submoduleReset(true, true);
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

    public String getRemoteUrl(String name) throws GitException {
        String result = launchCommand( "config", "--get", "remote."+name+".url" );
        return firstLine(result).trim();
    }

    public void setRemoteUrl(String name, String url) throws GitException {
        launchCommand( "config", "remote."+name+".url", url );
    }


    public String getRemoteUrl(String name, String GIT_DIR) throws GitException {
        String result
            = launchCommand( "--git-dir=" + GIT_DIR,
                             "config", "--get", "remote."+name+".url" );
        return firstLine(result).trim();
    }

    public void setRemoteUrl(String name, String url, String GIT_DIR ) throws GitException {
        launchCommand( "--git-dir=" + GIT_DIR,
                       "config", "remote."+name+".url", url );
    }


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

    private void setupSubmoduleUrls( String remote, TaskListener listener ) throws GitException {
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
    private String launchCommandIn(ArgumentListBuilder args, File workDir) throws GitException {
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        // JENKINS-13356: capture the output of stderr separately
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try {
            environment.put("GIT_ASKPASS", launcher.isUnix() ? "/bin/echo" : "echo");
            args.prepend(gitExe);
            Launcher.ProcStarter p = launcher.launch().cmds(args.toCommandArray()).
                    envs(environment).stdout(fos).stderr(err);
            if (workDir != null) p.pwd(workDir);
            int status = p.join();

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

    private Set<Branch> parseBranches(String fos) throws GitException {
        // TODO: git branch -a -v --abbrev=0 would do this in one shot..

        Set<Branch> branches = new HashSet<Branch>();

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
                    branches.add(new Branch(line, revParse(line)));
                }
            }
        } catch (IOException e) {
            throw new GitException("Error parsing branches", e);
        }

        return branches;
    }

    public Set<Branch> getBranches() throws GitException {
        return parseBranches(launchCommand("branch", "-a"));
    }

    public Set<Branch> getRemoteBranches() throws GitException {
        try {
            Repository db = getRepository();
            Map<String, Ref> refs = db.getAllRefs();
            Set<Branch> branches = new HashSet<Branch>();

            for(Ref candidate : refs.values()) {
                if(candidate.getName().startsWith(Constants.R_REMOTES)) {
                    Branch buildBranch = new Branch(candidate);
                    listener.getLogger().println("Seen branch in repository " + buildBranch.getName());
                    branches.add(buildBranch);
                }
            }

            return branches;
        } catch (IOException e) {
            throw new GitException(e);
        }
    }

    public void checkout(String commit) throws GitException {
        launchCommand("checkout", "-f", commit);
    }

    public void checkout(String ref, String branch) throws GitException {
        launchCommand("checkout", "-b", branch, ref);
    }

    public boolean tagExists(String tagName) throws GitException {
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

    public boolean isCommitInRepo(ObjectId commit) {
        try {
            List<ObjectId> revs = revList(commit.name());

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

    public void commit(String message) throws GitException {
        File f = null;
        try {
            f = File.createTempFile("gitcommit", ".txt");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(f);
                fos.write(message.getBytes());
            } finally {
                if (fos != null)
                    fos.close();
            }
            launchCommand("commit", "-F", f.getAbsolutePath());

        } catch (GitException e) {
            throw new GitException("Cannot commit " + message, e);
        } catch (FileNotFoundException e) {
            throw new GitException("Cannot commit " + message, e);
        } catch (IOException e) {
            throw new GitException("Cannot commit " + message, e);
        } finally {
            if (f != null) f.delete();
        }
    }

    public Repository getRepository() throws IOException {
        return new FileRepository(new File(workspace, Constants.DOT_GIT));
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

    public ObjectId getHeadRev(String remoteRepoUrl, String branch) throws GitException {
        String[] branchExploded = branch.split("/");
        branch = branchExploded[branchExploded.length-1];
        ArgumentListBuilder args = new ArgumentListBuilder("ls-remote");
        args.add("-h");
        args.add(remoteRepoUrl);
        args.add(branch);
        String result = launchCommand(args);
        return result.length()>=40 ? ObjectId.fromString(result.substring(0, 40)) : null;
    }
}
