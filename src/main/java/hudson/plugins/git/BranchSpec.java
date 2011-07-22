package hudson.plugins.git;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A specification of branches to build. Rather like a refspec.
 * 
 * eg:
 * master
 * origin/master
 * origin/ *
 * origin/ * /thing
 */
public class BranchSpec implements Serializable {
    private static final long serialVersionUID = -6177158367915899356L;

    private String name;
    private transient Pattern pattern;
    
    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    @DataBoundConstructor
    public BranchSpec(String name) {
        if(name == null)
            throw new IllegalArgumentException();
        else if(name.length() == 0)
            this.name = "**";
        else
            this.name = name;
    }
    
    public boolean matches(String item) {
        return getPattern().matcher(item).matches();
    }
    
    public List<String> filterMatching(Collection<String> branches) {
        List<String> items = new ArrayList<String>();
        
        for(String b : branches) {
            if(matches(b))
                items.add(b);
        }
        
        return items;
    }
    
    public List<Branch> filterMatchingBranches(Collection<Branch> branches) {
        List<Branch> items = new ArrayList<Branch>();
        
        for(Branch b : branches) {
            if(matches(b.getName()))
                items.add(b);
        }
        
        return items;
    }
    
    private Pattern getPattern() {
        // return the saved pattern if available
        if (pattern != null)
            return pattern;
        
        // if an unqualified branch was given add a "*/" so it will match branches
        // from remote repositories as the user probably intended
        String qualifiedName;
        if (!name.contains("**") && !name.contains("/"))
            qualifiedName = "*/" + name;
        else
            qualifiedName = name;
        
        // build a pattern into this builder
        StringBuilder builder = new StringBuilder();
        
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
        
        // save the pattern
        pattern = Pattern.compile(builder.toString());
        
        return pattern;
    }
}
