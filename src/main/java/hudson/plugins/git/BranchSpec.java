package hudson.plugins.git;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A specification of branches to build. Rather like a refspec.
 *
 * eg:
 * <pre>
 * master
 * origin/master
 * origin/&#42;
 * origin/&#42;/thing
 * </pre>
 */
@ExportedBean
public class BranchSpec extends AbstractDescribableImpl<BranchSpec> implements Serializable {
    private static final long serialVersionUID = -6177158367915899356L;

    private String name;

    @Exported
    public String getName() {
        return name;
    }

    public void setName(String name) {
    	if(name == null)
            throw new IllegalArgumentException();
        else if(name.length() == 0)
            this.name = "**";
        else
            this.name = name.trim();
    }

    @DataBoundConstructor
    public BranchSpec(String name) {
        setName(name);
    }

    public String toString() {
        return name;
    }

    public boolean matches(String item) {
        EnvVars env = new EnvVars();
        return matches(item, env);
    }

    /**
     * Compare a git branch reference to configured pattern.
     * <p>
     * reference uses normalized format `ref/(heads|tags)/xx`
     * pattern do support
     * <ul>
     *     <li>ref/heads/branch</li>
     *     <li>(remote-name)?/branch</li>
     *     <li>ref/remotes/branch</li>
     *     <li>tag</li>
     *     <li>(commit sha1)</li>
     * </ul>
     * @param ref branch reference to compare
     * @param env environment variables to use in comparison
     * @return true if ref matches configured pattern
     */
    public boolean matches(String ref, EnvVars env) {
        return getPattern(env).matcher(ref).matches();
    }

    /**
     * Compare the configured pattern to a git branch defined by the repository name and branch name.
     * @param repositoryName git repository name
     * @param branchName git branch name
     * @return true if repositoryName/branchName matches this BranchSpec
     */
    public boolean matchesRepositoryBranch(String repositoryName, String branchName) {
        if (branchName == null) {
            return false;
        }
        Pattern pattern = getPattern(new EnvVars(), repositoryName);
        String branchWithoutRefs = cutRefs(branchName);
        return pattern.matcher(branchWithoutRefs).matches() || pattern.matcher(join(repositoryName, branchWithoutRefs)).matches();
    }

    /**
     * @deprecated use {@link #filterMatching(Collection, EnvVars)}
     * @param branches source branch list to be filtered by configured branch specification using a newly constructed EnvVars
     * @return branch names which match
     */
    public List<String> filterMatching(Collection<String> branches) {
        EnvVars env = new EnvVars();
        return filterMatching(branches, env);
    }

    public List<String> filterMatching(Collection<String> branches, EnvVars env) {
        List<String> items = new ArrayList<>();
        
        for(String b : branches) {
            if(matches(b, env))
                items.add(b);
        }
        
        return items;
    }
    
    public List<Branch> filterMatchingBranches(Collection<Branch> branches) {
        EnvVars env = new EnvVars();
        return filterMatchingBranches(branches, env);
    }

    public List<Branch> filterMatchingBranches(Collection<Branch> branches, EnvVars env) {
        List<Branch> items = new ArrayList<>();
        
        for(Branch b : branches) {
            if(matches(b.getName(), env))
                items.add(b);
        }
        
        return items;
    }

    private String getExpandedName(EnvVars env) {
        String expandedName = env.expand(name);
        if (expandedName.length() == 0) {
            return "**";
        }
        return expandedName;
    }

    private Pattern getPattern(EnvVars env) {
        return getPattern(env, null);
    }

    private Pattern getPattern(EnvVars env, String repositoryName) {
        String expandedName = getExpandedName(env);
        // use regex syntax directly if name starts with colon
        if (expandedName.startsWith(":") && expandedName.length() > 1) {
            String regexSubstring = expandedName.substring(1, expandedName.length());
            return Pattern.compile(regexSubstring);
        }
        if (repositoryName != null) {
            // remove the "refs/.../" stuff from the branch-spec if necessary
            String pattern = cutRefs(expandedName)
                    // remove a leading "remotes/" from the branch spec
                    .replaceAll("^remotes/", "");
            pattern = convertWildcardStringToRegex(pattern);
            return Pattern.compile(pattern);
        }

        // build a pattern into this builder
        StringBuilder builder = new StringBuilder();

        // for legacy reasons (sic) we do support various branch spec format to declare remotes / branches
        builder.append("(refs/heads/");


        // if an unqualified branch was given, consider all remotes (with various possible syntaxes)
        // so it will match branches from  any remote repositories as the user probably intended
        if (!expandedName.contains("**") && !expandedName.contains("/")) {
            builder.append("|refs/remotes/[^/]+/|remotes/[^/]+/|[^/]+/");
        } else {
            builder.append("|refs/remotes/|remotes/");
        }
        builder.append(")?");
        builder.append(convertWildcardStringToRegex(expandedName));
        return Pattern.compile(builder.toString());
    }

    private String convertWildcardStringToRegex(String expandedName) {
        StringBuilder builder = new StringBuilder();

        // was the last token a wildcard?
        boolean foundWildcard = false;
        
        // split the string at the wildcards
        StringTokenizer tokenizer = new StringTokenizer(expandedName, "*", true);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            
            // is this token is a wildcard?
            if (token.equals("*")) {
                // yes, was the previous token a wildcard?
                if (foundWildcard) {
                    // yes, we found "**"
                    // match over any number of characters
                    builder.append(".*");
                    foundWildcard = false;
                }
                else {
                    // no, set foundWildcard to true and go on
                    foundWildcard = true;
                }
            }
            else {
                // no, was the previous token a wildcard?
                if (foundWildcard) {
                    // yes, we found "*" followed by a non-wildcard
                    // match any number of characters other than a "/"
                    builder.append("[^/]*");
                    foundWildcard = false;
                }
                // quote the non-wildcard token before adding it to the phrase
                builder.append(Pattern.quote(token));
            }
        }
        
        // if the string ended with a wildcard add it now
        if (foundWildcard) {
            builder.append("[^/]*");
        }
        return builder.toString();
    }

    private String cutRefs(@NonNull String name) {
        Matcher matcher = GitSCM.GIT_REF.matcher(name);
        return matcher.matches() ? matcher.group(2) : name;
    }

    private String join(String repositoryName, String branchWithoutRefs) {
        return StringUtils.join(Arrays.asList(repositoryName, branchWithoutRefs), "/");
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<BranchSpec> {
        @Override
        public String getDisplayName() {
            return "Branch Spec";
        }
    }
}
