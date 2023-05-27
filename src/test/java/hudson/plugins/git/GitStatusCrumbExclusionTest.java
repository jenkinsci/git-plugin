package hudson.plugins.git;

import hudson.model.TaskListener;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import jenkins.plugins.git.JenkinsRuleUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Check that no crumb is required for successful calls to notifyCommit.
 */
public class GitStatusCrumbExclusionTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    private static final String GIT_REPO_URL = "https://github.com/jenkinsci/git-client-plugin";

    private final URL notifyCommitURL;
    private final URL manageURL; // Valid URL that is not notifyCommit
    private final URL invalidURL; // Jenkins internal URL that reports page not found

    private final String urlArgument;
    private final byte[] urlArgumentBytes;

    private final String separator;
    private final byte[] separatorBytes;

    private final String branchArgument;
    private final byte[] branchArgumentBytes;

    private final String notifyCommitApiToken;
    private final byte[] notifyCommitApiTokenBytes;

    public GitStatusCrumbExclusionTest() throws Exception {
        String jenkinsUrl = r.getURL().toExternalForm();
        if (!jenkinsUrl.endsWith("/")) {
            jenkinsUrl = jenkinsUrl + "/";
        }
        notifyCommitURL = new URL(jenkinsUrl + "git/notifyCommit");
        manageURL = new URL(jenkinsUrl + "manage");
        invalidURL = new URL(jenkinsUrl + "jobs/no-such-job");

        urlArgument = "url=" + GIT_REPO_URL;
        urlArgumentBytes = urlArgument.getBytes(StandardCharsets.UTF_8);

        separator = "&";
        separatorBytes = separator.getBytes(StandardCharsets.UTF_8);

        branchArgument = "branches=origin/some-branch-name";
        branchArgumentBytes = branchArgument.getBytes(StandardCharsets.UTF_8);

        notifyCommitApiToken = "token=" + ApiTokenPropertyConfiguration.get().generateApiToken("test").getString("value");
        notifyCommitApiTokenBytes = notifyCommitApiToken.getBytes(StandardCharsets.UTF_8);
    }

    private HttpURLConnection connectionPOST;

    @Before
    public void connectWithPOST() throws IOException {
        URL postURL = notifyCommitURL;
        connectionPOST = (HttpURLConnection) postURL.openConnection();
        connectionPOST.setRequestMethod("POST");
        connectionPOST.setDoOutput(true);
    }

    @After
    public void disconnectFromPOST() {
        connectionPOST.disconnect();
    }

    @After
    public void makeFilesWritable() throws Exception {
        TaskListener listener = TaskListener.NULL;
        JenkinsRuleUtil.makeFilesWritable(r.getWebAppRoot(), listener);
        if (r.jenkins != null) {
            JenkinsRuleUtil.makeFilesWritable(r.jenkins.getRootDir(), listener);
        }
    }

    /*
     * POST tests.
     */
    @Test
    public void testPOSTValidPathNoArgument() throws Exception {
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
        assertThat(connectionPOST.getResponseMessage(), is("Server Error"));
    }

    @Test
    public void testPOSTValidPathMandatoryArgument() throws Exception {
        try (OutputStream os = connectionPOST.getOutputStream()) {
            os.write(urlArgumentBytes);
            os.write(separatorBytes);
            os.write(notifyCommitApiTokenBytes);
        }
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionPOST.getResponseMessage(), is("OK"));
    }

    @Test
    public void testPOSTValidPathEmptyMandatoryArgument() throws Exception {
        try (OutputStream os = connectionPOST.getOutputStream()) {
            String urlEmptyArgument = "url="; // Empty argument is not a valid URL
            byte[] urlEmptyArgumentBytes = urlEmptyArgument.getBytes(StandardCharsets.UTF_8);
            os.write(urlEmptyArgumentBytes);
            os.write(separatorBytes);
            os.write(notifyCommitApiTokenBytes);
        }
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_BAD_REQUEST));
        assertThat(connectionPOST.getResponseMessage(), is("Bad Request"));
    }

    @Test
    public void testPOSTValidPathBadURLInMandatoryArgument() throws Exception {
        try (OutputStream os = connectionPOST.getOutputStream()) {
            String urlBadArgument = "url=" + "http://256.256.256.256/"; // Not a valid URI per Java 8 javadoc
            byte[] urlBadArgumentBytes = urlBadArgument.getBytes(StandardCharsets.UTF_8);
            os.write(urlBadArgumentBytes);
            os.write(separatorBytes);
            os.write(notifyCommitApiTokenBytes);
        }
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionPOST.getResponseMessage(), is("OK"));
    }

    @Test
    public void testPOSTValidPathMandatoryAndOptionalArgument() throws Exception {
        try (OutputStream os = connectionPOST.getOutputStream()) {
            os.write(urlArgumentBytes);
            os.write(separatorBytes);
            os.write(branchArgumentBytes);
            os.write(separatorBytes);
            os.write(notifyCommitApiTokenBytes);
        }
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionPOST.getResponseMessage(), is("OK"));
    }

    @Test
    public void testPOSTValidPathOnlyOptionalArgument() throws Exception {
        try (OutputStream os = connectionPOST.getOutputStream()) {
            os.write(branchArgumentBytes);
        }
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
        assertThat(connectionPOST.getResponseMessage(), is("Server Error"));
    }

    @Test
    public void testPOSTInvalidPathNoArgument() throws Exception {
        URL invalidPathURL = invalidURL;
        HttpURLConnection invalidPathConnection = (HttpURLConnection) invalidPathURL.openConnection();
        invalidPathConnection.setRequestMethod("POST");
        invalidPathConnection.setDoOutput(true);
        assertThat(invalidPathConnection.getResponseCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(invalidPathConnection.getResponseMessage(), is("Forbidden"));
        invalidPathConnection.disconnect();
    }

    @Test
    public void testPOSTInvalidPathMandatoryArgument() throws Exception {
        URL invalidPathURL = invalidURL;
        HttpURLConnection invalidPathConnection = (HttpURLConnection) invalidPathURL.openConnection();
        invalidPathConnection.setRequestMethod("POST");
        invalidPathConnection.setDoOutput(true);
        try (OutputStream os = invalidPathConnection.getOutputStream()) {
            os.write(urlArgumentBytes);
        }
        assertThat(invalidPathConnection.getResponseCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(invalidPathConnection.getResponseMessage(), is("Forbidden"));
        invalidPathConnection.disconnect();
    }

    @Test
    public void testPOSTInvalidPathMandatoryAndOptionalArgument() throws Exception {
        URL invalidPathURL = invalidURL;
        HttpURLConnection invalidPathConnection = (HttpURLConnection) invalidPathURL.openConnection();
        invalidPathConnection.setRequestMethod("POST");
        invalidPathConnection.setDoOutput(true);
        try (OutputStream os = invalidPathConnection.getOutputStream()) {
            os.write(urlArgumentBytes);
            os.write(separatorBytes);
            os.write(branchArgumentBytes);
        }
        assertThat(invalidPathConnection.getResponseCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(invalidPathConnection.getResponseMessage(), is("Forbidden"));
        invalidPathConnection.disconnect();
    }

    @Test
    public void testPOSTManageNoArgument() throws Exception {
        URL postURL = manageURL;
        HttpURLConnection manageConnection = (HttpURLConnection) postURL.openConnection();
        manageConnection.setRequestMethod("POST");
        manageConnection.setDoOutput(true);
        assertThat(manageConnection.getResponseCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(manageConnection.getResponseMessage(), is("Forbidden"));
        manageConnection.disconnect();
    }

    @Test
    public void testPOSTManageMandatoryArgument() throws Exception {
        URL postURL = manageURL;
        HttpURLConnection manageConnection = (HttpURLConnection) postURL.openConnection();
        manageConnection.setRequestMethod("POST");
        manageConnection.setDoOutput(true);
        try (OutputStream os = manageConnection.getOutputStream()) {
            os.write(urlArgumentBytes);
        }
        assertThat(manageConnection.getResponseCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(manageConnection.getResponseMessage(), is("Forbidden"));
        manageConnection.disconnect();
    }

    @Test
    public void testPOSTManageMandatoryAndOptionalArgument() throws Exception {
        URL postURL = manageURL;
        HttpURLConnection manageConnection = (HttpURLConnection) postURL.openConnection();
        manageConnection.setRequestMethod("POST");
        manageConnection.setDoOutput(true);
        try (OutputStream os = manageConnection.getOutputStream()) {
            os.write(urlArgumentBytes);
            os.write(separatorBytes);
            os.write(branchArgumentBytes);
        }
        assertThat(manageConnection.getResponseCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(manageConnection.getResponseMessage(), is("Forbidden"));
        manageConnection.disconnect();
    }

    /*
     * GET tests.
     */
    @Test
    public void testGETValidPathNoArgument() throws Exception {
        URL getURL = notifyCommitURL;
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
        assertThat(connectionGET.getResponseMessage(), is("Server Error"));
        connectionGET.disconnect();
    }

    @Test
    public void testGETValidPathMandatoryArgument() throws Exception {
        URL getURL = new URL(notifyCommitURL + "?" + urlArgument + separator + notifyCommitApiToken);
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionGET.getResponseMessage(), is("OK"));
        connectionGET.disconnect();
    }

    @Test
    public void testGETValidPathMandatoryAndOptionalArgument() throws Exception {
        URL getURL = new URL(notifyCommitURL + "?" + urlArgument + separator + branchArgument + separator + notifyCommitApiToken);
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionGET.getResponseMessage(), is("OK"));
        connectionGET.disconnect();
    }

    @Test
    public void testGETInvalidPath() throws Exception {
        URL getURL = invalidURL;
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
        assertThat(connectionGET.getResponseMessage(), is("Not Found"));
        connectionGET.disconnect();
    }

    @Test
    public void testGETInvalidPathWithArgument() throws Exception {
        URL getURL = new URL(invalidURL + "?" + urlArgument);
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
        assertThat(connectionGET.getResponseMessage(), is("Not Found"));
        connectionGET.disconnect();
    }

    @Test
    public void testGETManagePath() throws Exception {
        URL getURL = manageURL;
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionGET.getResponseMessage(), is("OK"));
        connectionGET.disconnect();
    }

    @Test
    public void testGETManagePathWithArgument() throws Exception {
        URL getURL = new URL(manageURL + "?" + urlArgument);
        HttpURLConnection connection = (HttpURLConnection) getURL.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        assertThat(connection.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connection.getResponseMessage(), is("OK"));
        connection.disconnect();
    }
}
