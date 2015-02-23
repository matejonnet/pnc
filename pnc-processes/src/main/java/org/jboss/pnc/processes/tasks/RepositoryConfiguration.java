package org.jboss.pnc.processes.tasks;

import java.io.Serializable;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-20.
 */
public class RepositoryConfiguration implements Serializable {

    String url;

    public RepositoryConfiguration(String url) {
        this.url = url;
    }
}
