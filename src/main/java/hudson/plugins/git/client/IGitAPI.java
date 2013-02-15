package hudson.plugins.git.client;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Encapsulates Git operations on a particular directory through git(1).
 */
public interface IGitAPI {

    Repository getRepository() throws IOException;

    public void init() throws GitException;

    void add(String filePattern) throws GitException;

    // TODO way to set commit author/committer
    void commit(String message) throws GitException;

    boolean hasGitRepo() throws GitException;

    boolean isCommitInRepo(ObjectId commit) throws GitException;

    /**
     * From a given repository, get a remote's URL
     * @param name The name of the remote (e.g. origin)
     * @throws GitException if executing the git command fails
     */
    String getRemoteUrl(String name) throws GitException;

    /**
     * For a given repository, set a remote's URL
     * @param name The name of the remote (e.g. origin)
     * @param url The new value of the remote's URL
     * @throws GitException if executing the git command fails
     */
    void setRemoteUrl(String name, String url) throws GitException;

    void clean() throws GitException;

    void fetch(String remote, RefSpec refspec) throws GitException;

    /**
     * Creates a new branch that points to the current HEAD.
     */
    void branch(String name) throws GitException;

    void deleteBranch(String name) throws GitException;


    // --- submodules ----

    /**
     * @return a IGitAPI implementation to manage git submodule repository
     */
    IGitAPI subGit(String subdir);

    /**
     * Returns true if the repository has Git submodules.
     */
    boolean hasGitModules() throws GitException;
    boolean hasGitModules( String treeIsh ) throws GitException;
    List<IndexEntry> getSubmodules( String treeIsh ) throws GitException;
    void submoduleInit()  throws GitException;
    void submoduleUpdate(boolean recursive)  throws GitException;
    void submoduleClean(boolean recursive)  throws GitException;
    void submoduleSync() throws GitException;
    String getSubmoduleUrl(String name) throws GitException;
    void setSubmoduleUrl(String name, String url) throws GitException;
    void fixSubmoduleUrls( String remote, TaskListener listener ) throws GitException;
    void setupSubmoduleUrls( Revision rev, TaskListener listener ) throws GitException;
    void setupSubmoduleUrls( String remote, TaskListener listener ) throws GitException;

    void push(RemoteConfig repository, String revspec) throws GitException;
    void merge(String revSpec) throws GitException;
    void clone(RemoteConfig source) throws GitException;
    void clone(RemoteConfig rc, boolean useShallowClone) throws GitException;
    void prune(RemoteConfig repository) throws GitException;

    ObjectId revParse(String revName) throws GitException;
    List<Branch> getBranches() throws GitException;
    List<Branch> getRemoteBranches() throws GitException, IOException;
    List<Branch> getBranchesContaining(String revspec) throws GitException;

    List<IndexEntry> lsTree(String treeIsh) throws GitException;

    List<ObjectId> revListBranch(String branchId) throws GitException;
    List<ObjectId> revListAll() throws GitException;

    String describe(String commitIsh) throws GitException;

    List<Tag> getTagsOnCommit(String revName) throws GitException, IOException;

    void tag(String tagName, String comment) throws GitException;
    
    void appendNote(String note, String namespace ) throws GitException;
    void addNote(String note, String namespace ) throws GitException;
    	
   
    boolean tagExists(String tagName) throws GitException;
    void deleteTag(String tagName) throws GitException;
    Set<String> getTagNames(String tagPattern) throws GitException;

    /**
     * Adds the changelog entries for commits in the range revFrom..revTo.
     */
    void changelog(String revFrom, String revTo, OutputStream fos) throws GitException;

    /**
     * Short for {@code checkoutBranch(null,commitish)}
     */
    void checkout(String commitish) throws GitException;

    /**
     * Checks out the specified commit/ref into the workspace.
     *
     * @param branch
     *      If non-null, move/create the branch in this name at the specified commit-ish and check out that branch.
     */
    void checkoutBranch(String branch, String commitish) throws GitException;


    ObjectId mergeBase(ObjectId sha1, ObjectId sha12);
    String getAllLogEntries(String branch);

    List<String> showRevision(ObjectId r, ObjectId from) throws GitException;
    String getHeadRev(String remoteRepoUrl, String branch) throws GitException;

    String getReference();
}
