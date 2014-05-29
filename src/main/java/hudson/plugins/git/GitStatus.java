package hudson.plugins.git;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.AbstractModelObject;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Item;
import hudson.model.UnprotectedRootAction;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import jenkins.model.Jenkins;
import jenkins.triggers.SCMTriggerItem;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.*;


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

    public HttpResponse doNotifyCommit(@QueryParameter(required=true) String url,
                                       @QueryParameter(required=false) String branches,
                                       @QueryParameter(required=false) String sha1) throws ServletException, IOException {
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
            contributors.addAll(listener.onNotifyCommit(uri, sha1, branchesArray));
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

    /**
     * Used to test if what we have in the job configuration matches what was submitted to the notification endpoint.
     * It is better to match loosely and wastes a few polling calls than to be pedantic and miss the push notification,
     * especially given that Git tends to support multiple access protocols.
     */
    public static boolean looselyMatches(URIish lhs, URIish rhs) {
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
     * Contributes to a {@link #doNotifyCommit(String, String, String)} response.
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
         * @deprecated implement #onNotifyCommit(org.eclipse.jgit.transport.URIish, String, String...)
         */
        public List<ResponseContributor> onNotifyCommit(URIish uri, String[] branches) {
            return onNotifyCommit(uri, null, branches);
        }

        public List<ResponseContributor> onNotifyCommit(URIish uri, @Nullable String sha1, String... branches) {
            return Collections.EMPTY_LIST;
        }
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
        public List<ResponseContributor> onNotifyCommit(URIish uri, String sha1, String... branches) {
            List<ResponseContributor> result = new ArrayList<ResponseContributor>();
            // run in high privilege to see all the projects anonymous users don't see.
            // this is safe because when we actually schedule a build, it's a build that can
            // happen at some random time anyway.
            SecurityContext old = ACL.impersonate(ACL.SYSTEM);
            try {

                boolean scmFound = false,
                        urlFound = false;
                for (final Item project : Jenkins.getInstance().getAllItems()) {
                    SCMTriggerItem scmTriggerItem = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(project);
                    if (project == null) {
                        continue;
                    }
                    for (SCM scm : scmTriggerItem.getSCMs()) {
                        if (!(scm instanceof GitSCM)) {
                            continue;
                        }
                        GitSCM git = (GitSCM) scm;
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

                            SCMTrigger trigger = scmTriggerItem.getSCMTrigger();
                            if (trigger != null && trigger.isIgnorePostCommitHooks()) {
                                LOGGER.info("PostCommitHooks are disabled on " + project.getFullDisplayName());
                                continue;
                            }

                            Boolean branchFound = false;
                            if (branches.length == 0) {
                                branchFound = true;
                            } else {
                                OUT: for (BranchSpec branchSpec : git.getBranches()) {
                                    for (String branch : branches) {
                                        if (branchSpec.matches(repository.getName() + "/" + branch)) {
                                            branchFound = true;
                                            break OUT;
                                        }
                                    }
                                }
                            }
                            if (!branchFound) continue;
                            urlFound = true;

                            if (!(project instanceof AbstractProject && ((AbstractProject) project).isDisabled())) {
                                if (isNotEmpty(sha1)) {
                                    LOGGER.info("Scheduling " + project.getFullDisplayName() + " to build commit " + sha1);
                                    scmTriggerItem.scheduleBuild2(scmTriggerItem.getQuietPeriod(),
                                            new CauseAction(new CommitHookCause(sha1)),
                                            new RevisionParameterAction(sha1));
                                    result.add(new ScheduledResponseContributor(project));
                                } else if (trigger != null) {
                                    LOGGER.info("Triggering the polling of " + project.getFullDisplayName());
                                    trigger.run();
                                    result.add(new PollingScheduledResponseContributor(project));
                                }
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
                }

                return result;
            } finally {
                SecurityContextHolder.setContext(old);
            }
        }

        /**
         * A response contributor for triggering polling of a project.
         *
         * @since 1.4.1
         */
        private static class PollingScheduledResponseContributor extends ResponseContributor {
            /**
             * The project
             */
            private final Item project;

            /**
             * Constructor.
             *
             * @param project the project.
             */
            public PollingScheduledResponseContributor(Item project) {
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

        private static class ScheduledResponseContributor extends ResponseContributor {
            /**
             * The project
             */
            private final Item project;

            /**
             * Constructor.
             *
             * @param project the project.
             */
            public ScheduledResponseContributor(Item project) {
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
                w.println("Scheduled " + project.getFullDisplayName());
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

    public static class CommitHookCause extends Cause {

        public final String sha1;

        public CommitHookCause(String sha1) {
            this.sha1 = sha1;
        }

        @Override
        public String getShortDescription() {
            return "commit notification " + sha1;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GitStatus.class.getName());
}

