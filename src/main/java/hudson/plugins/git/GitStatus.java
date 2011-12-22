package hudson.plugins.git;

import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.UnprotectedRootAction;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;
import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Logger;

import static javax.servlet.http.HttpServletResponse.*;

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

    public HttpResponse doNotifyCommit(@QueryParameter(required=true) String url) throws ServletException, IOException {
        // run in high privilege to see all the projects anonymous users don't see
        Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);
        try {
            URIish uri;
            try {
                uri = new URIish(url);
            } catch (URISyntaxException e) {
                return HttpResponses.error(SC_BAD_REQUEST, new Exception("Illegal URL: "+url,e));
            }

            final List<AbstractProject<?,?>> projects = Lists.newArrayList();
            boolean scmFound = false,
                    triggerFound = false,
                    urlFound = false;
            for (AbstractProject<?,?> project : Hudson.getInstance().getAllItems(AbstractProject.class)) {
                SCM scm = project.getScm();
                if (scm instanceof GitSCM) scmFound = true; else continue;

                GitSCM git = (GitSCM) scm;
                for (RemoteConfig repository : git.getRepositories()) {
                    boolean repositoryMatches = false;
                    for (URIish remoteURL : repository.getURIs()) {
                        if (uri.equals(remoteURL)) { repositoryMatches = true; break; }
                    }
                    if (repositoryMatches) urlFound = true; else continue;

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

            final String msg;
            if (!scmFound)  msg = "No git jobs found";
            else if (!urlFound) msg = "No git jobs using repository: " + url;
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
            SecurityContextHolder.getContext().setAuthentication(old);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GitStatus.class.getName());
}

