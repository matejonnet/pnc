package org.jboss.pnc.processes.tasks;

import org.jboss.pnc.common.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2015-02-18.
 */
public class BasicProductConfiguration {
    String userCreated;
    String productName;

    public BasicProductConfiguration(String userCreated, String productName) {
        this.userCreated = userCreated;
        this.productName = productName;
    }

    public boolean isValid() {
        return !StringUtils.isEmpty(userCreated)
            && !StringUtils.isEmpty(productName);
    }

    public Map<String, Object> getProcessParams() {
        return new HashMap<String, Object>() {{
            put("userCreated", userCreated);
            put("productName", productName);
        }};
    }
}
