/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.restclient;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Defaults {

    private static volatile Defaults instance;

    private int httpPort = 80;

    private static Defaults getInstance() {
        if (instance == null) {
            synchronized(Defaults.class) {
                if (instance == null) {
                    instance = new Defaults();
                }
            }
        }
        return instance;
    }

    public static void init(int httpPort) {
        getInstance().httpPort = httpPort;
    }

    public static int getHttpPort() {
        return getInstance().httpPort;
    }

}
