package hudson.plugins.git;

import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.UnprotectedRootAction;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URISyntaxException;
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
	    URIish uri;
        try {
            uri = new URIish(url);
        } catch (URISyntaxException e) {
            return HttpResponses.error(SC_BAD_REQUEST, new Exception("Illegal URL: "+url,e));
        }

        boolean scmFound = false,
                triggerFound = false,
                urlFound = false;
        for (AbstractProject<?,?> project : Hudson.getInstance().getAllItems(AbstractProject.class)) {
            SCM scm = project.getScm();
            if (scm instanceof GitSCM) scmFound = true; else continue;

            SCMTrigger trigger = project.getTrigger(SCMTrigger.class);
            if (trigger!=null) triggerFound = true; else continue;

            GitSCM git = (GitSCM) scm;
            for (RemoteConfig repository : git.getRepositories()) {
                boolean repositoryMatches = false;
                for (URIish remoteURL : repository.getURIs()) {
                    if (uri.equals(remoteURL)) { repositoryMatches = true; break; }
                }
                if (repositoryMatches) urlFound = true; else continue;

                LOGGER.info("Triggering the polling of "+project.getFullDisplayName());
                trigger.run();
                break;
            }
        }

        if (!scmFound) LOGGER.warning("No git jobs found");
        else if (!triggerFound) LOGGER.warning("No git jobs using SCM polling");
        else if (!urlFound) LOGGER.warning("No git jobs using repository: " + uri.toString());

        return HttpResponses.status(SC_OK);
    }

    private static final Logger LOGGER = Logger.getLogger(GitStatus.class.getName());
}

