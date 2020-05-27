/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2020 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.common.graph;

import org.jboss.util.graph.Graph;
import org.jboss.util.graph.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class GraphBuilder<T> {

    private final Logger logger = LoggerFactory.getLogger(GraphBuilder.class);

    private List<Long> missingNodes = new ArrayList<>();

    private Function<Long, Optional<T>> nodeSupplier;

    private Function<T, Collection<Long>> dependencySupplier;

    private Function<T, Collection<Long>> dependantSupplier;

    public GraphBuilder(
            Function<Long, Optional<T>> nodeSupplier,
            Function<T, Collection<Long>> dependencySupplier,
            Function<T, Collection<Long>> dependantSupplier) {
        this.nodeSupplier = nodeSupplier;
        this.dependencySupplier = dependencySupplier;
        this.dependantSupplier = dependantSupplier;
    }

    private Optional<T> getNode(Long id) {
        return nodeSupplier.apply(id);
    }

    private Collection<Long> getDependencyIds(T node) {
        return dependencySupplier.apply(node);
    }

    private Collection<Long> getDependantIds(T node) {
        return dependantSupplier.apply(node);
    }

    public Vertex<T> buildDependencyGraph(Graph<T> graph, Long nodeId) {
        Optional<T> maybeNode = getNode(nodeId);
        if (maybeNode.isPresent()) {
            T node = maybeNode.get();
            Vertex<T> vertex = getVisited(nodeId, graph);
            if (vertex == null) {
                vertex = new NameUniqueVertex<>(Long.toString(nodeId), node);
                graph.addVertex(vertex);
            }
            for (Long dependencyId : getDependencyIds(node)) {
                Vertex<T> dependency = getVisited(dependencyId, graph);
                if (dependency == null) {
                    dependency = buildDependencyGraph(graph, dependencyId);
                }
                if (dependency != null) {
                    logger.trace("Creating new dependency edge from {} to {}.", vertex, dependency);
                    graph.addEdge(vertex, dependency, 1);
                }
            }
            return vertex;
        } else {
            logger.debug("Cannot find node with id {}.", nodeId);
            missingNodes.add(nodeId);
            return null;
        }
    }

    public Vertex<T> buildDependentGraph(Graph<T> graph, Long nodeId) {
        Optional<T> maybeNode = getNode(nodeId);
        if (maybeNode.isPresent()) {
            T node = maybeNode.get();
            Vertex<T> vertex = getVisited(nodeId, graph);
            if (vertex == null) {
                vertex = new NameUniqueVertex<>(Long.toString(nodeId), node);
                graph.addVertex(vertex);
            }
            for (Long dependantId : getDependantIds(node)) {
                Vertex<T> dependant = getVisited(dependantId, graph);
                if (dependant == null) {
                    dependant = buildDependentGraph(graph, dependantId);
                }
                if (dependant != null) {
                    logger.trace("Creating new dependant edge from {} to {}.", dependant, vertex);
                    graph.addEdge(dependant, vertex, 1);
                }
            }
            return vertex;
        } else {
            missingNodes.add(nodeId);
            return null;
        }
    }

    private Vertex<T> getVisited(Long nodeId, Graph<T> graph) {
        return graph.findVertexByName(Long.toString(nodeId));
    }

    public List<Long> getMissingNodes() {
        return missingNodes;
    }
}
