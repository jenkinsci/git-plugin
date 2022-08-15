package jenkins.plugins.git.maintenance.Logs;

import java.util.LinkedList;
import java.util.List;

public class RecordList {
    List<Record> maintenanceRecords;

    public RecordList(){
        maintenanceRecords = new LinkedList<>();
    }

    public List<Record> getMaintenanceRecords(){
        return maintenanceRecords;
    }

    public void addRecord(Record record){
        maintenanceRecords.add(record);
    }

    // Todo need a way to clean the recordsList.

}
