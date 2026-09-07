# Bitbucket Cloud OAuth support

## Purpose

This branch adds Bitbucket Cloud OAuth consumer authentication to the Jenkins Git plugin.
The behavior is deliberately narrow: it applies only to Git-over-HTTPS remotes hosted at
`bitbucket.org`.

GitHub, GitLab, Bitbucket Server, SSH remotes, app passwords, and credentials that already
contain an access token continue through the existing Git plugin credential path unchanged.

## Credential flow

Configure a Jenkins username/password credential with:

- username: the Bitbucket OAuth consumer key;
- password: the Bitbucket OAuth consumer secret.

For an `https://bitbucket.org/<workspace>/<repository>.git` remote, the plugin:

1. Recognizes the credential as a Bitbucket OAuth consumer key/secret pair.
2. Requests an access token from `https://bitbucket.org/site/oauth2/access_token` with the
   OAuth client-credentials grant.
3. Caches the short-lived token and refreshes it before expiration.
4. Supplies Git with a transient username/password credential using the required
   `x-token-auth` username and the access token as its password.

The token is not inserted into or persisted in the repository URL. OAuth error responses and
secrets are not written to the Jenkins log.

## Scope safeguards

OAuth conversion requires all of the following:

- the remote uses HTTPS;
- the host is exactly `bitbucket.org`;
- the selected credential is a username/password credential;
- the username and password match Bitbucket's OAuth consumer key/secret shape.

If any condition is false, the original credential is returned unchanged. In particular,
`ssh://git@bitbucket.org/...` and `git@bitbucket.org:...` never invoke the OAuth exchange.

## Implementation

- `BitbucketOAuthHelper` classifies the remote and credential, then creates the transient Git
  credential.
- `BitbucketOAuthTokenClient` performs and caches the client-credentials token exchange.
- `GitSCM` applies the helper to checkout and polling credentials.
- `UserRemoteConfig` applies the same behavior to the repository URL validation command.

## Tests

Run the focused tests:

```bash
mvn -Dtest=BitbucketOAuthHelperTest,BitbucketOAuthTokenClientTest test
```

The tests cover:

- Bitbucket Cloud remote recognition;
- OAuth consumer conversion;
- non-Bitbucket providers;
- Bitbucket app-password and access-token credentials;
- Bitbucket SSH and SCP-style remotes;
- token request method and authorization header;
- successful token response parsing;
- HTTP authentication failures without response-body disclosure;
- successful responses missing an access token.

An end-to-end Jenkins verification should select an OAuth consumer credential for a private
Bitbucket Cloud HTTPS repository and confirm both repository validation and checkout. The OAuth
consumer must include repository read permission.
