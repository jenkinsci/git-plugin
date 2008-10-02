package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Collection;

public class Revision implements java.io.Serializable
{
  String             sha1;
  Collection<Branch> branches;

  public Revision(String sha1)
  {
    this.sha1 = sha1;
    this.branches = new ArrayList<Branch>();
  }
  
  public Revision(String sha1, Collection<Branch> branches)
  {
    this.sha1 = sha1;
    this.branches = branches;
  }
  
  public String getSha1()
  {
    return sha1;
  }

  public void setSha1(String sha1)
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
    String s = "Rev " + sha1 + " (";
    for (Branch br : branches)
    {
      s += br.getName() + " ";
    }
    s += ")";

    return s;
  }
  
}
