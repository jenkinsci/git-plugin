package jenkins.plugins.git.maintenance.Logs;

public class Record {
    String repoName;
    int repoSize;
    String maintenanceType;
    int prevExecution;
    String executionStatus;
    int executionTime;

    public Record(String repoName,int repoSize, String maintenanceType){
        this.repoName = repoName;
        this.repoSize = repoSize;
        this.maintenanceType = maintenanceType;
    }

    public String getRepoName() {
        return repoName;
    }

    public int getRepoSize() {
        return repoSize;
    }

    public String getMaintenanceType() {
        return maintenanceType;
    }

    public int getPrevExecution() {
        return prevExecution;
    }

    public void setPrevExecution(int prevExecution) {
        this.prevExecution = prevExecution;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public int getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(int executionTime) {
        this.executionTime = executionTime;
    }
}
