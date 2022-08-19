package jenkins.plugins.git.maintenance.Logs;

import java.util.LinkedList;
import java.util.List;

public class RecordList {
    LinkedList<Record> maintenanceRecords;

    public RecordList(){
        maintenanceRecords = new LinkedList<>();
    }

    public List<Record> getMaintenanceRecords(){
        return new LinkedList<>(maintenanceRecords);
    }

    public void addRecord(Record record){
        maintenanceRecords.addFirst(record);
    }

    // Todo need a way to clean the recordsList.

}
