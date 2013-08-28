package hudson.plugins.git;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
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
import java.util.ArrayList;
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
        URIish uri;
        try {
            uri = new URIish(url);
        } catch (URISyntaxException e) {
            return HttpResponses.error(SC_BAD_REQUEST, new Exception("Illegal URL: " + url, e));
        }

        branches = Util.fixEmptyAndTrim(branches);
        String[] branchesArray;
        if (branches == null) {
            branchesArray = new String[0];
        } else {
            branchesArray = branches.split(",");
        }

        final List<ResponseContributor> contributors = new ArrayList<ResponseContributor>();
        for (Listener listener : Jenkins.getInstance().getExtensionList(Listener.class)) {
            contributors.addAll(listener.onNotifyCommit(uri, branchesArray));
        }

        return new HttpResponse() {
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node)
                    throws IOException, ServletException {
                rsp.setStatus(SC_OK);
                rsp.setContentType("text/plain");
                for (ResponseContributor c : contributors) {
                    c.addHeaders(req, rsp);
                }
                PrintWriter w = rsp.getWriter();
                for (ResponseContributor c : contributors) {
                    c.writeBody(req, rsp, w);
                }
            }
        };
    }

    private static Collection<GitSCM> getProjectScms(AbstractProject<?, ?> project) {
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
    protected static boolean looselyMatches(URIish lhs, URIish rhs) {
        return StringUtils.equals(lhs.getHost(),rhs.getHost())
            && StringUtils.equals(normalizePath(lhs.getPath()), normalizePath(rhs.getPath()));
    }

    private static String normalizePath(String path) {
        if (path.startsWith("/"))   path=path.substring(1);
        if (path.endsWith("/"))     path=path.substring(0,path.length()-1);
        if (path.endsWith(".git"))  path=path.substring(0,path.length()-4);
        return path;
    }

    /**
     * Contributes to a {@link #doNotifyCommit(String, String)} response.
     *
     * @since 1.4.1
     */
    public static class ResponseContributor {
        /**
         * Add headers to the response.
         *
         * @param req the request.
         * @param rsp the response.
         * @since 1.4.1
         */
        public void addHeaders(StaplerRequest req, StaplerResponse rsp) {
        }

        /**
         * Write the contributed body.
         *
         * @param req the request.
         * @param rsp the response.
         * @param w   the writer.
         * @since 1.4.1
         */
        public void writeBody(StaplerRequest req, StaplerResponse rsp, PrintWriter w) {
            writeBody(w);
        }

        /**
         * Write the contributed body.
         *
         * @param w the writer.
         * @since 1.4.1
         */
        public void writeBody(PrintWriter w) {
        }
    }

    /**
     * Other plugins may be interested in listening for these updates.
     *
     * @since 1.4.1
     */
    public static abstract class Listener implements ExtensionPoint {

        /**
         * Called when there is a change notification on a specific repository url.
         *
         * @param uri      the repository uri.
         * @param branches the (optional) branch information.
         * @return any response contributors for the response to the push request.
         * @since 1.4.1
         */
        public abstract List<ResponseContributor> onNotifyCommit(URIish uri, String... branches);
    }

    /**
     * Handle standard {@link AbstractProject} instances with a standard {@link SCMTrigger}.
     *
     * @since 1.4.1
     */
    @Extension
    @SuppressWarnings("unused") // Jenkins extension
    public static class JenkinsAbstractProjectListener extends Listener {

        /**
         * {@inheritDoc}
         */
        @Override
        public List<ResponseContributor> onNotifyCommit(URIish uri, String... branches) {
            List<ResponseContributor> result = new ArrayList<ResponseContributor>();
            // run in high privilege to see all the projects anonymous users don't see.
            // this is safe because when we actually schedule a build, it's a build that can
            // happen at some random time anyway.
            SecurityContext old = ACL.impersonate(ACL.SYSTEM);
            try {

                final List<AbstractProject<?, ?>> projects = Lists.newArrayList();
                boolean scmFound = false,
                        triggerFound = false,
                        urlFound = false;
                for (final AbstractProject<?, ?> project : Hudson.getInstance().getAllItems(AbstractProject.class)) {
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

                            if (!repositoryMatches || git.getExtensions().get(IgnoreNotifyCommit.class)!=null) {
                                continue;
                            }

                            if (branches.length == 0) {
                                branchMatches = true;
                            } else {
                                for (BranchSpec branchSpec : git.getBranches()) {
                                    for (String branch : branches) {
                                        if (branchSpec.matches(repository.getName() + "/" + branch)) {
                                            branchMatches = true;
                                            break;
                                        }
                                    }
                                    if (branchMatches) {
                                        break;
                                    }
                                }
                            }

                            if (branchMatches) {
                                urlFound = true;
                            } else {
                                continue;
                            }


                            SCMTrigger trigger = project.getTrigger(SCMTrigger.class);
                            if (trigger != null) {
                                triggerFound = true;
                            } else {
                                continue;
                            }

                            if (!project.isDisabled()) {
                                LOGGER.info("Triggering the polling of " + project.getFullDisplayName());
                                trigger.run();
                                result.add(new PollingScheduledResponseContributor(project));
                            }
                            break;
                        }

                    }
                }
                if (!scmFound) {
                    result.add(new MessageResponseContributor("No git jobs found"));
                } else if (!urlFound) {
                    result.add(new MessageResponseContributor(
                            "No git jobs using repository: " + uri.toString() + " and branches: " + StringUtils
                                    .join(branches, ",")));
                } else if (!triggerFound) {
                    result.add(new MessageResponseContributor("Jobs found but they aren't configured for polling"));
                }

                return result;
            } finally {
                SecurityContextHolder.setContext(old);
            }
        }

        /**
         * A response contributor for triggering polling of an {@link AbstractProject}.
         *
         * @since 1.4.1
         */
        private static class PollingScheduledResponseContributor extends ResponseContributor {
            /**
             * The project
             */
            private final AbstractProject<?, ?> project;

            /**
             * Constructor.
             *
             * @param project the project.
             */
            public PollingScheduledResponseContributor(AbstractProject<?, ?> project) {
                this.project = project;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void addHeaders(StaplerRequest req, StaplerResponse rsp) {
                rsp.addHeader("Triggered", project.getAbsoluteUrl());
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public void writeBody(PrintWriter w) {
                w.println("Scheduled polling of " + project.getFullDisplayName());
            }
        }
    }

    /**
     * A response contributor that just adds a simple message to the body.
     *
     * @since 1.4.1
     */
    public static class MessageResponseContributor extends ResponseContributor {
        /**
         * The message.
         */
        private final String msg;

        /**
         * Constructor.
         *
         * @param msg the message.
         */
        public MessageResponseContributor(String msg) {
            this.msg = msg;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeBody(PrintWriter w) {
            w.println(msg);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GitStatus.class.getName());
}

