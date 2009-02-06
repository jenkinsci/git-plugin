package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A specification of branches to build. Rather like a refspec.
 * 
 * eg:
 * master
 * origin/master
 * origin/ *
 * origin/ * /thing
 */
public class BranchSpec
{
    private String name;
    
    public String getName()
    {
        return name;
    }

    public BranchSpec(String name)
    {
        if( name == null )
            throw new IllegalArgumentException();
        else if( name.length() == 0 )
            this.name = "*";
        else
            this.name = name;
    }
    
    public boolean matches(String item)
    {
        if( name.contains("*") )
        {
            return Pattern.matches(name.replace("*", ".*"), item);
            
        }
        else
        {
            return this.name.equals(item);
        }
    }
    
    public List<String> filterMatching(Collection<String> branches)
    {
        List<String> items = new ArrayList<String>();
        
        for(String b : branches)
        {
            if(matches(b))
                items.add(b);
        }
        
        return items;
    }
    
    public List<Branch> filterMatchingBranches(Collection<Branch> branches)
    {
        List<Branch> items = new ArrayList<Branch>();
        
        for(Branch b : branches)
        {
            if(matches(b.getName()))
                items.add(b);
        }
        
        return items;
    }
}
