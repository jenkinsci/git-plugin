package jenkins.plugins.git.maintenance.Logs;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.security.AnyTypePermission;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class XmlSerialize{

    XStream xStream;

    // Need to set the exact required path for storing the maintenance file.
    String maintenanceRecordsFile = "maintenanceRecords.xml";

    public XmlSerialize(){
        this.xStream = new XStream(new DomDriver());
        // Need to change the Permission type. Todo need to read documentation and update security.
        this.xStream.addPermission(AnyTypePermission.ANY);
    }

    RecordList fetchMaintenanceData(){
        try {
            RecordList recordList;
            if (!new File(maintenanceRecordsFile).exists()) {
                recordList = new RecordList();
            } else {
                byte[] parsedXmlByteArr = Files.readAllBytes(Paths.get(maintenanceRecordsFile));
                String parsedXmlString = new String(parsedXmlByteArr, StandardCharsets.UTF_8);

                xStream.setClassLoader(RecordList.class.getClassLoader());
                recordList = (RecordList) xStream.fromXML(parsedXmlString);
            }

            return recordList;
        }catch (IOException e){
           // Handle exception...
        }

        return null;
    }

    public boolean addMaintenanceRecord(CacheRecord record){
        RecordList recordList = fetchMaintenanceData();

        if(recordList != null){
            try {

                recordList.addRecord(record);
                String xmlData = xStream.toXML(recordList);
                Files.write(Paths.get(maintenanceRecordsFile), xmlData.getBytes(StandardCharsets.UTF_8));
                return true;
            }catch (IOException e){
                // Handle exception...
            }
        }
        return false;
    }

    public List<CacheRecord> getMaintenanceRecords(){
        return fetchMaintenanceData().getMaintenanceRecords();
    }
}
