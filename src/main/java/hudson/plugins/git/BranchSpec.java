package hudson.plugins.git;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

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

    public boolean matches(String item, EnvVars env) {
        return getPattern(env).matcher(item).matches();
    }

    /**
     * @deprecated use filterMatching(Collection<String>, EnvVars)
     */
    public List<String> filterMatching(Collection<String> branches) {
        EnvVars env = new EnvVars();
        return filterMatching(branches, env);
    }

    public List<String> filterMatching(Collection<String> branches, EnvVars env) {
        List<String> items = new ArrayList<String>();
        
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
        List<Branch> items = new ArrayList<Branch>();
        
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
        String expandedName = getExpandedName(env);
        // use regex syntax directly if name starts with colon
        if (expandedName.startsWith(":") && expandedName.length() > 1) {
            String regexSubstring = expandedName.substring(1, expandedName.length());
            return Pattern.compile(regexSubstring);
        }
        
        // if an unqualified branch was given add a "*/" so it will match branches
        // from remote repositories as the user probably intended
        String qualifiedName;
        if (!expandedName.contains("**") && !expandedName.contains("/"))
            qualifiedName = "*/" + expandedName;
        else
            qualifiedName = expandedName;
        
        // build a pattern into this builder
        StringBuilder builder = new StringBuilder();

        // for legacy reasons (sic) we do support various branch spec format to declare remotes / branches
        builder.append("(refs/heads/|refs/remotes/|remotes/)?");
        
        // was the last token a wildcard?
        boolean foundWildcard = false;
        
        // split the string at the wildcards
        StringTokenizer tokenizer = new StringTokenizer(qualifiedName, "*", true);
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
        
        return Pattern.compile(builder.toString());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<BranchSpec> {
        @Override
        public String getDisplayName() {
            return "Branch Spec";
        }
    }
}
