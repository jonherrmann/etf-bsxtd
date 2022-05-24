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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.IntersectionMatrix;
import com.vividsolutions.jts.index.strtree.STRtree;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.basex.query.value.Value;
import org.basex.query.value.item.QNm;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;
import org.basex.query.value.node.FElem;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.Point;

import de.interactive_instruments.etf.bsxm.GeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoX;
import de.interactive_instruments.etf.bsxm.GmlGeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXException;
import de.interactive_instruments.etf.bsxm.JtsTransformer;
import de.interactive_instruments.etf.bsxm.UnsupportedGeometryTypeException;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class CurveComponentAnalysis {

    private CurveComponentAnalysis() {}

    /**
     * Checks if for each curve of the given geomNode a maximum (defined by parameter maxMatchesPerCurve) number of
     * identical curves (same control points - ignoring curve orientation) from the otherGeomsNodes exists.
     *
     * @param geomNode
     *            GML geometry node
     * @param otherGeomsNodes
     *            one or more database nodes representing GML geometries
     * @param maxMatchesPerCurve
     *            the maximum number of matching identical curves that are allowed to be found for each curve from the
     *            geomNode
     * @param context
     *            tbd
     * @return <code>null</code>, if all curves are matched correctly, otherwise the JTS geometry of the first curve from
     *         geomNode which is covered by more than the allowed number of identical curves from otherGeomsNodes
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry curveUnmatchedByIdenticalCurvesMax(final ANode geomNode,
            final Value otherGeomsNodes, final int maxMatchesPerCurve, final GeoXContext context)
            throws GmlGeoXException {
        return curveUnmatchedByIdenticalCurves(geomNode, otherGeomsNodes, maxMatchesPerCurve, true, context);
    }

    /**
     * Checks if for each curve of the given geomNode a minimum (defined by parameter minMatchesPerCurve) number of
     * identical curves (same control points - ignoring curve orientation) from the otherGeomsNodes exists.
     *
     * @param geomNode
     *            GML geometry node
     * @param otherGeomsNodes
     *            one or more database nodes representing GML geometries
     * @param minMatchesPerCurve
     *            the minimum number of matching identical curves that must be found for each curve from the geomNode
     * @param context
     *            tbd
     * @return <code>null</code>, if all curves are matched correctly, otherwise the JTS geometry of the first curve from
     *         geomNode which is not covered by the required number of identical curves from otherGeomsNodes
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry curveUnmatchedByIdenticalCurvesMin(final ANode geomNode,
            final Value otherGeomsNodes, final int minMatchesPerCurve, final GeoXContext context)
            throws GmlGeoXException {
        return curveUnmatchedByIdenticalCurves(geomNode, otherGeomsNodes, minMatchesPerCurve, false, context);
    }

    /**
     * @param geomNode
     *            GML geometry node
     * @param otherGeomsNodes
     *            one or more database nodes representing GML geometries
     * @param numberOfIdenticalCurvesToDetect
     *            the number of matching identical curves to detect
     * @param isMaxNumberToDetect
     *            <code>true</code>, if at most numberOfIdenticalCurvesToDetect matches are allowed, otherwise
     *            <code>false</code> (then at least numberOfIdenticalCurvesToDetect matches must be found)
     * @param context
     * @return
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    private static com.vividsolutions.jts.geom.Geometry curveUnmatchedByIdenticalCurves(final ANode geomNode,
            final Value otherGeomsNodes, final int numberOfIdenticalCurvesToDetect, final boolean isMaxNumberToDetect,
            final GeoXContext context) throws GmlGeoXException {

        if (geomNode == null) {
            throw new GmlGeoXException("Parameter geomNode must contain a database node.");
        }

        if (otherGeomsNodes == null || otherGeomsNodes.isEmpty()) {
            throw new GmlGeoXException("Parameter otherGeomsNodes must contain one or more database nodes.");
        }

        final Collection<Curve> curvesToMatch = context.deegreeTransformer.getCurveComponents(geomNode);
        final List<Curve> otherCurves = context.deegreeTransformer.getCurveComponents(otherGeomsNodes);

        if (numberOfIdenticalCurvesToDetect <= 0) {
            throw new GmlGeoXException("Parameter ..MatchesPerCurve must be greater than 0.");
        }

        try {

            final STRtree otherCurvesIndex = new STRtree();

            for (final Curve c : otherCurves) {
                final com.vividsolutions.jts.geom.Geometry c_jts = context.jtsTransformer.toJTSGeometry(c);
                otherCurvesIndex.insert(c_jts.getEnvelopeInternal(),
                        new ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve>(c_jts, c));
            }

            for (Curve c : curvesToMatch) {

                final com.vividsolutions.jts.geom.Geometry c_jts = context.jtsTransformer.toJTSGeometry(c);

                boolean matchFound = isValidIdenticalCurveCoverage(c, c_jts, otherCurvesIndex,
                        numberOfIdenticalCurvesToDetect, isMaxNumberToDetect, context);

                if (!matchFound) {
                    return context.jtsTransformer.toJTSGeometry(c_jts);
                }
            }

            return null;

        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * @param curve
     * @param c_jts
     *            JTS geometry of the curve
     * @param index
     * @param numberOfIdenticalCurvesToDetect
     * @param isMaxNumberToDetect
     *            <code>true</code>, if at most numberOfIdenticalCurvesToDetect matches are allowed, otherwise
     *            <code>false</code> (then at least numberOfIdenticalCurvesToDetect matches must be found)
     * @return
     * @throws UnsupportedGeometryTypeException
     */
    private static boolean isValidIdenticalCurveCoverage(final Curve curve,
            final com.vividsolutions.jts.geom.Geometry c_jts, final STRtree index,
            final int numberOfIdenticalCurvesToDetect, final boolean isMaxNumberToDetect, final GeoXContext context)
            throws UnsupportedGeometryTypeException {

        final Points curveControlPoints = context.deegreeTransformer.getControlPoints(curve);

        // get other curves from spatial index to compare
        @SuppressWarnings("rawtypes")
        final List results = index.query(c_jts.getEnvelopeInternal());

        int matchCount = 0;

        for (Object o : results) {

            @SuppressWarnings("unchecked")
            final ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve> pair = (ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve>) o;
            final com.vividsolutions.jts.geom.Geometry otherCurve_jts = pair.left;

            if (c_jts.equals(otherCurve_jts)) {

                /*
                 * So the two JTS geometries (of the two curves) are spatially equal. However, we need to ensure that the
                 * control points are identical as well (ignoring orientation).
                 */

                final Curve otherCurve_deegree = pair.right;
                final Points otherCurveControlPoints = context.deegreeTransformer.getControlPoints(otherCurve_deegree);

                if (identicalControlPoints(curveControlPoints, otherCurveControlPoints, true)) {
                    matchCount++;
                }
            }

            // determine if we can skip processing remaining results from index search
            if (isMaxNumberToDetect) {

                // at most numberOfIdenticalCurvesToDetect matches are allowed
                if (matchCount > numberOfIdenticalCurvesToDetect) {
                    return false;
                }

            } else {

                // at least numberOfIdenticalCurvesToDetect matches must be found
                if (matchCount >= numberOfIdenticalCurvesToDetect) {
                    return true;
                }
            }
        }

        if (isMaxNumberToDetect) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Identify points P in a 1D, 2D or 3D geometry that are connected to their neighbouring points N via linear curve
     * segments, and compute the angle of these lines as well as the distance of a point P to the imaginary line between its
     * two neighbouring points.
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geomNode
     *            the node that represents the geometry
     * @param context
     *            tbd
     * @return A DOM element with identified points; the element can be empty (if the geometry does not contain any relevant
     *         point). The element has the following (exemplary) structure:
     *
     *         <pre>
     * {@code
     * <geoxr:Result xmlns:geoxr="https://modules.etf-validator.net/gmlgeox/result">
     *   <geoxr:relevantPoint>
     *     <geoxr:Point>
     *       <!-- The well-known-text representation of the point. -->
     *       <geoxr:wkt>..</geoxr:wkt>
     *       <!-- The angle between the linear curve segments at this point. -->
     *       <geoxr:angleInDegree>..</geoxr:angleInDegree>
     *       <!-- The distance between the point and the imaginary line between its two neighbouring points. -->
     *       <geoxr:distance>..</geoxr:distance>
     *     </geoxr:Point>
     *   </geoxr:relevantPoint>
     *   ..
     * </geoxr:Result>
     * }
     * </pre>
     *
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static FElem determineDetailsOfPointsBetweenLinearCurveSegments(final ANode geomNode,
            final GeoXContext context) throws GmlGeoXException {

        if (geomNode == null) {
            throw new GmlGeoXException("geomNode must not be null");
        }

        ArrayList<ArrayList<Point>> pointLists = context.deegreeTransformer
                .getSetsOfContinuousLinearCurveSegments(geomNode);

        final QNm Result_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "Result", GmlGeoX.GMLGEOX_RESULT_NS);
        final QNm RelevantPoint_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "relevantPoint",
                GmlGeoX.GMLGEOX_RESULT_NS);
        final QNm Point_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "Point", GmlGeoX.GMLGEOX_RESULT_NS);
        final QNm Wkt_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "wkt", GmlGeoX.GMLGEOX_RESULT_NS);
        final QNm AngleInDegree_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "angleInDegree",
                GmlGeoX.GMLGEOX_RESULT_NS);
        final QNm Distance_QNM = new QNm(GmlGeoX.GMLGEOX_RESULT_PREFIX, "distance", GmlGeoX.GMLGEOX_RESULT_NS);

        DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        df.setGroupingUsed(false);
        df.setMaximumFractionDigits(Integer.MAX_VALUE);

        final FElem root = new FElem(Result_QNM);

        // now perform computations of point details
        for (ArrayList<Point> pointList : pointLists) {

            // ignore the first and the last point in the list
            for (int i = 1; i < pointList.size() - 1; i++) {

                final Point pDeg1 = pointList.get(i - 1);
                final Point pDeg2 = pointList.get(i);
                final Point pDeg3 = pointList.get(i + 1);

                com.vividsolutions.jts.geom.Point p1 = context.jtsTransformer.toJTSPoint(pDeg1);
                com.vividsolutions.jts.geom.Point p2 = context.jtsTransformer.toJTSPoint(pDeg2);
                com.vividsolutions.jts.geom.Point p3 = context.jtsTransformer.toJTSPoint(pDeg3);

                final Coordinate coord1 = p1.getCoordinate();
                final Coordinate coord2 = p2.getCoordinate();
                final Coordinate coord3 = p3.getCoordinate();

                final double diff_deg = Angles.directionChangeInDegree(coord1, coord2, coord3);

                com.vividsolutions.jts.geom.LineString lineP1toP3 = context.jtsFactory
                        .createLineString(new Coordinate[]{coord1, coord3});

                double distanceP2ToLine = p2.distance(lineP1toP3);

                final FElem pointElem = new FElem(Point_QNM);

                final FElem distanceElem = new FElem(Distance_QNM);
                String formattedDistance = df.format(distanceP2ToLine);
                distanceElem.add(formattedDistance);

                final FElem angleElem = new FElem(AngleInDegree_QNM);
                String formattedAngle = df.format(diff_deg);
                angleElem.add(formattedAngle);

                final FElem wktElem = new FElem(Wkt_QNM);
                wktElem.add(p2.toText());

                pointElem.add(wktElem);
                pointElem.add(angleElem);
                pointElem.add(distanceElem);

                final FElem relevantPointElem = new FElem(RelevantPoint_QNM);
                relevantPointElem.add(pointElem);

                root.add(relevantPointElem);
            }
        }

        return root;
    }

    /**
     * Checks if for each curve of the given geomNode an identical curve (same control points - ignoring curve orientation)
     * from the otherGeomNodes exists.
     *
     * @param geomNode
     *            GML geometry node
     * @param otherGeomNodes
     *            one or more database nodes representing GML geometries
     * @param context
     *            tbd
     * @return <code>null</code>, if full coverage was determined, otherwise the JTS geometry of the first curve from
     *         geomNode which is not covered by an identical curve from otherGeomNodes
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry determineIncompleteCoverageByIdenticalCurveComponents(
            final ANode geomNode, final Value otherGeomNodes, final GmlGeoXContext context) throws GmlGeoXException {

        try {

            /*
             * Ensure that other geometry nodes only consist of ANodes because we need to compare the original control points.
             */
            @SuppressWarnings("rawtypes")
            final Collection otherGeomNodesObjectList = GmlGeoX.toObjectCollection(otherGeomNodes);
            final Collection<ANode> otherGeomNodes_list = new ArrayList<>(otherGeomNodesObjectList.size());
            for (Object o : otherGeomNodesObjectList) {
                if (!(o instanceof ANode)) {
                    throw new IllegalArgumentException(
                            "Calling this function with an item in the second parameter that is not an ANode is illegal.");
                } else {
                    otherGeomNodes_list.add((ANode) o);
                }
            }

            /* Compute deegree and JTS geometries for the node given as first parameter. */
            final Geometry geom_deegree = context.deegreeTransformer.parseGeometry(geomNode);
            // try to get JTS geometry for the geometry node from cache
            com.vividsolutions.jts.geom.Geometry geom_jts = null;
            if (geomNode instanceof DBNode) {
                geom_jts = context.geometryCache()
                        .getGeometry(context.dbNodeRefFactory.createDBNodeEntry((DBNode) geomNode));
            }
            if (geom_jts == null) {
                geom_jts = context.jtsTransformer.toJTSGeometry(geom_deegree);
            }

            /*
             * Create a map of the curve components from the geometry (key: JTS geometry of a curve, value: deegree geometry of
             * that curve)
             */
            final Collection<Curve> geomCurves = context.deegreeTransformer.getCurveComponents(geom_deegree);
            final Map<com.vividsolutions.jts.geom.Geometry, Curve> geomCurvesMap = new HashMap<>(geomCurves.size());
            for (final Curve c : geomCurves) {
                geomCurvesMap.put(context.jtsTransformer.toJTSGeometry(c), c);
            }

            /*
             * Now parse and index the curves from the other geometry nodes (second parameter).
             */

            final STRtree otherGeomsCurvesIndex = new STRtree();

            for (ANode otherGeomNode : otherGeomNodes_list) {

                /*
                 * NOTE: We do not directly compute the deegree geometry here, because it is only necessary to do so if the JTS
                 * geometries are equal
                 */
                Geometry otherGeom_deegree = null;
                // try to get JTS geometry for the geometry node from cache
                com.vividsolutions.jts.geom.Geometry otherGeom_jts = null;
                if (otherGeomNode instanceof DBNode) {
                    otherGeom_jts = context.geometryCache()
                            .getGeometry(context.dbNodeRefFactory.createDBNodeEntry((DBNode) otherGeomNode));
                }
                if (otherGeom_jts == null) {
                    otherGeom_deegree = context.deegreeTransformer.parseGeometry(otherGeomNode);
                    otherGeom_jts = context.jtsTransformer.toJTSGeometry(otherGeom_deegree);
                }

                /*
                 * Check if the other geometry intersects at all. If not, the other geometry can be ignored.
                 *
                 * TODO: We may not need the overall intersection check ... could be more performant to just use other features
                 * returned from a query of the spatial index, and then just create a spatial index of their curve components.
                 * To do so, we still need to build the JTS geometries of the components (to get their envelopes -
                 * deegree.Geometry.getEnvelope() does that as well). Avoiding the JTS.equals(..) operation to compare curves,
                 * and instead just compare the sequence of points - AND the type of curve segment - might also suffice. ...
                 * That (i.e., also checking curve segment types) may require that we compare the sequence of curve segments,
                 * instead of just the set of all control points.
                 */
                if (geom_jts.intersects(otherGeom_jts)) {

                    if (otherGeom_deegree == null) {
                        otherGeom_deegree = context.deegreeTransformer.parseGeometry(otherGeomNode);
                    }

                    final Collection<Curve> otherGeomCurves = context.deegreeTransformer
                            .getCurveComponents(otherGeom_deegree);
                    for (final Curve oc : otherGeomCurves) {
                        final com.vividsolutions.jts.geom.Geometry oc_jts = context.jtsTransformer.toJTSGeometry(oc);
                        otherGeomsCurvesIndex.insert(oc_jts.getEnvelopeInternal(),
                                new ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve>(oc_jts, oc));
                    }
                }
            }

            /*
             * Now check that each curve of the geometry from the first parameter is matched by an identical curve from the
             * other geometries.
             */
            for (Entry<com.vividsolutions.jts.geom.Geometry, Curve> e : geomCurvesMap.entrySet()) {

                final com.vividsolutions.jts.geom.Geometry geomCurve_jts = e.getKey();
                final Curve geomCurve_deegree = e.getValue();
                final Points geomCurveControlPoints = context.deegreeTransformer.getControlPoints(geomCurve_deegree);

                // get other curves from spatial index to compare
                @SuppressWarnings("rawtypes")
                final List results = otherGeomsCurvesIndex.query(geomCurve_jts.getEnvelopeInternal());

                boolean fullMatchFound = false;
                for (Object o : results) {

                    @SuppressWarnings("unchecked")
                    final ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve> pair = (ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve>) o;
                    final com.vividsolutions.jts.geom.Geometry otherGeomCurve_jts = pair.left;

                    if (geomCurve_jts.equals(otherGeomCurve_jts)) {

                        /*
                         * So the two JTS geometries (of the two curves) are spatially equal. However, we need to ensure that
                         * the control points are identical as well (ignoring orientation).
                         */

                        final Curve otherGeomCurve_deegree = pair.right;
                        final Points otherGeomCurveControlPoints = context.deegreeTransformer
                                .getControlPoints(otherGeomCurve_deegree);

                        if (identicalControlPoints(geomCurveControlPoints, otherGeomCurveControlPoints, true)) {
                            fullMatchFound = true;
                            break;
                        }
                    }
                }

                if (!fullMatchFound) {
                    return geomCurve_jts;
                }
            }

            // No unmatched curve found.
            return null;

        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * Checks two geometries for interior intersection of curve components. If both geometries are point based, the result
     * will be <code>null</code> (since then there are no curves to check). Components of the first geometry are compared
     * with the components of the second geometry (using a spatial index to prevent unnecessary checks): If two components
     * are not equal (a situation that is allowed) then they are checked for an interior intersection, meaning that the
     * interiors of the two components intersect (T********) or - only when curves are compared - that the boundary of one
     * component intersects the interior of the other component (*T******* or ***T*****). If such a situation is detected,
     * the intersection of the two components will be returned and testing will stop (meaning that the result will only
     * provide information for one invalid intersection, not all intersections).
     *
     * @param geomNode1
     *            the node that represents the first geometry
     * @param geomNode2
     *            the node that represents the second geometry
     * @param context
     *            tbd
     * @return The intersection of two components from the two geometries, where an invalid intersection was detected, or
     *         <code>null</code> if no such case exists.
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry determineInteriorIntersectionOfCurveComponents(
            final ANode geomNode1, final ANode geomNode2, final GmlGeoXContext context) throws GmlGeoXException {

        try {

            // Determine if the two geometries intersect at all. Only if they do, a more
            // detailed computation is necessary.

            Geometry g1 = null;
            Geometry g2 = null;

            // try to get JTS geometry for first geometry node from cache
            com.vividsolutions.jts.geom.Geometry jtsg1 = null;
            if (geomNode1 instanceof DBNode) {
                jtsg1 = context.geometryCache()
                        .getGeometry(context.dbNodeRefFactory.createDBNodeEntry((DBNode) geomNode1));
            }
            if (jtsg1 == null) {
                g1 = context.deegreeTransformer.parseGeometry(geomNode1);
                jtsg1 = context.jtsTransformer.toJTSGeometry(g1);
            }

            // now the same for the second geometry node
            com.vividsolutions.jts.geom.Geometry jtsg2 = null;
            if (geomNode2 instanceof DBNode) {
                jtsg2 = context.geometryCache()
                        .getGeometry(context.dbNodeRefFactory.createDBNodeEntry((DBNode) geomNode2));
            }
            if (jtsg2 == null) {
                g2 = context.deegreeTransformer.parseGeometry(geomNode2);
                jtsg2 = context.jtsTransformer.toJTSGeometry(g2);
            }

            // If both geometries are points or multi-points, there cannot be a relevant
            // intersection.
            boolean g1IsPointGeom = jtsg1 instanceof com.vividsolutions.jts.geom.Point
                    || jtsg1 instanceof com.vividsolutions.jts.geom.MultiPoint;
            boolean g2IsPointGeom = jtsg2 instanceof com.vividsolutions.jts.geom.Point
                    || jtsg2 instanceof com.vividsolutions.jts.geom.MultiPoint;
            if (g1IsPointGeom && g2IsPointGeom) {
                return null;
            }

            // Check if the two geometries intersect at all. If not, we are done. Otherwise,
            // we need to check in more detail.
            if (!jtsg1.intersects(jtsg2)) {
                return null;
            }

            // Determine JTS geometries for relevant geometry components
            Collection<com.vividsolutions.jts.geom.Geometry> g1Components;
            if (jtsg1 instanceof com.vividsolutions.jts.geom.Point) {
                g1Components = Collections.singleton(jtsg1);
            } else if (jtsg1 instanceof com.vividsolutions.jts.geom.MultiPoint) {
                com.vividsolutions.jts.geom.MultiPoint mp = (com.vividsolutions.jts.geom.MultiPoint) jtsg1;
                g1Components = Arrays.asList(JtsTransformer.flattenAllGeometryCollections(mp));
            } else {
                if (g1 == null) {
                    g1 = context.deegreeTransformer.parseGeometry(geomNode1);
                }
                final Collection<Curve> curves = context.deegreeTransformer.getCurveComponents(g1);
                g1Components = new ArrayList<>(curves.size());
                for (final Curve c : curves) {
                    g1Components.add(context.jtsTransformer.toJTSGeometry(c));
                }
            }

            Collection<com.vividsolutions.jts.geom.Geometry> g2Components;
            if (jtsg2 instanceof com.vividsolutions.jts.geom.Point) {
                g2Components = Collections.singleton(jtsg2);
            } else if (jtsg2 instanceof com.vividsolutions.jts.geom.MultiPoint) {
                com.vividsolutions.jts.geom.MultiPoint mp = (com.vividsolutions.jts.geom.MultiPoint) jtsg2;
                g2Components = Arrays.asList(JtsTransformer.flattenAllGeometryCollections(mp));
            } else {
                if (g2 == null) {
                    g2 = context.deegreeTransformer.parseGeometry(geomNode2);
                }
                final Collection<Curve> curves = context.deegreeTransformer.getCurveComponents(g2);
                g2Components = new ArrayList<>(curves.size());
                for (final Curve c : curves) {
                    g2Components.add(context.jtsTransformer.toJTSGeometry(c));
                }
            }

            // Switch order of geometry arrays, if the second geometry is point based. We
            // want to create a
            // spatial index only for curve components.
            if (g2IsPointGeom) {
                final Collection<com.vividsolutions.jts.geom.Geometry> tmp = g1Components;
                g1Components = g2Components;
                g2Components = tmp;
            }

            // Create spatial index for curve components.
            final STRtree g2ComponentIndex = new STRtree();
            for (com.vividsolutions.jts.geom.Geometry g2CompGeom : g2Components) {
                g2ComponentIndex.insert(g2CompGeom.getEnvelopeInternal(), g2CompGeom);
            }

            // Now check for invalid interior intersections of components from the two
            // geometries.
            for (com.vividsolutions.jts.geom.Geometry g1CompGeom : g1Components) {

                // get g2 components from spatial index to compare
                @SuppressWarnings("rawtypes")
                final List g2Results = g2ComponentIndex.query(g1CompGeom.getEnvelopeInternal());

                for (Object o : g2Results) {
                    final com.vividsolutions.jts.geom.Geometry g2CompGeom = (com.vividsolutions.jts.geom.Geometry) o;
                    if (g1CompGeom.intersects(g2CompGeom)) {
                        /*
                         * It is allowed that two curves are spatially equal. So only check for an interior intersection in case
                         * that the two geometry components are not spatially equal. The intersection matrix must be computed in
                         * any case (to determine that the two components are not equal). So we compute it once, and re-use it
                         * for tests on interior intersection.
                         */
                        IntersectionMatrix im = g1CompGeom.relate(g2CompGeom);
                        if (!im.isEquals(g1CompGeom.getDimension(), g2CompGeom.getDimension())
                                && (im.matches("T********") || (!(g1IsPointGeom || g2IsPointGeom)
                                        && (im.matches("*T*******") || im.matches("***T*****"))))) {
                            // invalid intersection detected
                            return g1CompGeom.intersection(g2CompGeom);
                        }
                    }
                }
            }

            // No invalid intersection found.
            return null;

        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * Checks all curve segments of geomNode against all curve segments of otherGeomNode. In case of a 1-dimensional
     * intersection of curve segments, the curveSegmentMatchCriterium defines whether all these segments must have a) the
     * same sequence of control points (ignoring orientation), b) a different sequence of control points (again, ignoring
     * orientation), or c) either a or b applies (for all cases of 1-dim intersections).
     *
     * @param geomNode
     *            a database node representing a GML geometry
     * @param otherGeomNode
     *            another database node representing a GML geometry
     * @param curveSegmentMatchCriterium
     *            'ALL_MATCH': all cases of 1-dim intersection must have the same sequence of control points (ignoring
     *            orientation), 'NO_MATCH': no case of 1-dim intersection must have the same sequence of control points
     *            (ignoring orientation), 'ALL_OR_NO_MATCH': either 'ALL_MATCH' or 'NO_MATCH'
     * @param context
     *            TBD
     * @return <code>true</code> if all cases of 1-dimensional intersection of curve segments fulfill the
     *         curveSegmentMatchCriterium - or if there is no 1-dim intersection; else <code>false</code>
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static boolean isValid1DimIntersectionsOfCurves(final ANode geomNode, final ANode otherGeomNode,
            final String curveSegmentMatchCriterium, final GeoXContext context) throws GmlGeoXException {

        // Parameter checks

        if (geomNode == null) {
            throw new GmlGeoXException("Parameter geomNode must contain a database node.");
        }
        if (otherGeomNode == null) {
            throw new GmlGeoXException("Parameter otherGeomNode must contain a database node.");
        }

        boolean allMatch = false;
        boolean noMatch = false;
        boolean allOrNoMatch = false;

        if ("ALL_MATCH".equalsIgnoreCase(curveSegmentMatchCriterium)) {
            allMatch = true;
        } else if ("NO_MATCH".equalsIgnoreCase(curveSegmentMatchCriterium)) {
            noMatch = true;
        } else if ("ALL_OR_NO_MATCH".equalsIgnoreCase(curveSegmentMatchCriterium)) {
            allOrNoMatch = true;
        } else {
            throw new GmlGeoXException("Parameter curveSegmentMatchCriterium must be one of the allowed values. Found: "
                    + StringUtils.defaultIfBlank(curveSegmentMatchCriterium, "<none>"));
        }

        // Parsing the curve components
        final Collection<Curve> curvesGeomNode = context.deegreeTransformer.getCurveComponents(geomNode);
        final Collection<Curve> curvesOtherGeomNode = context.deegreeTransformer.getCurveComponents(otherGeomNode);

        try {

            final STRtree otherCurvesIndex = new STRtree();

            for (final Curve oc : curvesOtherGeomNode) {
                final com.vividsolutions.jts.geom.Geometry oc_jts = context.jtsTransformer.toJTSGeometry(oc);
                otherCurvesIndex.insert(oc_jts.getEnvelopeInternal(),
                        new ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve>(oc_jts, oc));
            }

            int count1DimIntersectionAllMatch = 0;
            int count1DimIntersectionNoMatch = 0;

            for (Curve c : curvesGeomNode) {

                final com.vividsolutions.jts.geom.Geometry c_jts = context.jtsTransformer.toJTSGeometry(c);
                final Points curveControlPoints = context.deegreeTransformer.getControlPoints(c);

                // get other curves from spatial index to compare
                @SuppressWarnings("rawtypes")
                final List results = otherCurvesIndex.query(c_jts.getEnvelopeInternal());

                for (Object o : results) {

                    @SuppressWarnings("unchecked")
                    final ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve> pair = (ImmutablePair<com.vividsolutions.jts.geom.Geometry, Curve>) o;
                    final com.vividsolutions.jts.geom.Geometry otherCurve_jts = pair.left;

                    if (c_jts.relate(otherCurve_jts, "1********")) {

                        /*
                         * The two JTS geometries (of the two curves) have a 1-dimensional intersection. Now check if the
                         * control points are identical (ignoring orientation), and add the result to the overall tally.
                         */

                        final Curve otherCurve_deegree = pair.right;
                        final Points otherCurveControlPoints = context.deegreeTransformer
                                .getControlPoints(otherCurve_deegree);

                        if (identicalControlPoints(curveControlPoints, otherCurveControlPoints, true)) {
                            count1DimIntersectionAllMatch++;
                        } else {
                            count1DimIntersectionNoMatch++;
                        }
                    }

                    // determine if we can already skip processing
                    if (allMatch && count1DimIntersectionNoMatch > 0) {

                        return false;

                    } else if (noMatch && count1DimIntersectionAllMatch > 0) {

                        return false;

                    } else if (allOrNoMatch && count1DimIntersectionNoMatch > 0 && count1DimIntersectionAllMatch > 0) {

                        return false;
                    }
                }
            }

            /*
             * Checks of 1-dimensional intersections - if there were any - have been completed. Since after each such check we
             * already investigated if the match criterium was violated, we can now assume that either the criterium was
             * fulfilled, or that there was no 1-dimensional intersection
             */
            return true;

        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * Checks if the two sequences of control points are equal (with or without ignoring orientation).
     *
     * @param curveControlPoints
     *            The first sequence of control points
     * @param otherCurveControlPoints
     *            The second sequence of control points
     * @param ignoreOrientation
     *            <code>true</code> if orientation shall be ignored, else <code>false</code>
     * @return <code>true</code> if the two sequences of control points are equal, else <code>false</code>
     */
    private static boolean identicalControlPoints(Points curveControlPoints, Points otherCurveControlPoints,
            boolean ignoreOrientation) {

        if (curveControlPoints.size() == otherCurveControlPoints.size()) {

            /*
             * NOTE: deegree.Point equals(..) implementation really just compares the coordinates. So no specific overhead in
             * doing so.
             */

            boolean pointsMatch = true;
            for (int i = 0; i < curveControlPoints.size(); i++) {
                if (!curveControlPoints.get(i).equals(otherCurveControlPoints.get(i))) {
                    pointsMatch = false;
                    break;
                }
            }

            if (ignoreOrientation && !pointsMatch) {

                // try with reversed order of control points from curve of other geometry
                pointsMatch = true;
                final int otherGeomCurveControlPointsSize = otherCurveControlPoints.size();
                for (int i = 0; i < curveControlPoints.size(); i++) {
                    if (!curveControlPoints.get(i)
                            .equals(otherCurveControlPoints.get(otherGeomCurveControlPointsSize - i - 1))) {
                        pointsMatch = false;
                        break;
                    }
                }
            }

            return pointsMatch;

        } else {
            // number of control points is different
            return false;
        }
    }
}
