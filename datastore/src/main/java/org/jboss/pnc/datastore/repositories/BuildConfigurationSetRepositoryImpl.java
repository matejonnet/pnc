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
package org.jboss.pnc.datastore.repositories;

import org.jboss.pnc.datastore.repositories.internal.AbstractRepository;
import org.jboss.pnc.datastore.repositories.internal.BuildConfigurationSetSpringRepository;
import org.jboss.pnc.model.BuildConfigurationSet;
import org.jboss.pnc.spi.datastore.predicates.BuildConfigurationSetPredicates;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationRepository;
import org.jboss.pnc.spi.datastore.repositories.BuildConfigurationSetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

@Stateless
public class BuildConfigurationSetRepositoryImpl extends AbstractRepository<BuildConfigurationSet, Integer> implements
        BuildConfigurationSetRepository {

    private final Logger logger = LoggerFactory.getLogger(BuildConfigurationSetRepositoryImpl.class);

    BuildConfigurationRepository buildConfigurationRepository;

    /**
     * @deprecated Created for CDI.
     */
    @Deprecated
    public BuildConfigurationSetRepositoryImpl() {
        super(null, null);
    }

    @Inject
    public BuildConfigurationSetRepositoryImpl(
            BuildConfigurationSetSpringRepository buildConfigurationSetSpringRepository,
            BuildConfigurationRepository buildConfigurationRepository
    ) {
        super(buildConfigurationSetSpringRepository, buildConfigurationSetSpringRepository);
        this.buildConfigurationRepository = buildConfigurationRepository;
    }

    @Override
    public List<BuildConfigurationSet> withProductVersionId(Integer id) {
        return queryWithPredicates(BuildConfigurationSetPredicates.withProductVersionId(id));
    }

    @Override
//    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public BuildConfigurationSet save(BuildConfigurationSet buildConfigurationSet) {
//        BuildConfigurationSet.Builder builder = BuildConfigurationSet.Builder.newBuilder()
//                .name(buildConfigurationSet.getName())
//                .buildConfigSetRecords(buildConfigurationSet.getBuildConfigSetRecords())
//                .buildConfigurations(buildConfigurationSet.getBuildConfigurations())
//                .productVersion(buildConfigurationSet.getProductVersion());
//        Integer id = buildConfigurationSet.getId();
//        if (id != null) {
//            builder.id(id);
//        }
//
//        return springRepository.save(builder.build());

        logger.trace("buildConfigurationSet: {}" , buildConfigurationSet);

        BuildConfigurationSet saved = springRepository.save(buildConfigurationSet);
//        springRepository.flush(); does not help
        return saved;

//        Set<BuildConfiguration> newBuildConfigurations = new HashSet<>();
//        logger.debug("Saving BuildConfigurationSet: {}.", buildConfigurationSet);
//        BuildConfigurationSet buildConfigurationSetSaved = springRepository.save(buildConfigurationSet);
//
//        for (BuildConfiguration buildConfigurationToAdd : buildConfigurationSet.getBuildConfigurations()) {
//            Integer id = buildConfigurationToAdd.getId();
//            if (id == null) {
//                logger.warn("Missing the id of linked BuildConfiguration.");
//                throw new RuntimeException("Invalid Entity: Missing the id of linked BuildConfiguration."); //TODO Typed exception
//            }
//            BuildConfiguration buildConfigurationInDb = buildConfigurationRepository.queryById(id);
//            if (buildConfigurationInDb == null) {
//                logger.warn("Trying to link to non persisted BC: {}.", buildConfigurationToAdd);
//                throw new RuntimeException("Invalid Entity: Trying to link Non existing BuildConfiguration."); //TODO Typed exception
//            }
//            logger.trace("Linking BC {} to BCSet {}.", buildConfigurationInDb.getId(), buildConfigurationSetSaved.getId());
//            buildConfigurationSetSaved.getBuildConfigurations().add(buildConfigurationInDb);
//            buildConfigurationInDb.getBuildConfigurationSets().add(buildConfigurationSetSaved);
//            buildConfigurationRepository.save(buildConfigurationInDb);
//            newBuildConfigurations.add(buildConfigurationInDb);
//        }
//
//
//        if (buildConfigurationSet.getId() != null) {
//            Set<BuildConfiguration> buildConfigurationsInDb = buildConfigurationSetSaved.getBuildConfigurations();
//            logger.trace("buildConfigurationsInDb: {}.", buildConfigurationsInDb);
//            Set<BuildConfiguration> buildConfigurationsToRemove = Sets.difference(buildConfigurationsInDb, newBuildConfigurations);
//            for (BuildConfiguration buildConfigurationToRemove : buildConfigurationsToRemove) {
//                logger.trace("UnLinking BC from BCSet {}.", buildConfigurationToRemove.getId(), buildConfigurationSetSaved.getId());
//                buildConfigurationToRemove.getBuildConfigurationSets().remove(buildConfigurationSetSaved);
//            }
//        }
//        return springRepository.save(buildConfigurationSetSaved);
    }
}
