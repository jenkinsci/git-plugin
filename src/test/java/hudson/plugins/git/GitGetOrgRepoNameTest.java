package hudson.plugins.git;

import hudson.plugins.git.util.BuildData;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

public class GitGetOrgRepoNameTest {

	class Url {
		String url;
		String testOrgName;
		String testRepoName;

		public Url(String url,String orgName,String repoName){
			this.url = url;
			this.testOrgName = orgName;
			this.testRepoName = repoName;
		}
	}

	@Test
	public void testOrgRepoName(){
//		GitSCM.DescriptorImpl globalConfig = new GitSCM.DescriptorImpl();
		String globalRegex = ".*(@|\\/\\/).*?(\\/|:)(?<group>.*?)\\/(?<repo>.*)$";
//		globalConfig.setGlobalUrlRegEx("");
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

		for(Url url : urls){
			String repoName = data.getRepoName(url.url,globalRegex);
			String orgName = data.getOrganizationName(url.url,globalRegex);
			assertEquals(orgName,url.testOrgName);
			assertEquals(repoName,url.testRepoName);
		}
	}
}
