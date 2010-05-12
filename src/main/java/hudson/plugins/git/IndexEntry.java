package hudson.plugins.git;

/**
 * An Entry in the Index / Tree
 * 
 * @author nigelmagnay
 */
public class IndexEntry {
    String mode, type, object, file;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String toString() {
        return file;
    }
  
    public IndexEntry(String mode, String type, String object, String file) {
        this.mode = mode;
        this.type = type;
        this.file = file;
        this.object = object;
    }

}
