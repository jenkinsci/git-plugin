package hudson.plugins.git;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.*;
import hudson.plugins.git.extensions.impl.IgnoreNotifyCommit;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMEvent;
import jenkins.triggers.SCMTriggerItem;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.lang.StringUtils;
import static org.apache.commons.lang.StringUtils.isNotEmpty;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.kohsuke.stapler.*;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Information screen for the use of Git in Hudson.
 */
@Extension
public class GitStatus implements UnprotectedRootAction {
    @Override
    public String getDisplayName() {
        return "Git";
    }

    public String getIconFileName() {
        // TODO
        return null;
    }

    @Override
    public String getUrlName() {
        return "git";
    }

    /* Package protected - not part of API, needed for testing */
    /* package */
    static void setAllowNotifyCommitParameters(boolean allowed) {
        allowNotifyCommitParameters = allowed;
    }

    private String lastURL = "";        // Required query parameter
    private String lastBranches = null; // Optional query parameter
    private String lastSHA1 = null;     // Optional query parameter
    private List<ParameterValue> lastBuildParameters = null;
    private static List<ParameterValue> lastStaticBuildParameters = null;

    private static void clearLastStaticBuildParameters() {
        lastStaticBuildParameters = null;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        s.append("URL: ");
        s.append(lastURL);

        if (lastSHA1 != null) {
            s.append(" SHA1: ");
            s.append(lastSHA1);
        }

        if (lastBranches != null) {
            s.append(" Branches: ");
            s.append(lastBranches);
        }

        if (lastBuildParameters != null && !lastBuildParameters.isEmpty()) {
            s.append(" Parameters: ");
            for (ParameterValue buildParameter : lastBuildParameters) {
                s.append(buildParameter.getName());
                s.append("='");
                s.append(buildParameter.getValue());
                s.append("',");
            }
            s.delete(s.length() - 1, s.length());
        }

        if (lastStaticBuildParameters != null && !lastStaticBuildParameters.isEmpty()) {
            s.append(" More parameters: ");
            for (ParameterValue buildParameter : lastStaticBuildParameters) {
                s.append(buildParameter.getName());
                s.append("='");
                s.append(buildParameter.getValue());
                s.append("',");
            }
            s.delete(s.length() - 1, s.length());
        }

        return s.toString();
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
                        justification = "Tests use null instance, Jenkins 2.60 declares instance is not null")
    public HttpResponse doNotifyCommit(HttpServletRequest request, @QueryParameter(required=true) String url,
                                       @QueryParameter(required=false) String branches,
                                       @QueryParameter(required=false) String sha1) throws ServletException, IOException {
        lastURL = url;
        lastBranches = branches;
        lastSHA1 = sha1;
        lastBuildParameters = null;
        GitStatus.clearLastStaticBuildParameters();
        URIish uri;
        List<ParameterValue> buildParameters = new ArrayList<>();

        try {
            uri = new URIish(url);
        } catch (URISyntaxException e) {
            return HttpResponses.error(SC_BAD_REQUEST, new Exception("Illegal URL: " + url, e));
        }

        if (allowNotifyCommitParameters || !safeParameters.isEmpty()) { // Allow SECURITY-275 bug
            final Map<String, String[]> parameterMap = request.getParameterMap();
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                if (!(entry.getKey().equals("url")) && !(entry.getKey().equals("branches")) && !(entry.getKey().equals("sha1")))
                    if (entry.getValue()[0] != null && (allowNotifyCommitParameters || safeParameters.contains(entry.getKey())))
                        buildParameters.add(new StringParameterValue(entry.getKey(), entry.getValue()[0]));
            }
        }
        lastBuildParameters = buildParameters;

        branches = Util.fixEmptyAndTrim(branches);

        String[] branchesArray;
        if (branches == null) {
            branchesArray = new String[0];
        } else {
            branchesArray = branches.split(",");
        }

        final List<ResponseContributor> contributors = new ArrayList<>();
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return HttpResponses.error(SC_BAD_REQUEST, new Exception("Jenkins.getInstance() null for : " + url));
        }
        String origin = SCMEvent.originOf(request);
        for (Listener listener : jenkins.getExtensionList(Listener.class)) {
            contributors.addAll(listener.onNotifyCommit(origin, uri, sha1, buildParameters, branchesArray));
        }

        return new HttpResponse() {
            @Override
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node)
                    throws IOException, ServletException {
                rsp.setStatus(SC_OK);
                rsp.setContentType("text/plain");
                for (int i = 0; i < contributors.size(); i++) {
                    if (i == MAX_REPORTED_CONTRIBUTORS) {
                        rsp.addHeader("Triggered", "<" + (contributors.size() - i) + " more>");
                        break;
                    } else {
                        contributors.get(i).addHeaders(req, rsp);
                    }
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
     * @param lhs left-hand side of comparison
     * @param rhs right-hand side of comparison
     * @return true if left-hand side loosely matches right-hand side
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
     * Contributes to a {@link #doNotifyCommit(HttpServletRequest, String, String, String)} response.
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
         * @deprecated implement {@link #onNotifyCommit(org.eclipse.jgit.transport.URIish, String, List, String...)}
         * @param uri             the repository uri.
         * @param branches        the (optional) branch information.
         * @return any response contributors for the response to the push request.
         */
        public List<ResponseContributor> onNotifyCommit(URIish uri, String[] branches) {
            throw new AbstractMethodError();
        }

        /**
         * @deprecated implement {@link #onNotifyCommit(org.eclipse.jgit.transport.URIish, String, List, String...)}
         * @param uri             the repository uri.
         * @param sha1            SHA1 hash of commit to build
         * @param branches        the (optional) branch information.
         * @return any response contributors for the response to the push request.
         */
        public List<ResponseContributor> onNotifyCommit(URIish uri, @Nullable String sha1, String... branches) {
            return onNotifyCommit(uri, branches);
        }

        /**
         * Called when there is a change notification on a specific repository url.
         *
         * @param uri             the repository uri.
         * @param sha1            SHA1 hash of commit to build
         * @param buildParameters parameters to be passed to the build.
         *                        Ignored unless build parameter flag is set
         *                        due to security risk of accepting parameters from
         *                        unauthenticated sources
         * @param branches        the (optional) branch information.
         * @return any response contributors for the response to the push request.
         * @since 2.4.0
         * @deprecated use {@link #onNotifyCommit(String, URIish, String, List, String...)}
         */
        @Deprecated
        public List<ResponseContributor> onNotifyCommit(URIish uri, @Nullable String sha1, List<ParameterValue> buildParameters, String... branches) {
            return onNotifyCommit(uri, sha1, branches);
        }

        /**
         * Called when there is a change notification on a specific repository url.
         *
         * @param origin          the origin of the notification (use {@link SCMEvent#originOf(HttpServletRequest)} if in
         *                        doubt) or {@code null} if the origin is unknown.
         * @param uri             the repository uri.
         * @param sha1            SHA1 hash of commit to build
         * @param buildParameters parameters to be passed to the build.
         *                        Ignored unless build parameter flag is set
         *                        due to security risk of accepting parameters from
         *                        unauthenticated sources
         * @param branches        the (optional) branch information.
         * @return any response contributors for the response to the push request.
         * @since 2.6.5
         */
        public List<ResponseContributor> onNotifyCommit(@CheckForNull String origin,
                                                        URIish uri,
                                                        @Nullable String sha1,
                                                        List<ParameterValue> buildParameters,
                                                        String... branches) {
            return onNotifyCommit(uri, sha1, buildParameters, branches);
        }


    }

    /**
     * Handle standard {@link SCMTriggerItem} instances with a standard {@link SCMTrigger}.
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
        @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
                            justification = "Tests use null instance, Jenkins 2.60 declares instance is not null")
        public List<ResponseContributor> onNotifyCommit(String origin, URIish uri, String sha1, List<ParameterValue> buildParameters, String... branches) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Received notification from {0} for uri = {1} ; sha1 = {2} ; branches = {3}",
                           new Object[]{StringUtils.defaultIfBlank(origin, "?"), uri, sha1, Arrays.toString(branches)});
            }

            GitStatus.clearLastStaticBuildParameters();
            List<ParameterValue> allBuildParameters = new ArrayList<>(buildParameters);
            List<ResponseContributor> result = new ArrayList<>();
            // run in high privilege to see all the projects anonymous users don't see.
            // this is safe because when we actually schedule a build, it's a build that can
            // happen at some random time anyway.
            SecurityContext old = ACL.impersonate(ACL.SYSTEM);
            try {

                boolean scmFound = false,
                        urlFound = false;
                Jenkins jenkins = Jenkins.getInstance();
                if (jenkins == null) {
                    LOGGER.severe("Jenkins.getInstance() is null in GitStatus.onNotifyCommit");
                    return result;
                }
                for (final Item project : jenkins.getAllItems()) {
                    SCMTriggerItem scmTriggerItem = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(project);
                    if (scmTriggerItem == null) {
                        continue;
                    }
                    SCMS: for (SCM scm : scmTriggerItem.getSCMs()) {
                        if (!(scm instanceof GitSCM)) {
                            continue;
                        }
                        GitSCM git = (GitSCM) scm;
                        scmFound = true;

                        for (RemoteConfig repository : git.getRepositories()) {
                            boolean repositoryMatches = false,
                                    branchMatches = false;
                            URIish matchedURL = null;
                            for (URIish remoteURL : repository.getURIs()) {
                                if (looselyMatches(uri, remoteURL)) {
                                    repositoryMatches = true;
                                    matchedURL = remoteURL;
                                    break;
                                }
                            }

                            if (!repositoryMatches || git.getExtensions().get(IgnoreNotifyCommit.class)!=null) {
                                continue;
                            }

                            SCMTrigger trigger = scmTriggerItem.getSCMTrigger();
                            if (trigger == null || trigger.isIgnorePostCommitHooks()) {
                                LOGGER.log(Level.INFO, "no trigger, or post-commit hooks disabled, on {0}", project.getFullDisplayName());
                                continue;
                            }

                            boolean branchFound = false,
                                    parametrizedBranchSpec = false;
                            if (branches.length == 0) {
                                branchFound = true;
                            } else {
                                OUT: for (BranchSpec branchSpec : git.getBranches()) {
                                    if (branchSpec.getName().contains("$")) {
                                        // If the branchspec is parametrized, always run the polling
                                        if (LOGGER.isLoggable(Level.FINE)) {
                                            LOGGER.log(Level.FINE, "Branch Spec is parametrized for {0}", project.getFullDisplayName());
                                        }
                                        branchFound = true;
                                        parametrizedBranchSpec = true;
                                    } else {
                                        for (String branch : branches) {
                                            if (branchSpec.matchesRepositoryBranch(repository.getName(), branch)) {
                                                if (LOGGER.isLoggable(Level.FINE)) {
                                                    LOGGER.log(Level.FINE, "Branch Spec {0} matches modified branch {1} for {2}", new Object[]{branchSpec, branch, project.getFullDisplayName()});
                                                }
                                                branchFound = true;
                                                break OUT;
                                            }
                                        }
                                    }
                                }
                            }
                            if (!branchFound) continue;
                            urlFound = true;
                            if (!(project instanceof AbstractProject && ((AbstractProject) project).isDisabled())) {
                                //JENKINS-30178 Add default parameters defined in the job
                                if (project instanceof Job) {
                                    Set<String> buildParametersNames = new HashSet<>();
                                    if (allowNotifyCommitParameters || !safeParameters.isEmpty()) {
                                        for (ParameterValue parameterValue: allBuildParameters) {
                                            if (allowNotifyCommitParameters || safeParameters.contains(parameterValue.getName())) {
                                                buildParametersNames.add(parameterValue.getName());
                                            }
                                        }
                                    }

                                    List<ParameterValue> jobParametersValues = getDefaultParametersValues((Job) project);
                                    for (ParameterValue defaultParameterValue : jobParametersValues) {
                                        if (!buildParametersNames.contains(defaultParameterValue.getName())) {
                                            allBuildParameters.add(defaultParameterValue);
                                        }
                                    }
                                }
                                if (!parametrizedBranchSpec && isNotEmpty(sha1)) {
                                    /* If SHA1 and not a parameterized branch spec, then schedule build.
                                     * NOTE: This is SCHEDULING THE BUILD, not triggering polling of the repo.
                                     * If no SHA1 or the branch spec is parameterized, it will only poll.
                                     */
                                    LOGGER.log(Level.INFO, "Scheduling {0} to build commit {1}", new Object[]{project.getFullDisplayName(), sha1});
                                    scmTriggerItem.scheduleBuild2(scmTriggerItem.getQuietPeriod(),
                                            new CauseAction(new CommitHookCause(sha1)),
                                            new RevisionParameterAction(sha1, matchedURL), new ParametersAction(allBuildParameters));
                                    result.add(new ScheduledResponseContributor(project));
                                } else {
                                    /* Poll the repository for changes
                                     * NOTE: This is not scheduling the build, just polling for changes
                                     * If the polling detects changes, it will schedule the build
                                     */
                                    LOGGER.log(Level.INFO, "Triggering the polling of {0}", project.getFullDisplayName());
                                    trigger.run();
                                    result.add(new PollingScheduledResponseContributor(project));
                                    break SCMS; // no need to trigger the same project twice, so do not consider other GitSCMs in it
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

                lastStaticBuildParameters = allBuildParameters;
                return result;
            } finally {
                SecurityContextHolder.setContext(old);
            }
        }

        /**
         * Get the default parameters values from a job
         *
         */
        private ArrayList<ParameterValue> getDefaultParametersValues(Job<?,?> job) {
            ArrayList<ParameterValue> defValues;
            ParametersDefinitionProperty paramDefProp = job.getProperty(ParametersDefinitionProperty.class);

            if (paramDefProp != null) {
                List <ParameterDefinition> parameterDefinition = paramDefProp.getParameterDefinitions();
                defValues = new ArrayList<>(parameterDefinition.size());

            } else {
                defValues = new ArrayList<>();
                return defValues;
            }

            /* Scan for all parameter with an associated default values */
            for (ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {
                ParameterValue defaultValue  = paramDefinition.getDefaultParameterValue();

                if (defaultValue != null) {
                    defValues.add(defaultValue);
                }
            }

            return defValues;
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
    private static final int MAX_REPORTED_CONTRIBUTORS = 10;

    /** Allow arbitrary notify commit parameters.
     *
     * SECURITY-275 detected that allowing arbitrary parameters through
     * the notifyCommit URL allows an unauthenticated user to set
     * environment variables for a job.
     *
     * If this property is set to true, then the bug exposed by
     * SECURITY-275 will be brought back. Only enable this if you
     * trust all unauthenticated users to not pass harmful arguments
     * to your jobs.
     *
     * -Dhudson.plugins.git.GitStatus.allowNotifyCommitParameters=true on command line
     *
     * Also honors the global Jenkins security setting
     * "hudson.model.ParametersAction.keepUndefinedParameters" if it
     * is set to true.
     */
    public static final boolean ALLOW_NOTIFY_COMMIT_PARAMETERS = Boolean.valueOf(System.getProperty(GitStatus.class.getName() + ".allowNotifyCommitParameters", "false"))
            || Boolean.valueOf(System.getProperty("hudson.model.ParametersAction.keepUndefinedParameters", "false"));
    private static boolean allowNotifyCommitParameters = ALLOW_NOTIFY_COMMIT_PARAMETERS;

    /* Package protected for test.
     * If null is passed as argument, safe parameters are reset to defaults.
     */
    static void setSafeParametersForTest(String parameters) {
        safeParameters = csvToSet(parameters != null ? parameters : SAFE_PARAMETERS);
    }

    private static Set<String> csvToSet(String csvLine) {
        String[] tokens = csvLine.split(",");
        Set<String> set = new HashSet<>(Arrays.asList(tokens));
        return set;
    }

    @NonNull
    private static String getSafeParameters() {
        String globalSafeParameters = System.getProperty("hudson.model.ParametersAction.safeParameters", "").trim();
        String gitStatusSafeParameters = System.getProperty(GitStatus.class.getName() + ".safeParameters", "").trim();
        if (globalSafeParameters.isEmpty()) {
            return gitStatusSafeParameters;
        }
        if (gitStatusSafeParameters.isEmpty()) {
            return globalSafeParameters;
        }
        return globalSafeParameters + "," + gitStatusSafeParameters;
    }

    /**
     * Allow specifically declared safe parameters.
     *
     * SECURITY-275 detected that allowing arbitrary parameters through the
     * notifyCommit URL allows an unauthenticated user to set environment
     * variables for a job.
     *
     * If this property is set to a comma separated list of parameters, then
     * those parameters will be allowed for any job. Only set this value for
     * parameters you trust in all the jobs in your system.
     *
     * -Dhudson.plugins.git.GitStatus.safeParameters=PARM1,PARM1 on command line
     *
     * Also honors the global Jenkins safe parameter list
     * "hudson.model.ParametersAction.safeParameters" if set.
     */
    public static final String SAFE_PARAMETERS = getSafeParameters();
    private static Set<String> safeParameters = csvToSet(SAFE_PARAMETERS);
}
