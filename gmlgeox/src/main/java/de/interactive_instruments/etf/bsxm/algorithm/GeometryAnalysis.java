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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.util.GeometryExtracter;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion;

import org.basex.query.value.node.ANode;
import org.deegree.geometry.Geometry;
import org.jetbrains.annotations.NotNull;

import de.interactive_instruments.etf.bsxm.GeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXException;
import de.interactive_instruments.etf.bsxm.JtsTransformer;
import de.interactive_instruments.etf.bsxm.spatialOperators.SpatialSetOperators;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class GeometryAnalysis {

    private GeometryAnalysis() {}

    /**
     * Computes the segment of the given (Multi)LineString that are in the interior of the given (Multi)Polygon.
     *
     * @param line
     *            a non-empty JTS LineString or MultiLineString
     * @param polygon
     *            a non-empty JTS Polygon or MultiPolygon
     * @param context
     *            tbd
     * @return the segments of the line that are in the interior of the polygon, as a JTS Geometry(Collection)
     * @throws GmlGeoXException
     *             if the parameter values are not correct
     */
    public static com.vividsolutions.jts.geom.Geometry interiorIntersectionsLinePolygon(
            final com.vividsolutions.jts.geom.Geometry line, final com.vividsolutions.jts.geom.Geometry polygon,
            final GmlGeoXContext context) throws GmlGeoXException {

        if (line == null || line.isEmpty()) {
            throw new GmlGeoXException("First parameter must not be null or the empty geometry.");
        } else if (polygon == null || polygon.isEmpty()) {
            throw new GmlGeoXException("Second parameter must not be null or the empty geometry.");
        } else if (!(line instanceof com.vividsolutions.jts.geom.LineString
                || line instanceof com.vividsolutions.jts.geom.MultiLineString)) {
            throw new GmlGeoXException("First parameter must be a JTS LineString or MultiLineString.");
        } else if (!(polygon instanceof com.vividsolutions.jts.geom.Polygon
                || polygon instanceof com.vividsolutions.jts.geom.MultiPolygon)) {
            throw new GmlGeoXException("Second parameter must be a JTS Polygon or MultiPolygon.");
        } else if (!line.relate(polygon, "T********")) {
            // there is no interior intersection
            return null;
        } else {

            com.vividsolutions.jts.geom.Geometry lineIntersectionWithPolygon;

            try {
                lineIntersectionWithPolygon = line.intersection(polygon);
            } catch (Exception e) {
                throw new GmlGeoXException(
                        e.getClass().getName() + " while computing the intersection. Message is: " + e.getMessage());
            }

            // now we compute the difference of the intersection and the polygon boundary
            // JTS difference does not support geometry collections, though
            // so we need to handle cases of geometry collections

            com.vividsolutions.jts.geom.Geometry[] intersections = JtsTransformer
                    .flattenAllGeometryCollections(lineIntersectionWithPolygon);
            com.vividsolutions.jts.geom.Geometry[] polyBoundaryComponents = JtsTransformer
                    .flattenAllGeometryCollections(polygon.getBoundary());

            List<com.vividsolutions.jts.geom.Geometry> interiorIntersections = new ArrayList<>();

            for (com.vividsolutions.jts.geom.Geometry intersection : intersections) {

                // diff the intersection against each component of the boundary
                // if the result is not empty, it represents an interior intersection

                com.vividsolutions.jts.geom.Geometry diffResult = intersection;

                for (com.vividsolutions.jts.geom.Geometry polyBoundaryComponent : polyBoundaryComponents) {

                    try {
                        diffResult = diffResult.difference(polyBoundaryComponent);
                    } catch (Exception e) {
                        throw new GmlGeoXException(e.getClass().getName()
                                + " while computing the difference. Message is: " + e.getMessage());
                    }

                    if (diffResult.isEmpty()) {
                        break;
                    }
                }

                if (!diffResult.isEmpty()) {
                    interiorIntersections.add(diffResult);
                }
            }

            if (interiorIntersections.isEmpty()) {
                return null;
            } else {
                return SpatialSetOperators.unionGeom(interiorIntersections, context);
            }
        }
    }

    /**
     * Checks if the coordinates of the given {@code point} are equal (comparing x, y, and z) to the coordinates of one of
     * the points that define the given {@code geometry}.
     *
     * @param point
     *            The point whose coordinates are checked against the coordinates of the points of {@code geometry}
     * @param geometry
     *            The geometry whose points are checked to see if one of them has coordinates equal to that of {@code point}
     * @return <code>true</code> if the coordinates of the given {@code point} are equal to the coordinates of one of the
     *         points that define {@code geometry}, else <code>false</code>
     */
    public static boolean pointCoordInGeometryCoords(final com.vividsolutions.jts.geom.Point point,
            final com.vividsolutions.jts.geom.Geometry geometry) {

        final Coordinate pointCoord = point.getCoordinate();
        final Coordinate[] geomCoords = geometry.getCoordinates();
        for (Coordinate geomCoord : geomCoords) {
            if (pointCoord.equals3D(geomCoord)) {
                return true;
            }
        }
        return false;
    }

    private static List<com.vividsolutions.jts.geom.Geometry> computeHoles(
            final com.vividsolutions.jts.geom.Geometry geom, final GeoXContext context) {

        final List<com.vividsolutions.jts.geom.Geometry> holes = new ArrayList<>();

        final List<com.vividsolutions.jts.geom.Polygon> extractedPolygons = new ArrayList<>();

        GeometryExtracter.extract(geom, com.vividsolutions.jts.geom.Polygon.class, extractedPolygons);

        if (!extractedPolygons.isEmpty()) {

            // get holes as polygons

            for (com.vividsolutions.jts.geom.Polygon polygon : extractedPolygons) {

                // check that polygon has holes
                if (polygon.getNumInteriorRing() > 0) {

                    // for each hole, convert it to a polygon
                    for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                        com.vividsolutions.jts.geom.LineString ls = polygon.getInteriorRingN(i);
                        com.vividsolutions.jts.geom.Polygon holeAsPolygon = context.jtsTransformer.toJTSPolygon(ls);
                        holes.add(holeAsPolygon);
                    }
                }
            }
        }

        return holes;
    }

    /**
     * Identifies the holes contained in the geometry represented by the given geometry node and returns them as a JTS
     * geometry. If holes were found a union is built, to ensure that the result is a valid JTS Polygon or JTS MultiPolygon.
     * If no holes were found an empty JTS GeometryCollection is returned.
     *
     * @param geometryNode
     *            potentially existing holes will be extracted from the geometry represented by this node (the geometry can
     *            be a Polygon, MultiPolygon, or any other JTS geometry)
     * @param context
     *            tbd
     * @return A geometry (JTS Polygon or MultiPolygon) with the holes contained in the given geometry. Can also be an empty
     *         JTS GeometryCollection but not <code>null</code>;
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry holes(final ANode geometryNode, final GmlGeoXContext context)
            throws GmlGeoXException {

        com.vividsolutions.jts.geom.Geometry geom = context.geometryCache().getOrCacheGeometry(geometryNode, context);
        return holesGeom(geom, context);
    }

    /**
     * Identifies the holes contained in the given geometry and returns them as a JTS geometry. If holes were found a union
     * is built, to ensure that the result is a valid JTS Polygon or JTS MultiPolygon. If no holes were found an empty JTS
     * GeometryCollection is returned.
     *
     * @param geom
     *            potentially existing holes will be extracted from this geometry (can be a Polygon, MultiPolygon, or any
     *            other JTS geometry)
     * @param context
     *            tbd
     * @return A geometry (JTS Polygon or MultiPolygon) with the holes contained in the given geometry. Can also be an empty
     *         JTS GeometryCollection but not <code>null</code>;
     */
    public static com.vividsolutions.jts.geom.Geometry holesGeom(final com.vividsolutions.jts.geom.Geometry geom,
            final GeoXContext context) {

        if (isEmptyGeom(geom)) {

            return context.jtsTransformer.emptyJTSGeometry();

        } else {

            List<com.vividsolutions.jts.geom.Geometry> holes = computeHoles(geom, context);

            if (holes.isEmpty()) {
                return context.jtsTransformer.emptyJTSGeometry();
            } else {
                // create union of holes and return it
                return CascadedPolygonUnion.union(holes);
            }
        }
    }

    /**
     * Check if a JTS geometry is empty.
     *
     * @param geom
     *            the geometry to check
     * @return <code>true</code> if the geometry is <code>null</code> or empty, else <code>false
     *     </code>.
     */
    public static boolean isEmptyGeom(final com.vividsolutions.jts.geom.Geometry geom) {
        return geom == null || geom.isEmpty();
    }

    /**
     * Identifies the holes contained in the given geometry and returns them as polygons within a JTS geometry collection.
     *
     * @param geom
     *            potentially existing holes will be extracted from this geometry (can be a Polygon, MultiPolygon, or any
     *            other JTS geometry)
     * @param context
     *            tbd
     * @return A JTS geometry collection with the holes (as polygons) contained in the given geometry. Can be empty but not
     *         <code>null</code>;
     */
    public static com.vividsolutions.jts.geom.Geometry holesAsGeometryCollection(
            final com.vividsolutions.jts.geom.Geometry geom, final GeoXContext context) {

        if (isEmptyGeom(geom)) {

            return context.jtsTransformer.emptyJTSGeometry();

        } else {

            List<com.vividsolutions.jts.geom.Geometry> holes = computeHoles(geom, context);

            if (holes.isEmpty()) {
                return context.jtsTransformer.emptyJTSGeometry();
            } else {
                return context.jtsTransformer.toJTSGeometryCollection(holes, true);
            }
        }
    }

    /**
     * Returns the boundary, or an empty geometry of appropriate dimension if the given geometry is empty or has no boundary
     * (e.g. a curve whose end points are equal). (In the case of zero-dimensional geometries, an empty GeometryCollection
     * is returned.) For a discussion of this function, see the OpenGIS SimpleFeatures Specification. As stated in SFS
     * Section 2.1.13.1, "the boundary of a Geometry is a set of Geometries of the next lower dimension."
     *
     * @param geometry
     *            the geometry
     * @param context
     *            tbd
     * @return the closure of the combinatorial boundary of this Geometry
     */
    public static com.vividsolutions.jts.geom.Geometry boundaryGeom(final com.vividsolutions.jts.geom.Geometry geometry,
            final GeoXContext context) {
        if (geometry == null) {
            return context.jtsTransformer.emptyJTSGeometry();
        } else {
            return geometry.getBoundary();
        }
    }

    /**
     * Returns the centroid of the given geometry. The centroid is equal to the centroid of the set of component geometries
     * of highest dimension (since the lower-dimension geometries contribute zero "weight" to the centroid). The centroid of
     * an empty geometry is POINT EMPTY.
     *
     * @param geometry
     *            the geometry
     * @return the centroid of the geometry
     */
    public static com.vividsolutions.jts.geom.Point centroidGeom(final com.vividsolutions.jts.geom.Geometry geometry) {
        if (geometry == null) {
            return null;
        } else {
            return geometry.getCentroid();
        }
    }

    /**
     * Returns the area of the given geometry. Only areal geometries have a non-zero area.
     *
     * @param geometry
     *            the geometry
     * @return the area of the geometry
     */
    public static double areaGeom(final com.vividsolutions.jts.geom.Geometry geometry) {
        if (geometry == null) {
            return 0;
        } else {
            return geometry.getArea();
        }
    }

    /**
     * Returns the distance of a point to a boundary of a surface.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param point
     *            a point as a jts geometry.
     * @param surfaceGeometry
     *            a surface as a geometry node.
     * @param context
     *            tbd
     * @throws GmlGeoXException
     *             In case an exception occurred.
     * @return Distance between the point and the surface boundary
     */
    public static double distancePointToSurfaceBoundary(final @NotNull com.vividsolutions.jts.geom.Point point,
            final @NotNull ANode surfaceGeometry, final GmlGeoXContext context) throws GmlGeoXException {
        final com.vividsolutions.jts.geom.Geometry surface = context.geometryCache().getOrCacheGeometry(surfaceGeometry,
                context);
        return point.distance(surface.getBoundary());
    }

    /**
     * Returns the distance between jts points.
     *
     * @param point1
     *            tbd
     * @param point2
     *            tbd
     * @return tbd
     */
    public static double distancePointToPoint(final @NotNull com.vividsolutions.jts.geom.Point point1,
            final @NotNull com.vividsolutions.jts.geom.Point point2) {
        return point1.distance(point2);
    }

    /**
     * Identify all geometries contained in the given geometry, that have the given dimension. Note that Point and
     * MultiPoint have dimension 0, LineString and MultiLineString have dimension 1, and Polygon and MultiPolygon have
     * dimension 2.
     *
     * @param geom
     *            the geometry - typically a collection - to investigate; must not be <code>null</code>
     * @param dimension
     *            the dimension of geometries to return (value must be 0, 1, or 2)
     * @return the geometries with the specified dimension; can be empty
     * @throws GmlGeoXException
     *             if parameter values are incorrect
     */
    public static com.vividsolutions.jts.geom.Geometry[] geometriesWithDimension(
            final com.vividsolutions.jts.geom.Geometry geom, final int dimension) throws GmlGeoXException {

        if (geom == null) {
            throw new GmlGeoXException("Parameter geom must not be null.");
        } else if (dimension < 0 || dimension > 2) {
            throw new GmlGeoXException("Parameter dimension must be 0, 1, or 2.");
        }

        Collection<com.vividsolutions.jts.geom.Geometry> geoms = JtsTransformer.flattenGeometryCollections(geom);

        List<com.vividsolutions.jts.geom.Geometry> result = new ArrayList<>();

        for (com.vividsolutions.jts.geom.Geometry g : geoms) {

            if (g.getDimension() == dimension) {
                result.add(g);
            }
        }

        return result.toArray(new com.vividsolutions.jts.geom.Geometry[result.size()]);
    }

    /**
     * Checks if a given geometry is closed. Points and MultiPoints are closed by definition (they do not have a boundary).
     * Polygons and MultiPolygons are never closed in 2D, and since operations in 3D are not supported, this method will
     * always return <code>false</code> if a polygon is encountered - unless the parameter onlyCheckCurveGeometries is set
     * to <code>true</code>. LinearRings are closed by definition. The remaining geometry types that will be checked are
     * LineString and MultiLineString. If a (Multi)LineString is not closed, this method will return <code>false</code>.
     *
     * @param geom
     *            the geometry to test
     * @param onlyCheckCurveGeometries
     *            <code>true</code> if only curve geometries (i.e., for JTS: LineString, LinearRing, and MultiLineString)
     *            shall be tested, else <code>false</code> (in this case, the occurrence of polygons will result in the
     *            return value <code>false</code>).
     * @return <code>true</code> if the given geometry is closed, else <code>false</code>
     * @throws GmlGeoXException
     *             If an exception occurred while computing the spatial operation.
     */
    public static boolean isClosedGeom(final com.vividsolutions.jts.geom.Geometry geom,
            final boolean onlyCheckCurveGeometries) throws GmlGeoXException {

        final Collection<com.vividsolutions.jts.geom.Geometry> gc = JtsTransformer.flattenGeometryCollections(geom);

        for (com.vividsolutions.jts.geom.Geometry g : gc) {

            if (g instanceof com.vividsolutions.jts.geom.Point || g instanceof com.vividsolutions.jts.geom.MultiPoint) {

                /* points are closed by definition (they do not have a boundary) */

            } else if (g instanceof com.vividsolutions.jts.geom.Polygon
                    || g instanceof com.vividsolutions.jts.geom.MultiPolygon) {

                /*
                 * The JTS FAQ contains the following question and answer:
                 *
                 * Question: Does JTS support 3D operations?
                 *
                 * Answer: JTS does not provide support for true 3D geometry and operations. However, JTS does allow Coordinates
                 * to carry an elevation or Z value. This does not provide true 3D support, but does allow "2.5D" uses which are
                 * required in some geospatial applications.
                 *
                 * -------
                 *
                 * So, JTS does not support true 3D geometry and operations. Therefore, JTS cannot determine if a surface is
                 * closed. deegree does not seem to support this, either. In order for a surface to be closed, it must be a
                 * sphere or torus, possibly with holes. A surface in 2D can never be closed. Since we lack the ability to
                 * compute in 3D we assume that a (Multi)Polygon is not closed. If we do check geometries other than curves,
                 * then we return false.
                 */
                if (!onlyCheckCurveGeometries) {
                    return false;
                }
            } else if (g instanceof com.vividsolutions.jts.geom.MultiLineString) {

                com.vividsolutions.jts.geom.MultiLineString mls = (com.vividsolutions.jts.geom.MultiLineString) g;
                if (!mls.isClosed()) {
                    return false;
                }
            } else if (g instanceof com.vividsolutions.jts.geom.LineString) {
                /* NOTE: LinearRing is a subclass of LineString, and closed by definition */
                final com.vividsolutions.jts.geom.LineString ls = (com.vividsolutions.jts.geom.LineString) g;
                if (!ls.isClosed()) {
                    return false;
                }
            } else {
                // should not happen
                throw new GmlGeoXException("Unexpected geometry type encountered: " + g.getClass().getName());
            }
        }

        // all relevant geometries are closed
        return true;
    }

    /**
     * Merges a collection of linear components to form maximal-length line strings.
     * <p/>
     * Merging stops at nodes of degree 1 or degree 3 or more.In other words, all nodes of degree 2 are merged together.The
     * exception is in the case of an isolated loop, which only has degree-2 nodes. In this case one of the nodes is chosen
     * as a starting point.
     * <p/>
     * The direction of each merged LineString will be that of the majority of the LineStrings from which it was derived.
     * <p/>
     * Any dimension of Geometry is handled - the constituent line work is extracted to form the edges. However, the edges
     * must be correctly noded; that is, they must only meet at their end points. The LineMerger will accept non-noded input
     * but will not merge non-noded edges. Therefore, if LineStrings with potential crossings or overlap shall be merged, it
     * is best to union them first, which has the effect of noding and dissolving the input line work. In this context
     * "noding" means that there will be a node or endpoint in the result for every endpoint or line segment crossing in the
     * input. "Dissolving" means that any duplicate (i.e. coincident) line segments or portions of line segments will be
     * reduced to a single line segment in the result
     * <p/>
     * Input lines which are empty or contain only a single unique coordinate are not included in the merging.
     *
     * @param geom
     *            the geometry (collection) to merge, typically a (collection of) (multi-) line string(s).
     * @param context
     *            tbd
     * @return a collection of merged LineStrings
     */
    public static com.vividsolutions.jts.geom.Geometry[] mergeLinesGeom(com.vividsolutions.jts.geom.Geometry geom,
            GmlGeoXContext context) {

        if (geom == null || geom.isEmpty()) {
            return new com.vividsolutions.jts.geom.Geometry[0];
        } else {

            LineMerger merger = new LineMerger();
            merger.add(geom);
            @SuppressWarnings("unchecked")
            Collection<LineString> mergedLineStrings = merger.getMergedLineStrings();
            return mergedLineStrings.toArray(new com.vividsolutions.jts.geom.Geometry[mergedLineStrings.size()]);
        }
    }
}
