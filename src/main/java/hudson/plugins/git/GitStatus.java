package hudson.plugins.git;

import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.RootAction;

import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import java.io.IOException;

import java.util.logging.Logger;

/**
 * Information screen for the use of Git in Hudson.
 */
@Extension
public class GitStatus extends AbstractModelObject implements RootAction {
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

    public void doNotifyCommit(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        String urlString = req.getParameter("url");
	URIish url = null;
        try {
            url = new URIish(urlString);
        } catch (java.net.URISyntaxException e) { }

        boolean scmFound = false,
                triggerFound = false,
                urlFound = false;
        for (AbstractProject<?,?> project : Hudson.getInstance().getItems(AbstractProject.class)) {
            SCM scm = project.getScm();
            if (scm instanceof GitSCM) scmFound = true; else continue;

            SCMTrigger trigger = project.getTrigger(SCMTrigger.class);
            if (trigger!=null) triggerFound = true; else continue;

            GitSCM git = (GitSCM) scm;
            for (RemoteConfig repository : git.getRepositories()) {
                boolean repositoryMatches = false;
                for (URIish remoteURL : repository.getURIs()) {
                    if (url.equals(remoteURL)) { repositoryMatches = true; break; }
                }
                if (repositoryMatches) urlFound = true; else continue;

                trigger.run();
            }
        }

        if (url == null)    LOGGER.warning("Couldn't read url: " + urlString);
        else if (!scmFound) LOGGER.warning("No git jobs found");
        else if (!triggerFound) LOGGER.warning("No git jobs using SCM polling");
        else if (!urlFound) LOGGER.warning("No git jobs using repository: " + url.toString());

        rsp.setStatus(SC_OK);
    }

    private static final Logger LOGGER = Logger.getLogger(GitStatus.class.getName());
}

