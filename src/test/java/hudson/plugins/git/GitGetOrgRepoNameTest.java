package hudson.plugins.git;

import hudson.plugins.git.util.BuildData;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.ArrayList;
import static org.junit.Assert.assertEquals;

public class GitGetOrgRepoNameTest {

	class Url {
		String remoteUrl;
		String testOrgName;
		String testRepoName;

		public Url(String remoteUrl,String testOrgName,String testRepoName){
			this.remoteUrl = remoteUrl;
			this.testOrgName = testOrgName;
			this.testRepoName = testRepoName;
		}
	}

	@Test
	public void testOrgRepoName() throws MalformedURLException {
		String globalRegex = "(.*github.*?[/:](?<org>.*)/(?<repo>.*))&&&(.*gitlab.*?[/:](?<org>.*)/(?<repo>.*))&&&(.*?//(?<org>\\w+).*visualstudio.*?/(?<repo>.*))&&&(.*bitbucket.*?[/:](?<org>.*)/(?<repo>.*))" +
				"&&&(.*assembla.*?[/:](?<repo>.*))";
		BuildData data = new BuildData();

		ArrayList<Url> urls = new ArrayList<>();
		urls.add(new Url("git@bitbucket.org:markewaite/tasks.git","markewaite","tasks.git"));
		urls.add(new Url("git@bitbucket.org:markewaite/bin.git","markewaite","bin.git"));
		urls.add(new Url("https://markewaite@bitbucket.org/markewaite/tasks.git","markewaite","tasks.git"));
		urls.add(new Url("https://markewaite@bitbucket.org/markewaite/git-client-plugin.git","markewaite","git-client-plugin.git"));
		urls.add(new Url("https://markewaite@bitbucket.org/markewaite/bin.git","markewaite","bin.git"));
		urls.add(new Url("https://MarkEWaite:also-a-password@gitlab.com/MarkEWaite/tasks.git","MarkEWaite","tasks.git"));
		urls.add(new Url("https://MarkEWaite:another-password@github.com/MarkEWaite/tasks.git","MarkEWaite","tasks.git"));
		urls.add(new Url("https://MarkEWaite:yes-this-is-a-password@github.com/MarkEWaite/bin.git","MarkEWaite","bin.git"));
		urls.add(new Url("https://gitlab.com/MarkEWaite/tasks.git","MarkEWaite","tasks.git"));
		urls.add(new Url("https://gitlab.com/MarkEWaite/tasks","MarkEWaite","tasks"));
		urls.add(new Url("https://gitlab.com/MarkEWaite/bin","MarkEWaite","bin"));
		urls.add(new Url("https://github.com/MarkEWaite/tasks.git","MarkEWaite","tasks.git"));
		urls.add(new Url("git@github.com:MarkEWaite/bin.git","MarkEWaite","bin.git"));
		urls.add(new Url("git@gitlab.com:MarkEWaite/tasks.git","MarkEWaite","tasks.git"));
		urls.add(new Url("git@github.com:MarkEWaite/tasks.git","MarkEWaite","tasks.git"));
		urls.add(new Url("https://bitbucket.org/markewaite/bin.git","markewaite","bin.git"));
		urls.add(new Url("https://bitbucket.org/markewaite/git-client-plugin.git","markewaite","git-client-plugin.git"));
		urls.add(new Url("https://bitbucket.org/markewaite/tasks.git","markewaite","tasks.git"));
		urls.add(new Url("https://github.com/MarkEWaite/bin.git","MarkEWaite","bin.git"));
		urls.add(new Url("https://markwaite.visualstudio.com/_git/elisp","markwaite","_git/elisp"));
		urls.add(new Url("https://markwaite.visualstudio.com/DefaultCollection/_git/","markwaite","DefaultCollection/_git/"));
		urls.add(new Url("https://markwaite.visualstudio.com/DefaultCollection/elisp/_git/elisp","markwaite","DefaultCollection/elisp/_git/elisp"));
		urls.add(new Url("https://github.com/MarkEWaite/bin.git","MarkEWaite","bin.git"));
		urls.add(new Url("https://github.com/MarkEWaite/bin.git","MarkEWaite","bin.git"));

		urls.add(new Url("https://www.assembla.com/spaces/git-plugin/git-2/",null,"spaces/git-plugin/git-2/"));
		urls.add(new Url("https://git.assembla.com/git-plugin.bin.git",null,"git-plugin.bin.git"));
		urls.add(new Url("git@git.assembla.com:git-plugin.bin.git",null,"git-plugin.bin.git"));

		for(Url url : urls){
			assertEquals(data.getOrganizationName(url.remoteUrl,globalRegex),url.testOrgName);
			assertEquals(data.getRepoName(url.remoteUrl,globalRegex),url.testRepoName);
		}
	}
}
