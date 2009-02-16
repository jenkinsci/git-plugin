package hudson.plugins.git.util;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.Action;
import hudson.plugins.git.GitException;
import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;
import hudson.util.XStream2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.spearce.jgit.lib.ObjectId;

public class BuildData implements Action, Serializable
{
    public Map<String, ObjectId> lastBuiltIds = new HashMap<String, ObjectId>();
    public Revision              lastBuiltRevision;
    
    public void save(FilePath storageFile)
    {
        try
        {
            storageFile.act(new FileCallable<Void>()
            {
                public Void invoke(File file, VirtualChannel channel) throws IOException
                {
                    if (!file.exists()) file.createNewFile();

                    XStream2 xstream = new XStream2();
                    FileOutputStream fos = new FileOutputStream(file);
                    BuildInfo s = new BuildInfo();
                    s.setVersion(1);
                    s.setLastBuiltIds(lastBuiltIds);
                    s.setLastBuiltRevision(lastBuiltRevision);
                    xstream.toXML(s, fos);
                    fos.close();
                    return null;
                }
            });
        }
        catch (Exception ex)
        {
            throw new GitException("Couldn't save build state", ex);
        }
    }

    public void load(FilePath storageFile)
    {
        try
        {
            storageFile.act(new FileCallable<Void>()
            {
                public Void invoke(File file, VirtualChannel channel) throws IOException
                {
                    try
                    {
                        InputStream is = new FileInputStream(file);
                        XStream2 xstream = new XStream2();
                        BuildInfo buildInfo = (BuildInfo) xstream.fromXML(is);
                        lastBuiltIds = buildInfo.getLastBuiltIds();
                        lastBuiltRevision = buildInfo.getLastBuiltRevision();
                        is.close();
                    }
                    catch (Exception ex)
                    {
                        // File not found, defective or corrupt.

                        lastBuiltIds = new HashMap<String, ObjectId>();
                        lastBuiltRevision = null;
                    }
                    return null;
                }
            });
        }
        catch (Exception e)
        {
            throw new GitException("Couldn't load build state", e);
        }
    }
    
    public String getDisplayName()
    {
        return "Git Changeset";
    }
    public String getIconFileName()
    {
        return null;
    }
    public String getUrlName()
    {
        return null;
    }
}
