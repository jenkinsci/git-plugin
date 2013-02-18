package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hudson.plugins.git.Branch;
import hudson.plugins.git.IndexEntry;
import hudson.plugins.git.Revision;
import junit.framework.Assert;
import junit.framework.TestCase;

public class SubmoduleCombinatorTest extends TestCase
{
  public void testCombination()
  {
    SubmoduleCombinator sc = new SubmoduleCombinator(null, null, null, null, null);

    Map<IndexEntry, Collection<Revision>> moduleBranches = new HashMap<IndexEntry, Collection<Revision>>();

    Collection<Revision> revisions;
    Revision revision;
    
    revisions = new ArrayList<Revision>();
    revision = new Revision("1");
    revision.getBranches().add(new Branch("bt1", "1"));
    revision.getBranches().add(new Branch("bt1", "2"));
    revision.getBranches().add(new Branch("bt1", "3"));

    revisions.add(revision);
    revisions.add(new Revision("2"));
    revisions.add(new Revision("3"));
    
    moduleBranches.put(new IndexEntry("", "", "", "moduleA"), revisions);

    revisions = new ArrayList<Revision>();
    revisions.add(new Revision("4"));
    revisions.add(new Revision("5"));
    revisions.add(new Revision("6"));
    moduleBranches.put(new IndexEntry("", "", "", "moduleB"), revisions);

    revisions = new ArrayList<Revision>();
    revisions.add(new Revision("7"));
    revisions.add(new Revision("8"));
    revisions.add(new Revision("9"));
    
    moduleBranches.put(new IndexEntry("", "", "", "moduleC"), revisions);
    
    List<Map<IndexEntry, Revision>> combinations = sc.createCombinations(moduleBranches);
    Assert.assertEquals(27, combinations.size());
    
    Map<IndexEntry, Revision> choice = combinations.get(0);
    List<IndexEntry> match = new ArrayList<IndexEntry>();
    match.add(new IndexEntry("", "", "1", "moduleA"));
    match.add(new IndexEntry("", "", "7", "moduleC"));
    match.add(new IndexEntry("", "", "4", "moduleB"));

    Assert.assertEquals(0, sc.difference(choice, match));
    
    

  }
}
