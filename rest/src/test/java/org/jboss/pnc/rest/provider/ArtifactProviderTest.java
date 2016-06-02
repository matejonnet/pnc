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

package org.jboss.pnc.rest.provider;

import org.jboss.pnc.datastore.limits.DefaultSortInfoProducer;
import org.jboss.pnc.datastore.predicates.SpringDataRSQLPredicateProducer;
import org.jboss.pnc.model.Artifact;
import org.jboss.pnc.model.BuildRecord;
import org.jboss.pnc.rest.provider.collection.CollectionInfo;
import org.jboss.pnc.rest.restmodel.ArtifactRest;
import org.jboss.pnc.spi.datastore.repositories.BuildRecordRepository;
import org.jboss.pnc.spi.datastore.repositories.SortInfoProducer;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Author: Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 * Date: 5/10/16
 * Time: 10:58 AM
 */
public class ArtifactProviderTest {

    private final SpringDataRSQLPredicateProducer predicateProvider = new SpringDataRSQLPredicateProducer();
    private final SortInfoProducer sortInfoProducer = new DefaultSortInfoProducer();

    private final Artifact a1 = createArtifact(100, "booya", "asdf");
    private final Artifact a2 = createArtifact(1, "woohoo", "fdsa");
    private final Artifact a3 = createArtifact(2, "aaa", "gggg");

    private final ArtifactRest a1Rest = new ArtifactRest(a1);
    private final ArtifactRest a2Rest = new ArtifactRest(a2);
    private final ArtifactRest a3Rest = new ArtifactRest(a3);

    private Artifact createArtifact(int id, String filename, String checkSum) {
        Artifact artifact = new Artifact();
        artifact.setId(id);
        artifact.setFilename(filename);
        artifact.setChecksum(checkSum);
        return artifact;
    }

    @Test
    public void shouldSortBuiltArtifactsByFilename() {
        // given
        ArtifactProvider provider = artifactProviderWithBuiltResult();
        //when
        CollectionInfo<ArtifactRest> artifacts = provider.getBuiltArtifactsForBuildRecord(0, 100, "=asc=filename", null, 12);
        // then
        assertThat(artifacts.getContent()).usingFieldByFieldElementComparator().containsExactly(a3Rest, a1Rest, a2Rest);
    }

    @Test
    public void shouldSortBuiltArtifactsById() {
        // given
        ArtifactProvider provider = artifactProviderWithBuiltResult();
        //when
        CollectionInfo<ArtifactRest> artifacts = provider.getBuiltArtifactsForBuildRecord(0, 100, "=asc=id", null, 12);
        // then
        assertThat(artifacts.getContent()).usingFieldByFieldElementComparator().containsExactly(a2Rest, a3Rest, a1Rest);
    }

    @Test
    public void shouldFilterBuiltArtifactsByFilename() {
        // given
        ArtifactProvider provider = artifactProviderWithBuiltResult();
        //when
        CollectionInfo<ArtifactRest> artifacts = provider.getBuiltArtifactsForBuildRecord(0, 100, null, "filename==woohoo", 12);
        // then
        assertThat(artifacts.getContent()).usingFieldByFieldElementComparator().containsExactly(a2Rest);
    }

    @Test
    public void shouldFilterBuiltArtifactsByFilenameIdOrChecksum() {
        // given
        ArtifactProvider provider = artifactProviderWithBuiltResult();
        //when
        CollectionInfo<ArtifactRest> artifacts = provider.getBuiltArtifactsForBuildRecord(0, 100, null, "id==2 or checksum == asdf or filename==woohoo", 12);
        // then
        assertThat(artifacts.getContent()).usingFieldByFieldElementComparator().contains(a1Rest, a2Rest, a3Rest);
    }

    @Test
    public void shouldFilterBuiltArtifactsByFilenameIdAndChecksum() {
        // given
        ArtifactProvider provider = artifactProviderWithBuiltResult();


        String matchingFilter = "id==100 and checksum == asdf and filename==booya";
        CollectionInfo<ArtifactRest> artifacts = provider.getBuiltArtifactsForBuildRecord(0, 100, null, matchingFilter, 12);
        assertThat(artifacts.getContent()).usingFieldByFieldElementComparator().containsExactly(a1Rest);


        String nonMatchingFilter = "id==100 and checksum == asdf and filename==woohoo";
        artifacts = provider.getBuiltArtifactsForBuildRecord(0, 100, null, nonMatchingFilter, 12);
        assertThat(artifacts.getContent()).isEmpty();
    }

    @Test
    public void shouldReturnAllWithoutFilterAndSort() {
        // given
        ArtifactProvider provider = artifactProviderWithBuiltResult();
        //when
        CollectionInfo<ArtifactRest> artifacts = provider.getBuiltArtifactsForBuildRecord(0, 100, null, null, 12);
        // then
        assertThat(artifacts.getContent()).usingFieldByFieldElementComparator().contains(a1Rest, a2Rest, a3Rest);
    }

    @Test
    public void shouldPaginateProperly() {
        // given
        ArtifactProvider provider = artifactProviderWithBuiltResult();

        //when
        CollectionInfo<ArtifactRest> artifacts = provider.getBuiltArtifactsForBuildRecord(0, 1, null, "id == 2 or checksum == asdf", 12);
        // then
        assertThat(artifacts.getContent()).usingFieldByFieldElementComparator().containsExactly(a1Rest);
        assertThat(artifacts.getTotalPages()).isEqualTo(2);

        //when
        artifacts = provider.getBuiltArtifactsForBuildRecord(1, 1, null, "id == 2 or checksum == asdf", 12);
        // then
        assertThat(artifacts.getContent()).usingFieldByFieldElementComparator().containsExactly(a3Rest);
        assertThat(artifacts.getTotalPages()).isEqualTo(2);
    }

    private ArtifactProvider artifactProviderWithBuiltResult() {
        BuildRecord record = new BuildRecord();
        record.setBuiltArtifacts(new HashSet<>(Arrays.asList(a1, a2, a3)));

        BuildRecordRepository recordRepo = mock(BuildRecordRepository.class);
        when(recordRepo.queryById(any())).thenReturn(record);

        return new ArtifactProvider(null, predicateProvider, sortInfoProducer, null, recordRepo);
    }
}