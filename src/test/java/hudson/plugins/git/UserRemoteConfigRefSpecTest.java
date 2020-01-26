package hudson.plugins.git;

import hudson.util.FormValidation;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertEquals;


public class UserRemoteConfigRefSpecTest {

    @Issue("JENKINS-57660")
    @Test
    public void testdoCheckRefspec(){
        String url = "https://github.com/daytonpa/pipeline_test_example.git";
        String name = "origin";
        String refSpec = "refs/heads/master";
        UserRemoteConfig.DescriptorImpl descriptor = new UserRemoteConfig.DescriptorImpl();
        assertEquals(FormValidation.ok(), descriptor.doCheckRefspec(url, name, refSpec));
    }
}
