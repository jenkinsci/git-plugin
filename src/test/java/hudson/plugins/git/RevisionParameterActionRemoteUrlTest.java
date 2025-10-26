package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.transport.RemoteConfig;

import org.junit.jupiter.api.Test;
import org.eclipse.jgit.transport.URIish;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RevisionParameterActionRemoteUrlTest {

    @Test
    void noRemoteURLSet() throws Exception {
        RevisionParameterAction target = new RevisionParameterAction("sha1");
        URIish remoteURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        assertTrue(target.canOriginateFrom(remotes(remoteURL)), "should always return true when no remote set");
    }

    @Test
    void remoteURLSetButDoesntMatch() throws Exception {
        URIish actionURL = new URIish("https://github.com/jenkinsci/multiple-scms-plugin.git");
        RevisionParameterAction target = new RevisionParameterAction("sha1", actionURL);

        URIish remoteURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        assertFalse(target.canOriginateFrom(remotes(remoteURL)), "should return false on different remotes");
    }

    @Test
    void remoteURLSetAndMatches() throws Exception {
        URIish actionURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        RevisionParameterAction target = new RevisionParameterAction("sha1", actionURL);

        URIish remoteURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        assertTrue(target.canOriginateFrom(remotes(remoteURL)), "should return true on same remotes");
    }

    @Test
    void multipleRemoteURLsSetAndOneMatches() throws Exception {
        URIish actionURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        RevisionParameterAction target = new RevisionParameterAction("sha1", actionURL);

        URIish remoteURL1 = new URIish("https://github.com/jenkinsci/multiple-scms-plugin.git");
        URIish remoteURL2 = new URIish("https://github.com/jenkinsci/git-plugin.git");
        assertTrue(target.canOriginateFrom(remotes(remoteURL1, remoteURL2)), "should return true when any remote matches");
    }

    private List<RemoteConfig> remotes(URIish... remoteURLs) {
        List<RemoteConfig> result = new ArrayList<>();
        for (URIish remoteURL : remoteURLs) {
            result.add(remote(remoteURL));
        }
        return result;
    }

    private RemoteConfig remote(URIish remoteURL) {
        RemoteConfig result = mock(RemoteConfig.class);
        when(result.getURIs()).thenReturn(Collections.singletonList(remoteURL));
        return result;
    }
}
