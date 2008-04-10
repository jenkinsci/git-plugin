package hudson.plugins.git;

import java.io.OutputStream;

public interface IGitAPI {
	
	boolean hasGitRepo() throws GitException;
	boolean hasGitModules() throws GitException;
	
	void submoduleInit()  throws GitException;
    void submoduleUpdate()  throws GitException;
    
    void fetch() throws GitException;
    void merge() throws GitException;
    void clone(String source) throws GitException;
    
    void log(OutputStream fos) throws GitException;
    void diff(OutputStream baos) throws GitException;
}
