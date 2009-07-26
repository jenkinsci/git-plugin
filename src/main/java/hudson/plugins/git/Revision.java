package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Collection;

import org.spearce.jgit.lib.ObjectId;

/**
 * A Revision is a SHA1 in the object tree, and the collection of branches
 * that share this ID. Unlike other SCMs, git can have >1 branches point
 * at the _same_ commit.
 * 
 * @author magnayn
 */
public class Revision implements java.io.Serializable, Cloneable
{
  private static final long serialVersionUID = -7203898556389073882L;

  ObjectId           sha1;
  Collection<Branch> branches;

  public Revision(ObjectId sha1)
  {
    this.sha1 = sha1;
    this.branches = new ArrayList<Branch>();
  }
  
  public Revision(ObjectId sha1, Collection<Branch> branches)
  {
    this.sha1 = sha1;
    this.branches = branches;
  }
  
  public ObjectId getSha1()
  {
    return sha1;
  }

  public void setSha1(ObjectId sha1)
  {
    this.sha1 = sha1;
  }

  public Collection<Branch> getBranches()
  {
    return branches;
  }

  public void setBranches(Collection<Branch> branches)
  {
    this.branches = branches;
  }
  
  public boolean containsBranchName(String name)
  {
    for (Branch b : branches)
    {
      if (b.getName().equals(name))
      {
        return true;
      }
    }
    return false;
  }

  public String toString()
  {
    String s = "Revision " + sha1.name() + " (";
    for (Branch br : branches)
    {
      s += br.getName() + ", ";
    }
    if (s.endsWith(", "))
      s = s.substring(0, s.length() - 2);
    s += ")";

    return s;
  }
  
  @Override
  public Revision clone()
  {
    Revision clone;
	try
    {
      clone = (Revision) super.clone();
    }
    catch (CloneNotSupportedException e)
    {
      throw new RuntimeException("Error cloning Revision", e);
    }
    clone.branches = new ArrayList<Branch>(branches);
    return clone;
  }
  
}
