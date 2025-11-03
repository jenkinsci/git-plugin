package hudson.plugins.git;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Check that no crumb is required for successful calls to notifyCommit.
 */
@WithJenkins
class GitStatusCrumbExclusionTest {

    private static JenkinsRule r;

    private static final String GIT_REPO_URL = "https://github.com/jenkinsci/git-client-plugin";

    private static URL notifyCommitURL;
    private static URL manageURL; // Valid URL that is not notifyCommit
    private static URL invalidURL; // Jenkins internal URL that reports page not found

    private static String urlArgument;
    private static byte[] urlArgumentBytes;

    private static String separator;
    private static byte[] separatorBytes;

    private static String branchArgument;
    private static byte[] branchArgumentBytes;

    private static String notifyCommitApiToken;
    private static byte[] notifyCommitApiTokenBytes;

    private HttpURLConnection connectionPOST;

    @BeforeAll
    static void beforeAll(JenkinsRule rule) throws Exception {
        r = rule;

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

    @BeforeEach
    void beforeEach() throws IOException {
        URL postURL = notifyCommitURL;
        connectionPOST = (HttpURLConnection) postURL.openConnection();
        connectionPOST.setRequestMethod("POST");
        connectionPOST.setDoOutput(true);
    }

    @AfterEach
    void afterEach() {
        connectionPOST.disconnect();
    }

    /*
     * POST tests.
     */
    @Test
    void testPOSTValidPathNoArgument() throws Exception {
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
        assertThat(connectionPOST.getResponseMessage(), is("Server Error"));
    }

    @Test
    void testPOSTValidPathMandatoryArgument() throws Exception {
        try (OutputStream os = connectionPOST.getOutputStream()) {
            os.write(urlArgumentBytes);
            os.write(separatorBytes);
            os.write(notifyCommitApiTokenBytes);
        }
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionPOST.getResponseMessage(), is("OK"));
    }

    @Test
    void testPOSTValidPathEmptyMandatoryArgument() throws Exception {
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
    void testPOSTValidPathBadURLInMandatoryArgument() throws Exception {
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
    void testPOSTValidPathMandatoryAndOptionalArgument() throws Exception {
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
    void testPOSTValidPathOnlyOptionalArgument() throws Exception {
        try (OutputStream os = connectionPOST.getOutputStream()) {
            os.write(branchArgumentBytes);
        }
        assertThat(connectionPOST.getResponseCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
        assertThat(connectionPOST.getResponseMessage(), is("Server Error"));
    }

    @Test
    void testPOSTInvalidPathNoArgument() throws Exception {
        URL invalidPathURL = invalidURL;
        HttpURLConnection invalidPathConnection = (HttpURLConnection) invalidPathURL.openConnection();
        invalidPathConnection.setRequestMethod("POST");
        invalidPathConnection.setDoOutput(true);
        assertThat(invalidPathConnection.getResponseCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(invalidPathConnection.getResponseMessage(), is("Forbidden"));
        invalidPathConnection.disconnect();
    }

    @Test
    void testPOSTInvalidPathMandatoryArgument() throws Exception {
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
    void testPOSTInvalidPathMandatoryAndOptionalArgument() throws Exception {
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
    void testPOSTManageNoArgument() throws Exception {
        URL postURL = manageURL;
        HttpURLConnection manageConnection = (HttpURLConnection) postURL.openConnection();
        manageConnection.setRequestMethod("POST");
        manageConnection.setDoOutput(true);
        assertThat(manageConnection.getResponseCode(), is(HttpURLConnection.HTTP_FORBIDDEN));
        assertThat(manageConnection.getResponseMessage(), is("Forbidden"));
        manageConnection.disconnect();
    }

    @Test
    void testPOSTManageMandatoryArgument() throws Exception {
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
    void testPOSTManageMandatoryAndOptionalArgument() throws Exception {
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
    void testGETValidPathNoArgument() throws Exception {
        URL getURL = notifyCommitURL;
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_INTERNAL_ERROR));
        assertThat(connectionGET.getResponseMessage(), is("Server Error"));
        connectionGET.disconnect();
    }

    @Test
    void testGETValidPathMandatoryArgument() throws Exception {
        URL getURL = new URL(notifyCommitURL + "?" + urlArgument + separator + notifyCommitApiToken);
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionGET.getResponseMessage(), is("OK"));
        connectionGET.disconnect();
    }

    @Test
    void testGETValidPathMandatoryAndOptionalArgument() throws Exception {
        URL getURL = new URL(notifyCommitURL + "?" + urlArgument + separator + branchArgument + separator + notifyCommitApiToken);
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionGET.getResponseMessage(), is("OK"));
        connectionGET.disconnect();
    }

    @Test
    void testGETInvalidPath() throws Exception {
        URL getURL = invalidURL;
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
        assertThat(connectionGET.getResponseMessage(), is("Not Found"));
        connectionGET.disconnect();
    }

    @Test
    void testGETInvalidPathWithArgument() throws Exception {
        URL getURL = new URL(invalidURL + "?" + urlArgument);
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
        assertThat(connectionGET.getResponseMessage(), is("Not Found"));
        connectionGET.disconnect();
    }

    @Test
    void testGETManagePath() throws Exception {
        URL getURL = manageURL;
        HttpURLConnection connectionGET = (HttpURLConnection) getURL.openConnection();
        connectionGET.setRequestMethod("GET");
        connectionGET.connect();
        assertThat(connectionGET.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connectionGET.getResponseMessage(), is("OK"));
        connectionGET.disconnect();
    }

    @Test
    void testGETManagePathWithArgument() throws Exception {
        URL getURL = new URL(manageURL + "?" + urlArgument);
        HttpURLConnection connection = (HttpURLConnection) getURL.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        assertThat(connection.getResponseCode(), is(HttpURLConnection.HTTP_OK));
        assertThat(connection.getResponseMessage(), is("OK"));
        connection.disconnect();
    }
}
