package jenkins.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BitbucketOAuthTokenClientTest {

    private static final URI TOKEN_ENDPOINT = URI.create("https://bitbucket.example/token");

    @Test
    void shouldRequestAndParseClientCredentialsToken() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"access_token\":\"returned-token\",\"expires_in\":3600}");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(client.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        Instant beforeRequest = Instant.now();
        BitbucketOAuthTokenClient.CachedToken token =
                BitbucketOAuthTokenClient.requestToken(client, TOKEN_ENDPOINT, "client-key", "client-secret");

        assertThat(token.value(), is("returned-token"));
        assertThat(token.expiresAt(), greaterThan(beforeRequest.plusSeconds(3500)));

        HttpRequest request = requestCaptor.getValue();
        String expectedBasic = Base64.getEncoder().encodeToString("client-key:client-secret".getBytes());
        assertThat(request.uri(), is(TOKEN_ENDPOINT));
        assertThat(request.method(), is("POST"));
        assertThat(request.headers().firstValue("Authorization").orElse(""), is("Basic " + expectedBasic));
        assertThat(
                request.headers().firstValue("Content-Type").orElse(""),
                is("application/x-www-form-urlencoded"));
    }

    @Test
    void shouldRejectFailedTokenRequestWithoutExposingResponseBody() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.body()).thenReturn("response-that-may-contain-sensitive-data");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> BitbucketOAuthTokenClient.requestToken(
                        client, TOKEN_ENDPOINT, "client-key", "client-secret"));

        assertThat(exception.getMessage(), containsString("HTTP 401"));
        assertThat(exception.getMessage().contains("sensitive-data"), is(false));
    }

    @Test
    void shouldRejectSuccessfulResponseWithoutAccessToken() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"expires_in\":3600}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> BitbucketOAuthTokenClient.requestToken(
                        client, TOKEN_ENDPOINT, "client-key", "client-secret"));

        assertThat(exception.getMessage(), containsString("did not include an access token"));
    }
}
