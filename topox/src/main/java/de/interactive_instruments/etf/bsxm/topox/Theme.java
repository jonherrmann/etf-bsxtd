/**
 * Copyright 2017-2020 European Union, interactive instruments GmbH
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * This work was supported by the EU Interoperability Solutions for
 * European Public Administrations Programme (http://ec.europa.eu/isa)
 * through Action 1.17: A Reusable INSPIRE Reference Platform (ARE3NA).
 */
package de.interactive_instruments.etf.bsxm.topox;

import static de.interactive_instruments.etf.bsxm.topox.TopologyErrorType.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.interactive_instruments.etf.bsxm.topox.geojson.writer.GeoJsonWriter;

/**
 * The Theme object bundles all objects that are used to create topological information for one or multiple Features,
 * including error handling, parsing and building topological data structure.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class Theme implements Serializable {

    public final String name;
    public final TopologyErrorCollector topologyErrorCollector;
    public final String errorFile;
    public final GeoJsonWriter geoJsonWriter;
    public final PosListParser parser;
    private boolean topologyMarkingFunctionCalled = false;

    final Topology topology;
    protected final TopologyBuilder topologyBuilder;

    public Theme(final String name, final TopologyErrorCollector topologyErrorCollector, final String errorFile,
            final GeoJsonWriter geoJsonWriter, final TopologyBuilder topologyBuilder) {
        this.name = name;
        this.topologyErrorCollector = topologyErrorCollector;
        this.errorFile = errorFile;
        this.geoJsonWriter = geoJsonWriter;
        this.topologyBuilder = topologyBuilder;
        this.topology = new TopologyStore(topologyBuilder);
        this.parser = new HashingPosListParser(topologyBuilder);
    }

    public void nextInterior() {
        this.topologyBuilder.nextInterior();
    }

    public int detectHoles() {
        int count = 0;
        for (final Topology.Edge emptyInterior : topology.emptyInteriors()) {
            count++;
            topologyErrorCollector.collectError(HOLE_EMPTY_INTERIOR,
                    emptyInterior.source().x(),
                    emptyInterior.source().y(),
                    "IS",
                    String.valueOf(emptyInterior.leftObject()));
        }
        return count;
    }

    private void checkTopologyMarkingFunctionCalled() {
        if (topologyMarkingFunctionCalled) {
            throw new IllegalStateException(
                    "Either the unenclosed-boundary or the free-standing surface detection can only be called once");
        }
        topologyMarkingFunctionCalled = true;
    }

    public int detectFreeStandingSurfaces() {
        checkTopologyMarkingFunctionCalled();
        int count = 0;
        for (final Topology.Edge freeStandingSurface : topology.freeStandingSurfaces()) {
            count++;
            topologyErrorCollector.collectError(FREE_STANDING_SURFACE,
                    freeStandingSurface.source().x(),
                    freeStandingSurface.source().y(),
                    "IS",
                    String.valueOf(freeStandingSurface.leftObject()));
        }
        return count;
    }

    public int detectUnenclosedBoundaries() {
        checkTopologyMarkingFunctionCalled();
        int count = 0;
        for (final Topology.Edge unenclosedEdge : topology.unenclosedBoundaries()) {
            count++;
            topologyErrorCollector.collectError(BOUNDARY_EDGE_UNENCLOSED,
                    unenclosedEdge.source().x(),
                    unenclosedEdge.source().y(),
                    "IS",
                    String.valueOf(unenclosedEdge.leftObject()));
        }
        return count;
    }

    public int detectFreeStandingSurfacesWithAllObjects() {
        checkTopologyMarkingFunctionCalled();
        int count = 0;
        for (final Topology.Edge freeStandingSurface : topology.freeStandingSurfaces()) {
            count++;

            Topology.Edge nextConnectedEdge = freeStandingSurface.targetCcwNext();
            final int maxCount = 1_000_000;
            int c = 0;
            // compressed indexes of the geometric objects
            final List<Long> compressedObjIds = new ArrayList<>();
            // object ids
            final Set<Integer> objIds = new HashSet<>();
            while (!nextConnectedEdge.equals(freeStandingSurface) && c++ < maxCount) {
                final long objId = nextConnectedEdge.leftObject();
                if (objId != 0 && objIds.add(DataCompression.preObject(objId))) {
                    compressedObjIds.add(objId);
                }
                nextConnectedEdge = nextConnectedEdge.targetCcwNext();
            }

            final String[] isIds = new String[objIds.size() * 2];
            int i = 0;
            for (final Long compressedObjId : compressedObjIds) {
                isIds[i++] = "IS";
                isIds[i++] = String.valueOf(compressedObjId);
            }

            topologyErrorCollector.collectError(
                    FREE_STANDING_SURFACE_DETAILED,
                    freeStandingSurface.source().x(),
                    freeStandingSurface.source().y(),
                    isIds);
        }
        return count;
    }

    public TopologyMXBean getMBean() {
        return (TopologyMXBean) topology;
    }

    @Override
    public String toString() {
        return topologyBuilder.toString();
    }
}
