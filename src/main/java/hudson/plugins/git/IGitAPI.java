package hudson.plugins.git;

import java.io.File;
import java.io.OutputStream;
import java.util.List;

public interface IGitAPI {
    String getGitExe();
	
    boolean hasGitRepo() throws GitException;
	boolean hasGitModules() throws GitException;
	
	void submoduleInit()  throws GitException;
    void submoduleUpdate()  throws GitException;
    
    public void fetch(String repository, String refspec) throws GitException;
    void fetch() throws GitException;
    void push(String revspec) throws GitException;
    void merge(String revSpec) throws GitException;
    void clone(String source) throws GitException;
    
    String revParse(String revName) throws GitException;
    List<Tag> getHudsonTags() throws GitException;
    List<Branch> getBranches() throws GitException;
    List<Branch> getBranchesContaining(String revspec) throws GitException;
    
    List<IndexEntry> lsTree(String treeIsh) throws GitException;
    
    List<String> revListBranch(String branchId) throws GitException;
    List<String> revListAll() throws GitException;
    
    void tag(String tagName, String comment) throws GitException;
    void deleteTag(String tagName) throws GitException;
    
    void changelog(String revFrom, String revTo, OutputStream fos) throws GitException;
	void checkout(String revToBuild) throws GitException;
  
	void add(String filePattern) throws GitException;
	void branch(String name) throws GitException;

    void commit(File f) throws GitException;
}
