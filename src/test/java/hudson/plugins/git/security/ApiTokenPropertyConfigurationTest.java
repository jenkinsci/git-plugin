package hudson.plugins.git.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.plugins.git.ApiTokenPropertyConfiguration;
import java.util.Collection;
import java.util.Collections;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class ApiTokenPropertyConfigurationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void init() {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
        authorizationStrategy.grant(Jenkins.ADMINISTER).everywhere().to("alice");
        authorizationStrategy.grant(Jenkins.READ).everywhere().to("bob");
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);
    }

    @Test
    public void testAdminPermissionRequiredToGenerateNewApiTokens() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("bob");
            WebRequest req = new WebRequest(
                    wc.createCrumbedUrl(ApiTokenPropertyConfiguration.get().getDescriptorUrl() + "/generate"),
                    HttpMethod.POST);
            req.setRequestBody("{\"apiTokenName\":\"test\"}");
            wc.setThrowExceptionOnFailingStatusCode(false);

            WebResponse res = wc.getPage(req).getWebResponse();

            assertEquals(403, res.getStatusCode());
            assertTrue(res.getContentAsString().contains("bob is missing the Overall/Administer permission"));
        }
    }

    @Test
    public void adminPermissionsRequiredToRevokeApiTokens() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("bob");
            WebRequest req = new WebRequest(
                    wc.createCrumbedUrl(ApiTokenPropertyConfiguration.get().getDescriptorUrl() + "/revoke"),
                    HttpMethod.POST);
            wc.setThrowExceptionOnFailingStatusCode(false);

            WebResponse res = wc.getPage(req).getWebResponse();

            assertEquals(403, res.getStatusCode());
            assertTrue(res.getContentAsString().contains("bob is missing the Overall/Administer permission"));
        }
    }

    @Test
    public void testBasicGenerationAndRevocation() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");
            WebRequest generateReq = new WebRequest(
                    wc.createCrumbedUrl(ApiTokenPropertyConfiguration.get().getDescriptorUrl() + "/generate"),
                    HttpMethod.POST);
            generateReq.setRequestParameters(Collections.singletonList(new NameValuePair("apiTokenName", "token")));
            String uuid = JSONObject.fromObject(
                            wc.getPage(generateReq).getWebResponse().getContentAsString())
                    .getJSONObject("data")
                    .getString("uuid");

            generateReq.setRequestParameters(Collections.singletonList(new NameValuePair("apiTokenName", "nekot")));
            String uuid2 = JSONObject.fromObject(
                            wc.getPage(generateReq).getWebResponse().getContentAsString())
                    .getJSONObject("data")
                    .getString("uuid");

            Collection<ApiTokenPropertyConfiguration.HashedApiToken> apiTokens =
                    ApiTokenPropertyConfiguration.get().getApiTokens();
            assertThat(
                    apiTokens,
                    allOf(
                            iterableWithSize(2),
                            hasItem(allOf(hasProperty("name", is("token")), hasProperty("uuid", is(uuid)))),
                            hasItem(allOf(hasProperty("name", is("nekot")), hasProperty("uuid", is(uuid2))))));

            WebRequest revokeReq = new WebRequest(
                    wc.createCrumbedUrl(ApiTokenPropertyConfiguration.get().getDescriptorUrl() + "/revoke"),
                    HttpMethod.POST);
            revokeReq.setRequestParameters(Collections.singletonList(new NameValuePair("apiTokenUuid", uuid)));
            wc.getPage(revokeReq);

            apiTokens = ApiTokenPropertyConfiguration.get().getApiTokens();
            assertThat(
                    apiTokens,
                    allOf(
                            iterableWithSize(1),
                            hasItem(allOf(hasProperty("name", is("nekot")), hasProperty("uuid", is(uuid2))))));
        }
    }

    @Test
    public void isValidApiTokenReturnsTrueIfGivenApiTokenExists() {
        JSONObject json = ApiTokenPropertyConfiguration.get().generateApiToken("test");

        assertTrue(ApiTokenPropertyConfiguration.get().isValidApiToken(json.getString("value")));
    }
}
