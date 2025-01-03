package jenkins.plugins.git.maintenance.Logs;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.security.AnyTypePermission;
import jenkins.model.Jenkins;
import jenkins.plugins.git.maintenance.GitMaintenanceSCM;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class XmlSerialize{

    XStream xStream;

    File maintenanceRecordsFile;

    RecordList recordList;

    String maintenanceFileName = "maintenanceRecords.xml";
    private static Logger LOGGER = Logger.getLogger(GitMaintenanceSCM.class.getName());

    public XmlSerialize(){
        this.xStream = new XStream(new DomDriver());
        // Need to change the Permission type. Todo need to read documentation and update security.
        this.xStream.addPermission(AnyTypePermission.ANY);
        Jenkins jenkins = Jenkins.getInstanceOrNull();

        if(jenkins != null) {
            File rootDir = jenkins.getRootDir();
            this.maintenanceRecordsFile = new File(rootDir.getAbsolutePath(), maintenanceFileName);
        }
    }

    RecordList fetchMaintenanceData(){
        if(maintenanceRecordsFile == null){
            // Need to log.....
            LOGGER.log(Level.FINE,maintenanceFileName + " file path error.");
            return null;
        }

        // Checks if recordList is loaded from xml. If not loaded, load it.
        if(recordList == null) {
            try {
                RecordList recordList;
                if (!maintenanceRecordsFile.exists()) {
                    recordList = new RecordList();
                    LOGGER.log(Level.FINE,maintenanceFileName + " file doesn't exist");
                } else {
                    byte[] parsedXmlByteArr = Files.readAllBytes(Paths.get(maintenanceRecordsFile.getAbsolutePath()));
                    String parsedXmlString = new String(parsedXmlByteArr, StandardCharsets.UTF_8);

                    xStream.setClassLoader(RecordList.class.getClassLoader());
                    recordList = (RecordList) xStream.fromXML(parsedXmlString);
                    LOGGER.log(Level.FINE,"Maintenance data loaded from " + maintenanceFileName);
                }
                this.recordList = recordList;
            } catch (IOException e) {
                LOGGER.log(Level.FINE,"Couldn't load data from " + maintenanceFileName + ". Err: " + e.getMessage());
            }
        }

        return this.recordList;
    }

    public boolean addMaintenanceRecord(CacheRecord record){
        RecordList recordList = fetchMaintenanceData();

        if(recordList != null){
            try {

                recordList.addRecord(record);
                String xmlData = xStream.toXML(recordList);
                Files.write(Paths.get(maintenanceRecordsFile.getAbsolutePath()), xmlData.getBytes(StandardCharsets.UTF_8));
                return true;
            }catch (IOException e){
                LOGGER.log(Level.FINE,"Error writing a record to " + maintenanceFileName + ". Err: " + e.getMessage());
            }
        }
        return false;
    }

    public List<CacheRecord> getMaintenanceRecords(){
        return fetchMaintenanceData().getMaintenanceRecords();
    }

    public List<CacheRecord> getAllMaintenanceRecordsForSingleCache(String cacheName) {
       return fetchMaintenanceData().getAllMaintenanceRecordsForSingleCache(cacheName);
    }
}
