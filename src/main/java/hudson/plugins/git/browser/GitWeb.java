package hudson.plugins.git.browser;

import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Git Browser URLs
 */
public class GitWeb extends RepositoryBrowser<GitChangeSet> {
	private final URL url;

	@DataBoundConstructor
	public GitWeb(String url) throws MalformedURLException {
		
		this.url = new URL(url);

		
	}

	public URL getUrl() {
		return url;
	}

	@Override
	public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {

		String queryPart = "a=commit;h=" + changeSet.getId();
		String q = url.getQuery();
		if (q == null)
			q = queryPart;
		else
			q += ";" + queryPart;
		try {
			return new URL(url, url.getPath() + "?" + q);
		} catch (MalformedURLException e) {
			// impossible
			throw new Error(e);
		}

	}

	public Descriptor<RepositoryBrowser<?>> getDescriptor() {
		return DESCRIPTOR;
	}

	public static final Descriptor<RepositoryBrowser<?>> DESCRIPTOR = new Descriptor<RepositoryBrowser<?>>(
			GitWeb.class) {
		public String getDisplayName() {
			return "gitweb";
		}

		public GitWeb newInstance(StaplerRequest req) throws FormException {
			return req.bindParameters(GitWeb.class, "gitweb.");
		}
	};

	private static final long serialVersionUID = 1L;
}
