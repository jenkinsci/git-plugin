package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import hudson.Extension;
import hudson.model.PeriodicWork;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

@Extension
public class Cron extends PeriodicWork {

    TaskScheduler taskScheduler;

    @Override
    public long getInitialDelay(){
        return MIN - (Calendar.getInstance().get(Calendar.SECOND) * 1000);
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    protected void doRun() throws Exception {
        scheduleMaintenanceTask();
    }

    private void scheduleMaintenanceTask() throws ANTLRException {
        if(taskScheduler == null){
            taskScheduler = new TaskScheduler();
        }

        taskScheduler.scheduleTasks();
    }
}
