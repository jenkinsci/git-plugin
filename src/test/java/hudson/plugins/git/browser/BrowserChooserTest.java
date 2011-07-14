/**
 * Copyright 2010 Mirko Friedenhagen 
 */

package hudson.plugins.git.browser;

import hudson.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import junit.framework.TestCase;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.WebApp;
import org.mockito.Mockito;

/**
 * 
 * This class tests switching between the different browser implementation.
 * 
 * @author mfriedenhagen
 */
public class BrowserChooserTest extends TestCase {

    private final Stapler stapler = Mockito.mock(Stapler.class);

    private final HttpServletRequest servletRequest = Mockito.mock(HttpServletRequest.class);

    @SuppressWarnings("unchecked")
    private final StaplerRequest staplerRequest = new RequestImpl(stapler, servletRequest, Collections.EMPTY_LIST, null);

    {
        final WebApp webApp = Mockito.mock(WebApp.class);
        Mockito.when(webApp.getClassLoader()).thenReturn(this.getClass().getClassLoader());
        Mockito.when(stapler.getWebApp()).thenReturn(webApp);
    }

    public void testRedmineWeb() throws IOException {
        testExistingBrowser(RedmineWeb.class);
    }

    public void testGitoriousWeb() throws IOException {
        testExistingBrowser(GitoriousWeb.class);
    }

    public void testGithubWeb() throws IOException {
        testExistingBrowser(GithubWeb.class);
    }

    public void testGitWeb() throws IOException {
        testExistingBrowser(GitWeb.class);
    }

    public void testNonExistingBrowser() throws IOException {
        final JSONObject json = readJson();
        try {
            createBrowserFromJson(json);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertSame(e.getCause().getCause().getClass(), ClassNotFoundException.class);
        }
    }

    /**
     * @param browserClass
     * @throws IOException
     */
    void testExistingBrowser(final Class<? extends GitRepositoryBrowser> browserClass) throws IOException {
        final JSONObject json = readJson(browserClass);
        assertSame(browserClass, createBrowserFromJson(json).getClass());
    }

    /**
     * @param json
     * @return
     */
    GitRepositoryBrowser createBrowserFromJson(final JSONObject json) {
        GitRepositoryBrowser browser = staplerRequest.bindJSON(GitRepositoryBrowser.class,
                json.getJSONObject("browser"));
        return browser;
    }

    /**
     * Reads the request data from file scm.json and replaces the invalid browser class in the JSONObject with the class
     * specified as parameter.
     * 
     * @param browserClass
     * @return
     * @throws IOException
     */
    JSONObject readJson(Class<? extends GitRepositoryBrowser> browserClass) throws IOException {
        final JSONObject json = readJson();
        json.getJSONObject("browser").element("stapler-class", browserClass.getName());
        return json;
    }

    /**
     * Reads the request data from file scm.json.
     * 
     * @return
     * @throws IOException
     */
    JSONObject readJson() throws IOException {
        final InputStream stream = this.getClass().getResourceAsStream("scm.json");
        final String scmString;
        try {
            scmString = IOUtils.toString(stream);
        } finally {
            stream.close();
        }
        final JSONObject json = (JSONObject) JSONSerializer.toJSON(scmString);
        return json;
    }
}
