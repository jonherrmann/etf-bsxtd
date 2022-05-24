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
package de.interactive_instruments.etf.bsxm.algorithm;

import static de.interactive_instruments.etf.bsxm.geometry.GeometricPoint.toPosListString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;

import org.apache.commons.lang3.StringUtils;
import org.basex.query.value.Value;
import org.basex.query.value.item.Item;
import org.basex.query.value.item.QNm;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;
import org.basex.query.value.node.FElem;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.Point;
import org.jetbrains.annotations.NotNull;

import de.interactive_instruments.etf.bsxm.ControlPointSearchBehavior;
import de.interactive_instruments.etf.bsxm.GmlGeoX;
import de.interactive_instruments.etf.bsxm.GmlGeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXException;
import de.interactive_instruments.etf.bsxm.JtsTransformer;
import de.interactive_instruments.etf.bsxm.index.SpatialIndexRegister;
import de.interactive_instruments.etf.bsxm.node.DBNodeRef;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class GeometryPointsAnalysis {

    private GeometryPointsAnalysis() {}

    /**
     * Prepares spatial indexing of the control points of $featuresToSearchIn, for the named spatial index "PointIndex".
     * Parses the control points of $featuresToSearchBy. The behavior for computing control points can be influenced by the
     * parameter controlPointSearchBehavior. For each control point, search the index "PointIndex" for a nearest point. If a
     * point within a distance which is strictly smaller than 'maxDistance' is found, detailed information will be returned
     * as a result DOM element. Points which are strictly smaller than 'minDistance' will not be detected as nearest points.
     * Identical points will also not be detected as nearest points.
     *
     * <p>
     * Requires the default spatial index to be initialized in order to retrieve the minimal bounding region.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param featuresToSearchBy
     *            as a sequence of GML feature nodes.
     * @param featuresToSearchByGeom
     *            $featuresToSearchByGeom as a sequence of the GML geometry nodes of $featuresToSearchBy.
     * @param featuresToSearchIn
     *            as a sequence of the GML geometry nodes of $featuresToSearchIn.
     * @param featuresToSearchInGeom
     *            as a sequence of the GML geometry nodes of $featuresToSearchIn.
     * @param controlPointSearchBehavior
     *            code(s) to influence the set of control points (multiple values separated by commas):
     *            'IGNORE_ARC_MID_POINT' - to ignore the middle point of an arc (in Arc and ArcString)
     * @param minDistance
     *            as the minimum distance to search nearest points around the points of $featuresToSearchBy. Points greater
     *            or equal minDistance will be reported.
     * @param maxDistance
     *            as the maximum distance to search nearest points around the points of $featuresToSearchBy. Points strictly
     *            smaller maxDistance will be reported.
     * @param limitErrors
     *            as the maximum number of features with errors to report.
     * @param tileLength
     *            as the coordinate length of each square tile, by which the input will be processed.
     * @param tileOverlap
     *            as the coordinate length, by which the tiles will overlap. In order to prevent errors at boundaries.
     * @param ignoreIdenticalPoints
     *            as boolean value which defines weather to return points with identical positions or not.
     * @param context
     *            tbd
     * @return A DOM element with detailed information of the computed results; the element can be empty (if the geometry
     *         does not contain any relevant points). The element has the following (exemplary) structure:
     *
     *         {@code
     * <geoxr:geoxResults xmlns:geoxr=
    "https://modules.etf-validator.net/gmlgeox/result">
     * <geoxr:geoxResults xmlns:geoxr=
    "https://modules.etf-validator.net/gmlgeox/result">
     *   <geoxr:result>
     *     <!-- The node of the gml feature which has a nearest point. -->
     *     <geoxr:featureWithNearest>
     *       <GmlFeature>...</GmlFeature>
     *     </geoxr:featureWithNearest>
     *     <!-- Nodes with information of nearest objects -->
     *     <geoxr:nearestObject>
     *       <!-- The node of the gml feature which is nearest to featureWithNearest. -->
     *       <geoxr:nearestFeature>
     *         <GmlFeature>...</GmlFeature>
     *       </geoxr:nearestFeature>
     *       <!-- The point of featureWithNearest which has a nearest point. -->
     *       <geoxr:pointWithNearest>408331.407 5473380.787</geoxr:pointWithNearest>
     *       <!-- The point of nearestFeature which is nearest to currentPointWithNearest. -->
     *       <geoxr:nearestPoint>408331.407 5473380.789</geoxr:nearestPoint>
     *       <!-- The distance between the currentPointWithNearest and the nearestPoint. -->
     *       <geoxr:distance>0.0020000003278255463</geoxr:distance>
     *     </geoxr:nearestObject>
     *     <geoxr:nearestObject> ... </geoxr:nearestObject>
     *     ...
     *   </geoxr:result>
     *   <geoxr:result>
     *   ..
     *   <geoxr:result>
     *   ..
     * </geoxr:geoxResults>
     * }
     *
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static FElem detectNearestPoints(final Value featuresToSearchBy, final Value featuresToSearchByGeom,
            final Value featuresToSearchIn, final Value featuresToSearchInGeom, final String controlPointSearchBehavior,
            final double minDistance, final double maxDistance, final int limitErrors, final double tileLength,
            final double tileOverlap, final boolean ignoreIdenticalPoints, final GmlGeoXContext context)
            throws GmlGeoXException {

        final QNm GeoxResults_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "geoxResults", GmlGeoX.GMLGEOX_RESULT_NS);
        final FElem results_root = new FElem(GeoxResults_QNM);

        if (featuresToSearchBy.size() != featuresToSearchByGeom.size()
                || featuresToSearchIn.size() != featuresToSearchInGeom.size()) {
            throw new IllegalArgumentException(
                    "The number of features and the number of geometries to process must be equal.");
        }
        if (featuresToSearchBy.isEmpty() || featuresToSearchIn.isEmpty()) {
            return results_root;
        }
        final HashMap<DBNodeRef, DBNodeRef> geomByFeature = new HashMap<>();
        final Iterator<Item> featureBy_iterator = featuresToSearchBy.iterator();
        final Iterator<Item> geomBy_iterator = featuresToSearchByGeom.iterator();
        final List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> featuresToSearchByEntries = new ArrayList<>();
        while (featureBy_iterator.hasNext() && geomBy_iterator.hasNext()) {
            final DBNode currentFeature = (DBNode) featureBy_iterator.next();
            final DBNode currentGeom = (DBNode) geomBy_iterator.next();
            featuresToSearchByEntries.add(SpatialIndexRegister.spatialIndexEntry(currentFeature, currentGeom, context));

            final DBNodeRef featureNodeEntry = context.dbNodeRefFactory.createDBNodeEntry(currentFeature);
            final DBNodeRef geomNodeEntry = context.dbNodeRefFactory.createDBNodeEntry(currentGeom);
            geomByFeature.put(featureNodeEntry, geomNodeEntry);
        }
        final Iterator<Item> featureIn_iterator = featuresToSearchIn.iterator();
        final Iterator<Item> geomIn_iterator = featuresToSearchInGeom.iterator();
        final List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> featuresToSearchInEntries = new ArrayList<>();
        while (featureIn_iterator.hasNext() && geomIn_iterator.hasNext()) {
            final DBNode currentFeature = (DBNode) featureIn_iterator.next();
            final DBNode currentGeom = (DBNode) geomIn_iterator.next();
            featuresToSearchInEntries.add(SpatialIndexRegister.spatialIndexEntry(currentFeature, currentGeom, context));

            final DBNodeRef featureNodeEntry = context.dbNodeRefFactory.createDBNodeEntry(currentFeature);
            final DBNodeRef geomNodeEntry = context.dbNodeRefFactory.createDBNodeEntry(currentGeom);
            geomByFeature.put(featureNodeEntry, geomNodeEntry);
        }
        final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> featuresToSearchByIndex = SpatialIndexRegister
                .spatialIndex(featuresToSearchByEntries.toArray());
        final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> featuresToSearchInIndex = SpatialIndexRegister
                .spatialIndex(featuresToSearchInEntries.toArray());

        Rectangle envelope_featuresToSearchIn = featuresToSearchInIndex.mbr().get();
        Collection<Rectangle> tiles = getTiles(envelope_featuresToSearchIn, tileLength, tileOverlap);
        final List<ANode> featuresWithNearest = new ArrayList<>();

        outerloop: for (Rectangle tile : tiles) {
            @NotNull
            List<DBNode> featuresToSearchBy_inTile = context.dbNodeRefLookup
                    .collect(featuresToSearchByIndex.search(tile).map(com.github.davidmoten.rtree.Entry::value));
            @NotNull
            List<DBNode> featuresToSearchIn_inTile = context.dbNodeRefLookup
                    .collect(featuresToSearchInIndex.search(tile).map(com.github.davidmoten.rtree.Entry::value));
            if (featuresToSearchBy_inTile.isEmpty() || featuresToSearchIn_inTile.isEmpty()) {
                continue;
            }

            final List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> entries = new ArrayList<>();
            for (final DBNode feature : featuresToSearchIn_inTile) {
                final DBNodeRef geomRef = geomByFeature.get(context.dbNodeRefFactory.createDBNodeEntry(feature));
                final DBNode geom = context.dbNodeRefLookup.resolve(geomRef);
                Collections.addAll(entries, SpatialIndexRegister.spatialPointIndexEntries(feature, geom,
                        controlPointSearchBehavior, context));
            }
            final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> pointIndex = SpatialIndexRegister
                    .spatialIndex(entries.toArray());

            for (final DBNode feature : featuresToSearchBy_inTile) {
                final DBNodeRef geomRef = geomByFeature.get(context.dbNodeRefFactory.createDBNodeEntry(feature));
                final DBNode geom = context.dbNodeRefLookup.resolve(geomRef);

                if (!featuresWithNearest.contains(feature)) {
                    final FElem result = detectNearestPointsByFeature(pointIndex, feature, geom,
                            controlPointSearchBehavior, minDistance, maxDistance, ignoreIdenticalPoints, context);
                    if (result.hasChildren()) {
                        results_root.add(result);
                        featuresWithNearest.add(feature);
                    }
                    final long resultSize = results_root.childIter().size();
                    if (resultSize >= limitErrors)
                        break outerloop;
                }
            }
        }

        return results_root;
    }

    /**
     * Parses the control points of featuresToSearchBy and returns the result of a nearest point search. The behavior for
     * retrieving control points can be influenced by the parameter controlPointSearchBehavior. For each relevant control
     * point, search the index "PointIndex" for a nearest point. If a point within a distance which is strictly smaller than
     * 'maxDistance' is found, then the corresponding feature which is nearest to the current point will be returned. Points
     * which are strictly smaller than 'minDistance' will not be detected as nearest points. Identical points will not be
     * detected as nearest points.
     *
     *
     * @param pointIndex
     * @param featureToSearchBy
     *            represents the node of the feature to search the index
     * @param geometry
     *            represents the GML geometry of the feature to search the index by
     * @param controlPointSearchBehavior
     *            codes to influence the set of control points (multiple values separated by commas): 'IGNORE_ARC_MID_POINT'
     *            - to ignore the middle point of an arc (in Arc and ArcString)
     * @param minDistance
     *            as the minimum distance to search nearest points around the points of featuresToSearchBy.
     * @param maxDistance
     *            as the maximum distance to search nearest points around the points of featuresToSearchBy.
     * @param ignoreIdenticalPoints
     *            as boolean value which defines weather to return points with identical positions or not.
     * @return A DOM element with detailed information of the computed results; the element can be empty (if the geometry
     *         does not contain any relevant points).
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    private static FElem detectNearestPointsByFeature(
            final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> pointIndex,
            final ANode featureToSearchBy, final ANode geometry, final String controlPointSearchBehavior,
            final double minDistance, final double maxDistance, final boolean ignoreIdenticalPoints,
            final GmlGeoXContext context) throws GmlGeoXException {

        final QNm result_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "result", GmlGeoX.GMLGEOX_RESULT_NS);
        final FElem result = new FElem(result_QNM);

        EnumSet<ControlPointSearchBehavior> searchBehavior = EnumSet.noneOf(ControlPointSearchBehavior.class);
        if (StringUtils.isNotBlank(controlPointSearchBehavior)) {
            for (String sp : StringUtils.split(controlPointSearchBehavior, ", ")) {
                Optional<ControlPointSearchBehavior> osp = ControlPointSearchBehavior.fromString(sp);
                if (osp.isPresent()) {
                    searchBehavior.add(osp.get());
                } else {
                    throw new GmlGeoXException("Search behavior '" + sp
                            + "' is not supported. Only use one or more of the following value(s): "
                            + Arrays.stream(ControlPointSearchBehavior.values()).map(cpsb -> cpsb.getName())
                                    .collect(Collectors.joining(", ")));
                }
            }
        }

        final Points points = context.deegreeTransformer.getControlPoints(geometry, searchBehavior);
        final DBNodeRef currentFeature = context.dbNodeRefFactory.createDBNodeEntry((DBNode) featureToSearchBy);

        outerloop: for (Point currentPointDeegree : points) {
            final double x = currentPointDeegree.get0();
            final double y = currentPointDeegree.get1();
            final com.github.davidmoten.rtree.geometry.Point currentPointRTree = Geometries.point(x, y);

            List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> nearestEntries = pointIndex
                    .nearest(currentPointRTree, maxDistance, Integer.MAX_VALUE).toList().toBlocking().single();
            if (minDistance > 0.0) {
                List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> nearestEntries_lowerBound = pointIndex
                        .nearest(currentPointRTree, minDistance, Integer.MAX_VALUE).toList().toBlocking().single();
                nearestEntries.removeAll(nearestEntries_lowerBound);
            }

            for (com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> entry : nearestEntries) {
                if (entry.geometry().equals(currentPointRTree) && ignoreIdenticalPoints) {
                    // Don't return
                } else {
                    final QNm nearestObject_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "nearestObject",
                            GmlGeoX.GMLGEOX_RESULT_NS);
                    final QNm nearestFeature_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "nearestFeature",
                            GmlGeoX.GMLGEOX_RESULT_NS);
                    final QNm currentPointWithNearest_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "pointWithNearest",
                            GmlGeoX.GMLGEOX_RESULT_NS);
                    final QNm nearestPoint_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "nearestPoint",
                            GmlGeoX.GMLGEOX_RESULT_NS);
                    final QNm distance_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "distance",
                            GmlGeoX.GMLGEOX_RESULT_NS);

                    final FElem nearestObject = new FElem(nearestObject_QNM);
                    final FElem nearestFeature = new FElem(nearestFeature_QNM);
                    final FElem currentPointWithNearest = new FElem(currentPointWithNearest_QNM);
                    final FElem nearestPoint = new FElem(nearestPoint_QNM);
                    final FElem distance = new FElem(distance_QNM);

                    nearestFeature.add(context.dbNodeRefLookup.resolve(entry.value()).toString());
                    currentPointWithNearest.add(toPosListString(currentPointRTree));
                    nearestPoint.add(toPosListString((com.github.davidmoten.rtree.geometry.Point) entry.geometry()));
                    distance.add(String.valueOf(
                            currentPointRTree.distance((com.github.davidmoten.rtree.geometry.Point) entry.geometry())));

                    nearestObject.add(nearestFeature);
                    nearestObject.add(currentPointWithNearest);
                    nearestObject.add(nearestPoint);
                    nearestObject.add(distance);

                    result.add(nearestObject);
                }
            }
        }
        if (result.hasChildren()) {
            final QNm featureWithNearest_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "featureWithNearest",
                    GmlGeoX.GMLGEOX_RESULT_NS);
            final FElem featureWithNearest = new FElem(featureWithNearest_QNM);

            featureWithNearest.add(context.dbNodeRefLookup.resolve(currentFeature).toString());
            result.add(featureWithNearest);
        }
        return result;
    }

    private static Collection<Rectangle> getTiles(final Rectangle allFeaturesEnvelope, final double tileLength,
            final double tileOverlap) {
        final List<Rectangle> tiles = new ArrayList<>();

        for (double y = allFeaturesEnvelope.y1(); y <= allFeaturesEnvelope.y2(); y = y + tileLength) {
            for (double x = allFeaturesEnvelope.x1(); x <= allFeaturesEnvelope.x2(); x = x + tileLength) {
                final double tileX1 = x;
                final double tileY1 = y;
                final double tileX2 = x + tileLength;
                final double tileY2 = y + tileLength;
                final Rectangle tile = Geometries.rectangle(tileX1 - tileOverlap, tileY1 - tileOverlap,
                        tileX2 + tileOverlap, tileY2 + tileOverlap);
                tiles.add(tile);
            }
        }
        return tiles;
    }

    /**
     * Retrieve the the control points of the geometry represented by the given geometry node. Does not interpolate any
     * non-linear geometry components, and thus the result only contains points that are defined in the geometry node.
     * <p>
     * The behavior for searching control points can be set using parameter controlPointSearchBehavior. By default, all
     * control points defined by the geometry node are returned. If controlPointSearchBehavior contains the string (ignoring
     * case) 'IGNORE_ARC_MID_POINT', then the middle point of an arc (in Arc and ArcString) is ignored.
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geomNode
     *            the node that represents the geometry
     * @param controlPointSearchBehavior
     *            code(s) to influence the set of control points (multiple values separated by commas):
     *            'IGNORE_ARC_MID_POINT' - to ignore the middle point of an arc (in Arc and ArcString)
     * @param context
     *            tbd
     * @return a list of unique control points of the geometry node, as JTS Point objects; can be empty if the node does not
     *         represent a geometry but not <code>null</code>
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static List<com.vividsolutions.jts.geom.Point> getControlPoints(final ANode geomNode,
            final String controlPointSearchBehavior, final GmlGeoXContext context) throws GmlGeoXException {

        EnumSet<ControlPointSearchBehavior> searchBehavior = EnumSet.noneOf(ControlPointSearchBehavior.class);

        if (StringUtils.isNotBlank(controlPointSearchBehavior)) {
            for (String sp : StringUtils.split(controlPointSearchBehavior, ", ")) {
                Optional<ControlPointSearchBehavior> osp = ControlPointSearchBehavior.fromString(sp);
                if (osp.isPresent()) {
                    searchBehavior.add(osp.get());
                } else {
                    throw new GmlGeoXException("Search behavior '" + sp
                            + "' is not supported. Only use one or more of the following value(s): "
                            + Arrays.stream(ControlPointSearchBehavior.values()).map(cpsb -> cpsb.getName())
                                    .collect(Collectors.joining(", ")));
                }
            }
        }

        Points points = context.deegreeTransformer.getControlPoints(geomNode, searchBehavior);

        final List<com.vividsolutions.jts.geom.Point> jtsPointsList = new ArrayList<>(points.size());

        for (Point point : points) {
            com.vividsolutions.jts.geom.Point geom = (com.vividsolutions.jts.geom.Point) context.jtsTransformer
                    .toJTSGeometry(point);
            jtsPointsList.add(geom);
        }

        return jtsPointsList;
    }
}
