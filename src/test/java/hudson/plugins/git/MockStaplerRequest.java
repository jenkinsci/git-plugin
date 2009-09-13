package hudson.plugins.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.Principal;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import net.sf.json.JSONObject;

import org.apache.commons.fileupload.FileItem;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * A Mock implementation of {@link StaplerRequest}. Almost all calls to the methods defined by
 * StaplerRequest and its parent interfaces are guaranteed to fail. The only ones that will not fail are
 * {@link #getParameter(String)} and {@link #getParameterValues(String)}. These are backed by a Map that
 * can be primed by multiple setter methods defined in this class. The setters generally set the values
 * expected by the GitSCM builder.
 *
 * @author ishaaq
 *
 */
public class MockStaplerRequest implements StaplerRequest {

    private final Map<String, String[]> params = new HashMap<String, String[]>();

    public String getParameter(String name) {
        String[] values = params.get(name);
        if(values != null) {
            return values[0];
        }
        return null;
    }

    public String[] getParameterValues(String name) {
        return params.get(name);
    }

    public MockStaplerRequest setRepos(String[] repoUrls, String[] repoNames, String[] refSpecs) {
        return put("git.repo.url", repoUrls)
            .put("git.repo.name", repoNames)
            .put("git.repo.refspec", refSpecs);
    }

    public MockStaplerRequest setRepo(String repoUrl, String repoName, String refSpec) {
        return put("git.repo.url", repoUrl)
            .put("git.repo.name", repoName)
            .put("git.repo.refspec", refSpec);

    }

    public MockStaplerRequest setBranches(String[] branches) {
        return put("git.branch", branches);
    }

    public MockStaplerRequest setBranch(String branch) {
        return put("git.branch", branch);
    }

    public MockStaplerRequest setMergeTarget(String mergeTarget) {
        return put("git.mergeTarget", mergeTarget);
    }

    public MockStaplerRequest setGitWebUrl(String gitWebUrl) {
        return put("gitweb.url", gitWebUrl);
    }

    public MockStaplerRequest setGitGenerate(String gitGenerate) {
        return put("git.generate", gitGenerate);
    }

    public MockStaplerRequest setGitClean(String gitClean) {
        return put("git.clean", gitClean);
    }

    public MockStaplerRequest setGitExe(String gitExe) {
        return put("git.gitExe", gitExe);
    }

    /**
     * In addition to the setter methods you can directly prime values into the parameter map
     * using this method.
     * @param key a parameter key.
     * @param values an array of matching values.
     */
    public MockStaplerRequest put(String key, String[] values) {
        params.put(key, values);
        return this;
    }

    /**
     * In addition to the setter methods you can directly prime values into the parameter map
     * using this method.
     * @param key a parameter key.
     * @param values a matching value.
     */
    public MockStaplerRequest put(String key, String value) {
        params.put(key, new String[]{value});
        return this;
    }

    public <T> T bindJSON(Class<T> type, JSONObject src) {
        throw new UnsupportedOperationException();
    }

    public void bindJSON(Object bean, JSONObject src) {
        throw new UnsupportedOperationException();
    }

    public <T> List<T> bindJSONToList(Class<T> type, Object src) {
        throw new UnsupportedOperationException();
    }

    public void bindParameters(Object bean) {
        throw new UnsupportedOperationException();
    }

    public void bindParameters(Object bean, String prefix) {
        throw new UnsupportedOperationException();
    }

    public <T> T bindParameters(Class<T> type, String prefix) {
        throw new UnsupportedOperationException();
    }

    public <T> T bindParameters(Class<T> type, String prefix, int index) {
        throw new UnsupportedOperationException();
    }

    public <T> List<T> bindParametersToList(Class<T> type, String prefix) {
        throw new UnsupportedOperationException();
    }

    public boolean checkIfModified(long timestampOfResource, StaplerResponse rsp) {
        throw new UnsupportedOperationException();
    }

    public boolean checkIfModified(Date timestampOfResource, StaplerResponse rsp) {
        throw new UnsupportedOperationException();
    }

    public Ancestor findAncestor(Class type) {
        throw new UnsupportedOperationException();
    }

    public Ancestor findAncestor(Object o) {
        throw new UnsupportedOperationException();
    }

    public <T> T findAncestorObject(Class<T> type) {
        throw new UnsupportedOperationException();
    }

    public List<Ancestor> getAncestors() {
        throw new UnsupportedOperationException();
    }

    public FileItem getFileItem(String name) {
        throw new UnsupportedOperationException();
    }

    public String getOriginalRequestURI() {
        throw new UnsupportedOperationException();
    }

    public String getReferer() {
        throw new UnsupportedOperationException();
    }

    public String getRestOfPath() {
        throw new UnsupportedOperationException();
    }

    public String getRootPath() {
        throw new UnsupportedOperationException();
    }

    public ServletContext getServletContext() {
        throw new UnsupportedOperationException();
    }

    public JSONObject getSubmittedForm() throws ServletException {
        throw new UnsupportedOperationException();
    }

    public RequestDispatcher getView(Object it, String viewName) {
        throw new UnsupportedOperationException();
    }

    public RequestDispatcher getView(Class clazz, String viewName) {
        throw new UnsupportedOperationException();
    }

    public boolean hasParameter(String name) {
        throw new UnsupportedOperationException();
    }

    public String getAuthType() {
        throw new UnsupportedOperationException();
    }

    public String getContextPath() {
        throw new UnsupportedOperationException();
    }

    public Cookie[] getCookies() {
        throw new UnsupportedOperationException();
    }

    public long getDateHeader(String name) {
        throw new UnsupportedOperationException();
    }

    public String getHeader(String name) {
        throw new UnsupportedOperationException();
    }

    public Enumeration getHeaderNames() {
        throw new UnsupportedOperationException();
    }

    public Enumeration getHeaders(String name) {
        throw new UnsupportedOperationException();
    }

    public int getIntHeader(String name) {
        throw new UnsupportedOperationException();
    }

    public String getMethod() {
        throw new UnsupportedOperationException();
    }

    public String getPathInfo() {
        throw new UnsupportedOperationException();
    }

    public String getPathTranslated() {
        throw new UnsupportedOperationException();
    }

    public String getQueryString() {
        throw new UnsupportedOperationException();
    }

    public String getRemoteUser() {
        throw new UnsupportedOperationException();
    }

    public String getRequestURI() {
        throw new UnsupportedOperationException();
    }

    public StringBuffer getRequestURL() {
        throw new UnsupportedOperationException();
    }

    public String getRequestedSessionId() {
        throw new UnsupportedOperationException();
    }

    public String getServletPath() {
        throw new UnsupportedOperationException();
    }

    public HttpSession getSession() {
        throw new UnsupportedOperationException();
    }

    public HttpSession getSession(boolean create) {
        throw new UnsupportedOperationException();
    }

    public Principal getUserPrincipal() {
        throw new UnsupportedOperationException();
    }

    public boolean isRequestedSessionIdFromCookie() {
        throw new UnsupportedOperationException();
    }

    public boolean isRequestedSessionIdFromURL() {
        throw new UnsupportedOperationException();
    }

    public boolean isRequestedSessionIdFromUrl() {
        throw new UnsupportedOperationException();
    }

    public boolean isRequestedSessionIdValid() {
        throw new UnsupportedOperationException();
    }

    public boolean isUserInRole(String role) {
        throw new UnsupportedOperationException();
    }

    public Object getAttribute(String name) {
        throw new UnsupportedOperationException();
    }

    public Enumeration getAttributeNames() {
        throw new UnsupportedOperationException();
    }

    public String getCharacterEncoding() {
        throw new UnsupportedOperationException();
    }

    public int getContentLength() {
        throw new UnsupportedOperationException();
    }

    public String getContentType() {
        throw new UnsupportedOperationException();
    }

    public ServletInputStream getInputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    public String getLocalAddr() {
        throw new UnsupportedOperationException();
    }

    public String getLocalName() {
        throw new UnsupportedOperationException();
    }

    public int getLocalPort() {
        throw new UnsupportedOperationException();
    }

    public Locale getLocale() {
        throw new UnsupportedOperationException();
    }

    public Enumeration getLocales() {
        throw new UnsupportedOperationException();
    }

    public Map getParameterMap() {
        throw new UnsupportedOperationException();
    }

    public Enumeration getParameterNames() {
        throw new UnsupportedOperationException();
    }

    public String getProtocol() {
        throw new UnsupportedOperationException();
    }

    public BufferedReader getReader() throws IOException {
        throw new UnsupportedOperationException();
    }

    public String getRealPath(String path) {
        throw new UnsupportedOperationException();
    }

    public String getRemoteAddr() {
        throw new UnsupportedOperationException();
    }

    public String getRemoteHost() {
        throw new UnsupportedOperationException();
    }

    public int getRemotePort() {
        throw new UnsupportedOperationException();
    }

    public RequestDispatcher getRequestDispatcher(String path) {
        throw new UnsupportedOperationException();
    }

    public String getScheme() {
        throw new UnsupportedOperationException();
    }

    public String getServerName() {
        throw new UnsupportedOperationException();
    }

    public int getServerPort() {
        throw new UnsupportedOperationException();
    }

    public boolean isSecure() {
        throw new UnsupportedOperationException();
    }

    public void removeAttribute(String name) {
        throw new UnsupportedOperationException();
    }

    public void setAttribute(String name, Object o) {
        throw new UnsupportedOperationException();
    }

    public void setCharacterEncoding(String env) {
        throw new UnsupportedOperationException();
    }

    public boolean checkIfModified(Calendar timestampOfResource, StaplerResponse rsp) {
        throw new UnsupportedOperationException();
    }

    public boolean checkIfModified(long timestampOfResource, StaplerResponse rsp, long expiration) {
        throw new UnsupportedOperationException();
    }
}
