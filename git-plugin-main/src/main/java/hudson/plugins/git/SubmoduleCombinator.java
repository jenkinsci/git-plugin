package hudson.plugins.git;

import hudson.model.TaskListener;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Deprecated as inaccessible in git plugin 4.6.0.  Class retained for
 * binary compatibility.
 * 
 * @author nigelmagnay
 * @deprecated
 */
@Deprecated
public class SubmoduleCombinator {
    @SuppressFBWarnings(value="URF_UNREAD_FIELD", justification="Deprecated, retained for compatibility")
    GitClient git;
    @SuppressFBWarnings(value="URF_UNREAD_FIELD", justification="Deprecated, retained for compatibility")
    TaskListener listener;

    @SuppressFBWarnings(value="URF_UNREAD_FIELD", justification="Deprecated, retained for compatibility")
    long         tid = new Date().getTime();
    @SuppressFBWarnings(value="URF_UNREAD_FIELD", justification="Deprecated, retained for compatibility")
    long         idx = 1;
  
    @SuppressFBWarnings(value="URF_UNREAD_FIELD", justification="Deprecated, retained for compatibility")
    Collection<SubmoduleConfig> submoduleConfig;
  
    @SuppressFBWarnings(value="EI_EXPOSE_REP2", justification="Deprecated, retained for compatibility")
    public SubmoduleCombinator(GitClient git, TaskListener listener, Collection<SubmoduleConfig> cfg) {
        this.git = git;
        this.listener = listener;
        this.submoduleConfig = cfg;
    }

    public void createSubmoduleCombinations() throws GitException, IOException, InterruptedException {
    }

    protected void makeCombination(Map<IndexEntry, Revision> settings) throws InterruptedException {
    }

    public int difference(Map<IndexEntry, Revision> item, List<IndexEntry> entries) {
        return 0;
    }

    protected boolean matches(Map<IndexEntry, Revision> item, List<IndexEntry> entries) {
        return false;
    }

    public List<Map<IndexEntry, Revision>> createCombinations(Map<IndexEntry, Collection<Revision>> moduleBranches) {
        List<Map<IndexEntry, Revision>> result = new ArrayList<>();
        return result;
    }
}
