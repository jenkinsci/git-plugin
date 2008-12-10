package hudson.plugins.git.util;

import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.IndexEntry;
import hudson.plugins.git.Revision;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GitUtils
{
  IGitAPI git;
  TaskListener listener;

  public GitUtils(TaskListener listener, IGitAPI git)
  {
    this.git = git;
    this.listener = listener;
  }

  public List<IndexEntry> getSubmodules(String treeIsh)
  {
    List<IndexEntry> submodules = git.lsTree(treeIsh);

    // Remove anything that isn't a submodule
    for (Iterator<IndexEntry> it = submodules.iterator(); it.hasNext();)
    {
      if (!it.next().getMode().equals("160000"))
      {
        it.remove();
      }
    }
    return submodules;
  }
  
  public Collection<Revision> getAllBranchRevisions()
  {
    Map<String, Revision> revisions = new HashMap<String, Revision>();
    List<Branch> branches = git.getBranches();
    for (Branch b : branches)
    {
      Revision r = revisions.get(b.getSHA1());
      if (r == null)
      {
        r = new Revision(b.getSHA1());
        revisions.put(b.getSHA1(), r);
      }
      r.getBranches().add(b);
    }
    return revisions.values();
  }
  
  /**
   * Return a list of 'tip' branches (I.E. branches that aren't included entirely within another branch).
   * 
   * @param git
   * @return
   */
  public Collection<Revision> getTipBranches()
  {
    Collection<Revision> revisions = getAllBranchRevisions();

    for (Iterator<Revision> it = revisions.iterator(); it.hasNext();)
    {
      Revision r = it.next();
      Collection<Branch> contained = git.getBranchesContaining(r.getSha1());

      for (Branch candidate : contained)
      {
        if (!candidate.getSHA1().equals(r.getSha1()))
        {
          it.remove();
          break;
        }
      }
    }
    
    return revisions;
  }
}