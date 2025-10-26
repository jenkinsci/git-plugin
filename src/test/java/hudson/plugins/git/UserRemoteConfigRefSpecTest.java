package hudson.plugins.git;

import hudson.util.FormValidation;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


class UserRemoteConfigRefSpecTest {

    @Issue("JENKINS-57660")
    @Test
    void testdoCheckRefspecSuccessWithoutWildcards(){
        String url = "git://git.example.com/repository-that-does-not-exist";
        String name = "origin";
        List<String> refSpec = new ArrayList<>();
        refSpec.add("+refs/heads/master:refs/remotes/origin/master");
        refSpec.add("+refs/heads/JENKINS-57660:refs/remotes/origin/JENKINS-57660");
        refSpec.add("master:refs/remotes/origin/mymaster");
        refSpec.add("master:refs/remotes/origin/mymaster topic:refs/remotes/origin/topic");

        UserRemoteConfig.DescriptorImpl descriptor = new UserRemoteConfig.DescriptorImpl();
        for (String ref:refSpec) {
            assertEquals(FormValidation.ok(), descriptor.doCheckRefspec(url, name, ref));
        }
    }

    @Issue("JENKINS-57660")
    @Test
    void testdoCheckRefspecSuccessWithMatchedWildCards(){
        String url = "git://git.example.com/repository-that-does-not-exist";
        String name = "origin";
        List<String> refSpec = new ArrayList<>();
        refSpec.add("+refs/heads/qa/*:refs/remotes/origin/qa/*");
        refSpec.add("+refs/pull/*/head:refs/remotes/origin/pr/*");

        UserRemoteConfig.DescriptorImpl descriptor = new UserRemoteConfig.DescriptorImpl();
        for (String ref:refSpec) {
            assertEquals(FormValidation.ok(), descriptor.doCheckRefspec(url, name, ref));
        }
    }

    @Issue("JENKINS-57660")
    @Test
    void testdoCheckRefspecFailureWithUnMatchedWildCards(){
        String url = "git://git.example.com/repository-that-does-not-exist";
        String name = "origin";
        List<String> refSpec = new ArrayList<>();
        refSpec.add("+refs/heads/qa:refs/remotes/origin/qa/*");
        refSpec.add("+refs/heads/qa/*:refs/remotes/origin/qa");

        UserRemoteConfig.DescriptorImpl descriptor = new UserRemoteConfig.DescriptorImpl();
        for (String ref:refSpec) {
            assertEquals("Specification is invalid.",
                    descriptor.doCheckRefspec(url, name, ref).getLocalizedMessage());
        }
    }

    @Issue("JENKINS-57660")
    @Test
    void testdoCheckRefspecSuccessWithEmptyString(){
        String url = "git://git.example.com/repository-that-does-not-exist";
        String name = "origin";
        List<String> refSpec = new ArrayList<>();
        refSpec.add("");

        UserRemoteConfig.DescriptorImpl descriptor = new UserRemoteConfig.DescriptorImpl();
        for (String ref:refSpec) {
            assertEquals(FormValidation.ok(),
                    descriptor.doCheckRefspec(url, name, ref));
        }
    }

    @Issue("JENKINS-57660")
    @Test
    void testdoCheckRefspecSuccessWithPartialGlobs(){
        String url = "git://git.example.com/repository-that-does-not-exist";
        String name = "origin";
        List<String> refSpec = new ArrayList<>();
        refSpec.add("+refs/heads/qa*:refs/remotes/origin/qa*");
        UserRemoteConfig.DescriptorImpl descriptor = new UserRemoteConfig.DescriptorImpl();
        for (String ref:refSpec) {
            assertEquals(FormValidation.ok(),
                    descriptor.doCheckRefspec(url, name, ref));
        }
    }

    @Issue("JENKINS-57660")
    @Test
    void testdoCheckRefspecSuccessWithEnvironmentVariable(){
        String url = "git://git.example.com/repository-that-does-not-exist";
        String name = "origin";
        List<String> refSpec = new ArrayList<>();
        refSpec.add("$REFSPEC");
        UserRemoteConfig.DescriptorImpl descriptor = new UserRemoteConfig.DescriptorImpl();
        for (String ref:refSpec) {
            assertEquals(FormValidation.ok(),
                    descriptor.doCheckRefspec(url, name, ref));
        }
    }
}
