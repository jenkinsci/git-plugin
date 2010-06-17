package hudson.plugins.git.util;


import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.*;
import org.joda.time.DateTime;
import org.kohsuke.stapler.DataBoundConstructor;
import org.spearce.jgit.lib.ObjectId;

import java.io.IOException;
import java.util.*;

public class DiggBuildChooser extends BuildChooser {

    private final String separator = "#";

    @DataBoundConstructor
    public DiggBuildChooser() {

    }

    /**
     * Determines which Revisions to build.
     *
     * Uses git log --all to get every commit in repository. Then orders commits by commit time
     * and determines what to build next.
     *
     * Doesn't care about branches.
     * @throws IOException
     * @throws GitException
     */
    @Override
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                                                      IGitAPI git, TaskListener listener, BuildData data)
            throws GitException, IOException {

        Build lastTimeBased = data.getLastBuildOfBranch("timebased");

        Revision last = null;
        if(lastTimeBased != null) {
            last = data.getLastBuildOfBranch("timebased").getRevision();
            if(!last.getSha1String().equals(data.getLastBuiltRevision().getSha1String())) {
                //previous build wasn't timebased, so consider this as a new start
                last = null;
            }
        }

        String result = git.getAllLogEntries(singleBranch);
        Collection<TimedCommit> commits = sortRevList(result);
        Iterator<TimedCommit> i = commits.iterator();
        ArrayList<Revision> revs = new ArrayList<Revision>();

        TimedCommit first = null;

        while(i.hasNext()) {
            TimedCommit tc = i.next();

            //When encountered last build, break
            if(last != null && tc.commit.name().equals(last.getSha1String())) {
                break;
            }

            if (first == null) {
                first = tc;
            }
            else {
                // i.e., if first is newer than tc - we want the oldest possible
                // commit.
                if (first.when.compareTo(tc.when) > 0) {
                    first = tc;
                }
            }
        }

        if (first != null) {
            revs.add(getRevFromTimedCommit(first));
        }

        if(last == null) {
            return revs;
        }
        if(revs.size() == 0 && !isPollCall) {
            return Collections.singletonList(last);
        }
        //reverse order
        ArrayList<Revision> finalRevs = new ArrayList<Revision>();
        for(int j = revs.size() - 1 ; j >= 0 ; j--) {
            finalRevs.add(revs.get(j));

        }
        return finalRevs;

    }

    private Revision getRevFromTimedCommit(TimedCommit tc) {
        Revision rev = new Revision(tc.commit);
        rev.getBranches().add(new Branch("timebased", rev.getSha1()));
        return rev;
    }
    
    private void addToRevs(ArrayList<Revision> revs, TimedCommit tc) {
        revs.add(getRevFromTimedCommit(tc));
    }

    /* This returns commits that are always in same order.
     *
     */
    private Collection<TimedCommit> sortRevList(String logOutput) {
        SortedSet<TimedCommit> timedCommits = new TreeSet<TimedCommit>();
        String[] lines = logOutput.split("\n");
        for (String s : lines ) {
            timedCommits.add(parseCommit(s));
        }

        return timedCommits;
    }

    private TimedCommit parseCommit(String line) {

        String[] lines = line.split(separator);
        /*Line has ' in the beginning and in the end */
        String id = lines[0].substring(1);
        String date = lines[1].substring(0, lines[1].length() - 1 );
        //From seconds to milliseconds
        return new TimedCommit(ObjectId.fromString(id),
                new DateTime(Long.parseLong(date) * 1000));
    }

    private class TimedCommit implements Comparable<TimedCommit> {

        private ObjectId commit;
        public DateTime when;

        public TimedCommit(ObjectId c, DateTime when) {
            this.commit = c;
            this.when = when;
        }

        public ObjectId getCommit() {
            return commit;
        }

        @Override
        public int compareTo(TimedCommit o) {
            //I want newest to be first
            int result = -(when.compareTo(o.when));
            //If time is equal, keep order from log.
            if(result == 0) {
                return -1;
            }
            return result;
        }
     }

    @Extension
    public static final class DescriptorImpl extends BuildChooserDescriptor {
        @Override
        public String getDisplayName() {
            return "Digg";
        }

        @Override
        public String getLegacyId() {
            return "Digg";
        }
    }
}
