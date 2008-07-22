package hudson.plugins.git;

/**
 * Definition of a remote GIT repository.
 */
public class RemoteRepository
{
  /**
   * Name of the host (e.g origin).
   */
  private String name;

  /**
   * URL to get to the repository.
   */
  private String url;

  // Something like +refs/heads/*:refs/remotes/nm/*
  private String refspec;

  /**
   * Is this repository a centralised repository, or a working-copy style personal repository.
   */
  private boolean isCentralisedRepository = false;
  
  public boolean isCentralisedRepository()
  {
    return isCentralisedRepository;
  }

  public void setCentralisedRepository(boolean isCentralisedRepository)
  {
    this.isCentralisedRepository = isCentralisedRepository;
  }

  public RemoteRepository()
  {

  }
  
  
  public RemoteRepository(String name, String url, String refspec)
  {
    if (name == null || name.length() == 0) throw new IllegalArgumentException("You must name a repository");
    this.name = name;
    this.url = url;
    this.refspec = refspec;
    
    if (this.refspec == null || this.refspec.length() == 0)
    {
      this.refspec = "+refs/heads/*:refs/remotes/" + name + "/*";
    }
    
  }

  public RemoteRepository getSubmoduleRepository(String name)
  {
    String refUrl = this.url;
    if (refUrl.endsWith("/.git"))
    {
      refUrl = refUrl.substring(0, refUrl.length() - 4);
    }

    if (!refUrl.endsWith("/")) refUrl += "/";

    refUrl += name;

    if (!refUrl.endsWith("/")) refUrl += "/";

    refUrl += ".git";

    return new RemoteRepository(name, refUrl, refspec);
  }
  
  public String getName()
  {
    return name;
  }

  public void setName(String name)
  {
    this.name = name;
  }

  public String getUrl()
  {
    return url;
  }

  public void setUrl(String url)
  {
    this.url = url;
  }

  public String getRefspec()
  {
    return refspec;
  }

  public void setRefspec(String refspec)
  {
    this.refspec = refspec;
  }
}
