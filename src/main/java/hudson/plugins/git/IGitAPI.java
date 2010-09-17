package hudson.plugins.git;

import hudson.EnvVars;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Tag;
import org.spearce.jgit.transport.RemoteConfig;

public interface IGitAPI {
    String getGitExe();
    EnvVars getEnvironment();

    public void init() throws GitException;
    boolean hasGitRepo() throws GitException;
    boolean hasGitModules() throws GitException;

    void submoduleInit()  throws GitException;
    void submoduleUpdate(boolean recursive)  throws GitException;
    void submoduleClean(boolean recursive)  throws GitException;
    void submoduleSync() throws GitException;

    public void fetch(String repository, String refspec) throws GitException;
    void fetch(RemoteConfig remoteRepository);

    void fetch() throws GitException;
    void push(RemoteConfig repository, String revspec) throws GitException;
    void merge(String revSpec) throws GitException;
    void clone(RemoteConfig source) throws GitException;
    void clean() throws GitException;

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
    boolean tagExists(String tagName) throws GitException;
    void deleteTag(String tagName) throws GitException;
    Set<String> getTagNames(String tagPattern) throws GitException;

    void changelog(String revFrom, String revTo, OutputStream fos) throws GitException;
    void checkout(String revToBuild) throws GitException;
    void checkoutBranch(String branch, String revToBuild) throws GitException;
    
    void add(String filePattern) throws GitException;
    void branch(String name) throws GitException;
    void deleteBranch(String name) throws GitException;
    
    void commit(File f) throws GitException;

    ObjectId mergeBase(ObjectId sha1, ObjectId sha12);
    String getAllLogEntries(String branch);

    List<String> showRevision(Revision r) throws GitException;
}
