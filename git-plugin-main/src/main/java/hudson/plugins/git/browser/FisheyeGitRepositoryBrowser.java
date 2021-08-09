package hudson.plugins.git.browser;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSet.Path;
import hudson.scm.EditType;
import hudson.scm.RepositoryBrowser;
import hudson.util.FormValidation;
import hudson.util.FormValidation.URLCheck;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;

public class FisheyeGitRepositoryBrowser extends GitRepositoryBrowser {

	private static final long serialVersionUID = 2881872624557203410L;

	@DataBoundConstructor
	public FisheyeGitRepositoryBrowser(String repoUrl) {
        super(repoUrl);
	}

	@Override
	public URL getDiffLink(Path path) throws IOException {
		if (path.getEditType() != EditType.EDIT)
			return null; // no diff if this is not an edit change
		String r1 = path.getChangeSet().getParentCommit();
		String r2 = path.getChangeSet().getId();
		return new URL(getUrl(), getPath(path) + String.format("?r1=%s&r2=%s", r1, r2));
	}

	@Override
	public URL getFileLink(Path path) throws IOException {
		return encodeURL(new URL(getUrl(), getPath(path)));
	}

	private String getPath(Path path) {
		return trimHeadSlash(path.getPath());
	}

	/**
	 * Pick up "FOOBAR" from "http://site/browse/FOOBAR/"
	 */
	private String getProjectName() throws IOException {
		String p = getUrl().getPath();
		if (p.endsWith("/"))
			p = p.substring(0, p.length() - 1);

		int idx = p.lastIndexOf('/');
		return p.substring(idx + 1);
	}

	@Override
	public URL getChangeSetLink(GitChangeSet changeSet) throws IOException {
		return new URL(getUrl(), "../../changelog/" + getProjectName() + "?cs=" + changeSet.getId());
	}

	@Extension
	public static class FisheyeGitRepositoryBrowserDescriptor extends Descriptor<RepositoryBrowser<?>> {

		@NonNull
		public String getDisplayName() {
			return "FishEye";
		}

		@Override
		public FisheyeGitRepositoryBrowser newInstance(StaplerRequest req, @NonNull JSONObject jsonObject) throws FormException {
			assert req != null; //see inherited javadoc
			return req.bindJSON(FisheyeGitRepositoryBrowser.class, jsonObject);
		}

		/**
		 * Performs on-the-fly validation of the URL.
                 * @param value URL value to be checked
                 * @return form validation result
                 * @throws IOException on input or output error
                 * @throws ServletException on servlet error
		 */
		@RequirePOST
		public FormValidation doCheckRepoUrl(@QueryParameter(fixEmpty = true) String value) throws IOException,
				ServletException {
			if (value == null) // nothing entered yet
				return FormValidation.ok();

			if (!value.endsWith("/"))
				value += '/';
			if (!URL_PATTERN.matcher(value).matches())
				return FormValidation.errorWithMarkup("The URL should end like <code>.../browse/foobar/</code>");

			// Connect to URL and check content only if we have admin permission
			if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER))
				return FormValidation.ok();

			final String finalValue = value;
			return new URLCheck() {
				@Override
				protected FormValidation check() throws IOException, ServletException {
					try {
						if (findText(open(new URL(finalValue)), "FishEye")) {
							return FormValidation.ok();
						} else {
							return FormValidation.error("This is a valid URL but it doesn't look like FishEye");
						}
					} catch (IOException e) {
						return handleIOException(finalValue, e);
					}
				}
			}.check();
		}

		private static final Pattern URL_PATTERN = Pattern.compile(".+/browse/[^/]+/");

	}
}
