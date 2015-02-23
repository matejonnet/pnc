package org.jboss.pnc.processes.test.mock;

import org.jboss.pnc.model.BuildRecord;
import org.jboss.pnc.spi.datastore.Datastore;

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-11-24.
 */
@ApplicationScoped
public class DatastoreMock implements Datastore {

    private Logger log = Logger.getLogger(DatastoreMock.class.getName());

    private List<BuildRecord> buildRecords = Collections.synchronizedList(new ArrayList<BuildRecord>());

    @Override
    public void storeCompletedBuild(BuildRecord buildRecord) {
        log.info("Storing build " + buildRecord.getBuildConfiguration());
        buildRecords.add(buildRecord);
    }

    public List<BuildRecord> getBuildRecords() {
        return buildRecords;
    }
}
