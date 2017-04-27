package hudson.plugins.git;

import hudson.security.csrf.CrumbFilter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Collections;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jvnet.hudson.test.JenkinsRule;

public class GitStatusCrumbExclusionTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private CrumbFilter filter;
    private HttpServletRequest req;
    private HttpServletResponse resp;
    private FilterChain chain;

    @Before
    public void before() {
        filter = new CrumbFilter();
        req = mock(HttpServletRequest.class);
        resp = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    public void testNotifyCommit() throws Exception {
        when(req.getPathInfo()).thenReturn("/git/notifyCommit");
        when(req.getMethod()).thenReturn("POST");
        when(req.getParameterNames()).thenReturn(Collections.<String>emptyEnumeration());
        filter.doFilter(req, resp, chain);
        verify(resp, never()).sendError(anyInt(), anyString());
    }

    @Test
    public void testInvalidPath() throws Exception {
        when(req.getPathInfo()).thenReturn("/git/somethingElse");
        when(req.getMethod()).thenReturn("POST");
        when(req.getParameterNames()).thenReturn(Collections.<String>emptyEnumeration());
        filter.doFilter(req, resp, chain);
        verify(resp, times(1)).sendError(anyInt(), anyString());
    }
}
