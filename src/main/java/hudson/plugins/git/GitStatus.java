package hudson.plugins.git;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.UnprotectedRootAction;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.*;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import org.acegisecurity.context.SecurityContext;

/**
 * Information screen for the use of Git in Hudson.
 */
@Extension
public class GitStatus extends AbstractModelObject implements UnprotectedRootAction {
    public String getDisplayName() {
        return "Git";
    }

    public String getSearchUrl() {
        return getUrlName();
    }

    public String getIconFileName() {
        // TODO
        return null;
    }

    public String getUrlName() {
        return "git";
    }

    public HttpResponse doNotifyCommit(@QueryParameter(required=true) String url, @QueryParameter(required=false) String branches) throws ServletException, IOException {
        // run in high privilege to see all the projects anonymous users don't see.
        // this is safe because when we actually schedule a build, it's a build that can
        // happen at some random time anyway.
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            URIish uri;
            try {
                uri = new URIish(url);
            } catch (URISyntaxException e) {
                return HttpResponses.error(SC_BAD_REQUEST, new Exception("Illegal URL: "+url,e));
            }

            if (branches == null) branches = "";
            String[] branchesArray = branches.split(",");

            final List<AbstractProject<?,?>> projects = Lists.newArrayList();
            boolean scmFound = false,
                    triggerFound = false,
                    urlFound = false;
            for (AbstractProject<?,?> project : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                Collection<GitSCM> projectSCMs = getProjectScms(project);
                for (GitSCM git : projectSCMs) {
                  scmFound = true;

                  for (RemoteConfig repository : git.getRepositories()) {
                      boolean repositoryMatches = false,
                              branchMatches = false;
                      for (URIish remoteURL : repository.getURIs()) {
                          if (looselyMatches(uri, remoteURL)) {
                              repositoryMatches = true;
                              break;
                          }
                      }

                      if (!repositoryMatches || git.getExtensions().get(IgnoreNotifyCommit.class)!=null) continue;

                      if (branchesArray.length == 1 && branchesArray[0] == "") {
                          branchMatches = true;
                      } else {
                          for (BranchSpec branchSpec : git.getBranches()) {
                              for (int i=0; i < branchesArray.length; i++) {
                                  if (branchSpec.matches(repository.getName() + "/" + branchesArray[i])) { branchMatches = true; break; }
                              }
                              if (branchMatches) break;
                          }
                      }

                      if (branchMatches) urlFound = true; else continue;


                      SCMTrigger trigger = project.getTrigger(SCMTrigger.class);
                      if (trigger!=null) triggerFound = true; else continue;

                      if (!project.isDisabled()) {
                          LOGGER.info("Triggering the polling of "+project.getFullDisplayName());
                          trigger.run();
                          projects.add(project);
                      }
                      break;
                  }

               }
            }
            final String msg;
            if (!scmFound)  msg = "No git jobs found";
            else if (!urlFound) msg = "No git jobs using repository: " + url + " and branches: " + branches;
            else if (!triggerFound) msg = "Jobs found but they aren't configured for polling";
            else msg = null;

            return new HttpResponse() {
                public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                    rsp.setStatus(SC_OK);
                    rsp.setContentType("text/plain");
                    for (AbstractProject<?, ?> p : projects) {
                        rsp.addHeader("Triggered", p.getAbsoluteUrl());
                    }
                    PrintWriter w = rsp.getWriter();
                    for (AbstractProject<?, ?> p : projects) {
                        w.println("Scheduled polling of "+p.getFullDisplayName());
                    }
                    if (msg!=null)
                        w.println(msg);
                }
            };
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

    private Collection<GitSCM> getProjectScms(AbstractProject<?, ?> project) {
        Set<GitSCM> projectScms = Sets.newHashSet();
        if (Jenkins.getInstance().getPlugin("multiple-scms") != null) {
            MultipleScmResolver multipleScmResolver = new MultipleScmResolver();
            multipleScmResolver.resolveMultiScmIfConfigured(project, projectScms);
        }
        if (projectScms.isEmpty()) {
            SCM scm = project.getScm();
            if (scm instanceof GitSCM) {
                projectScms.add(((GitSCM) scm));
            }
        }
        return projectScms;
    }

    /**
     * Used to test if what we have in the job configuration matches what was submitted to the notification endpoint.
     * It is better to match loosely and wastes a few polling calls than to be pedantic and miss the push notification,
     * especially given that Git tends to support multiple access protocols.
     */
    protected boolean looselyMatches(URIish lhs, URIish rhs) {
        return StringUtils.equals(lhs.getHost(),rhs.getHost())
            && StringUtils.equals(normalizePath(lhs.getPath()), normalizePath(rhs.getPath()));
    }

    private String normalizePath(String path) {
        if (path.startsWith("/"))   path=path.substring(1);
        if (path.endsWith("/"))     path=path.substring(0,path.length()-1);
        if (path.endsWith(".git"))  path=path.substring(0,path.length()-4);
        return path;
    }

    private static final Logger LOGGER = Logger.getLogger(GitStatus.class.getName());
}

