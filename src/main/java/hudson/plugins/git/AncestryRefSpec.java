package hudson.plugins.git;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

public class AncestryRefSpec implements Serializable {

	private static final long serialVersionUID = 508278084583920082L;

    private String name;

    public String getName() {
        return name;
    }
    
    public void setName(String name) {
    	if(name == null)
            throw new IllegalArgumentException();
        else
            this.name = name.trim();
    }
    
    @DataBoundConstructor
    public AncestryRefSpec(String name) {
    	setName(name);
    }
}
