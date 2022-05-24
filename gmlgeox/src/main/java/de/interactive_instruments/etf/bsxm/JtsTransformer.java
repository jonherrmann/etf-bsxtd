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
package de.interactive_instruments.etf.bsxm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.union.CascadedPolygonUnion;

import org.apache.commons.lang3.StringUtils;
import org.basex.api.dom.BXElem;
import org.basex.query.value.Value;
import org.basex.query.value.item.Item;
import org.basex.query.value.item.Jav;
import org.basex.query.value.node.ANode;
import org.basex.util.Token;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.composite.CompositeGeometry;
import org.deegree.geometry.composite.CompositeSolid;
import org.deegree.geometry.multi.MultiGeometry;
import org.deegree.geometry.multi.MultiSolid;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.OrientableCurve;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.Ring;
import org.deegree.geometry.primitive.Surface;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.patches.SurfacePatch;
import org.deegree.geometry.primitive.segments.CurveSegment;
import org.deegree.geometry.standard.AbstractDefaultGeometry;
import org.deegree.geometry.standard.curvesegments.DefaultArc;
import org.deegree.geometry.standard.curvesegments.DefaultArcString;
import org.deegree.geometry.standard.curvesegments.DefaultLineStringSegment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import de.interactive_instruments.etf.bsxm.geometry.CRS;
import de.interactive_instruments.etf.bsxm.geometry.IICurve;
import de.interactive_instruments.etf.bsxm.geometry.JtsSridCoordinates;

/**
 * Transforms Geometry objects into JTS Geometry objects
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
final public class JtsTransformer {

    private final DeegreeTransformer deegreeTransformer;
    private final com.vividsolutions.jts.geom.GeometryFactory jtsFactory;
    private final SrsLookup srsLookup;

    /**
     * @param deegreeTransformer
     *            the Transformer for Deegree Geometries
     * @param jtsFactory
     *            used to build JTS geometries
     * @param srsLookup
     *            to perform SRS lookups
     */
    public JtsTransformer(final DeegreeTransformer deegreeTransformer, final GeometryFactory jtsFactory,
            final SrsLookup srsLookup) {
        this.deegreeTransformer = deegreeTransformer;
        this.jtsFactory = jtsFactory;
        this.srsLookup = srsLookup;
    }

    /**
     * Creates a JTS Polygon from the given deegree PolygonPatch.
     *
     * @param patch
     *            the polygon patch
     * @return the resulting JTS Polygon
     */
    public Polygon toJTSPolygon(final PolygonPatch patch) {
        final com.vividsolutions.jts.geom.LinearRing shell = toJtsLinearizedRing(patch.getExteriorRing());

        com.vividsolutions.jts.geom.LinearRing[] holes = null;
        final List<Ring> interiorRings = patch.getInteriorRings();
        if (interiorRings != null) {
            holes = new com.vividsolutions.jts.geom.LinearRing[interiorRings.size()];
            for (int i = 0; i < interiorRings.size(); i++) {
                holes[i] = toJtsLinearizedRing(interiorRings.get(i));
            }
        }

        return jtsFactory.createPolygon(shell, holes);
    }

    public @NotNull com.vividsolutions.jts.geom.Point toJTSPoint(final @NotNull Point point) {

        Coordinate c;

        if (point.getCoordinateDimension() == 2) {
            c = new Coordinate(point.get0(), point.get1());
        } else {
            c = new Coordinate(point.get0(), point.get1(), point.get2());
        }

        return jtsFactory.createPoint(c);
    }

    /**
     * Creates a JTS Polygon from the given JTS LineString.
     *
     * @param exterior
     *            the LineString
     * @return the resulting JTS Polygon
     */
    public Polygon toJTSPolygon(final com.vividsolutions.jts.geom.LineString exterior) {

        com.vividsolutions.jts.geom.LinearRing exteriorRing = jtsFactory.createLinearRing(exterior.getCoordinates());

        return jtsFactory.createPolygon(exteriorRing, null);
    }

    private com.vividsolutions.jts.geom.LinearRing toJtsLinearizedRing(final Ring deegreeRing) {

        List<CurveSegment> curveSegments = deegreeRing.getCurveSegments();
        final Curve linearizedCurve = deegreeTransformer.getGeometryFactory().createCurve(deegreeRing.getId(),
                deegreeRing.getCoordinateSystem(), curveSegments.toArray(new CurveSegment[0]));
        return jtsFactory.createLinearRing(((IICurve) linearizedCurve).buildJTSGeometry().getCoordinateSequence());
    }

    /**
     * Creates a JTS GeometryCollection from the list of JTS geometry objects. If the geometries are homogeneous, ie only
     * points, line strings, or polygons, create the specific geometric aggregate
     *
     * @param gList
     *            list of JTS geometries
     * @param forceGeometryCollection
     *            <code>true</code>, if the result shall be a com.vividsolutions.jts.geom.GeometryCollection no matter what,
     *            else <code>false</code>
     * @return a JTS GeometryCollection (empty if the given list is <code>null</code> or empty)
     */
    public GeometryCollection toJTSGeometryCollection(final @NotNull List<com.vividsolutions.jts.geom.Geometry> gList,
            boolean forceGeometryCollection) {

        /*
         * Before processing the list of geometries, first remove all empty geometry collections from the list
         */
        final List<com.vividsolutions.jts.geom.Geometry> gListClean = new ArrayList<>(gList.size());
        for (com.vividsolutions.jts.geom.Geometry g : gList) {
            if (!g.isEmpty()) {
                gListClean.add(g);
            }
        }

        final GeometryCollection gc;
        if (gListClean.isEmpty()) {
            gc = jtsFactory.createGeometryCollection(null);
        } else if (forceGeometryCollection) {
            gc = jtsFactory.createGeometryCollection(gListClean.toArray(new com.vividsolutions.jts.geom.Geometry[0]));
        } else {
            boolean point = true;
            boolean linestring = true;
            boolean polygon = true;
            for (com.vividsolutions.jts.geom.Geometry g : gListClean) {
                if (!(g instanceof com.vividsolutions.jts.geom.Polygon))
                    polygon = false;
                if (!(g instanceof com.vividsolutions.jts.geom.LineString))
                    linestring = false;
                if (!(g instanceof com.vividsolutions.jts.geom.Point))
                    point = false;
                if (!polygon && !linestring && !point)
                    break;
            }
            if (point) {
                gc = jtsFactory.createMultiPoint(gListClean.toArray(new com.vividsolutions.jts.geom.Point[0]));
            } else if (linestring) {
                gc = jtsFactory
                        .createMultiLineString(gListClean.toArray(new com.vividsolutions.jts.geom.LineString[0]));
            } else if (polygon) {
                gc = jtsFactory.createMultiPolygon(gListClean.toArray(new Polygon[0]));
            } else {
                if (gListClean.size() == 1) {
                    com.vividsolutions.jts.geom.Geometry g = gListClean.get(0);
                    if (g instanceof GeometryCollection) {
                        gc = (GeometryCollection) g;
                    } else {
                        gc = jtsFactory.createGeometryCollection(
                                gListClean.toArray(new com.vividsolutions.jts.geom.Geometry[0]));
                    }
                } else {
                    gc = jtsFactory
                            .createGeometryCollection(gListClean.toArray(new com.vividsolutions.jts.geom.Geometry[0]));
                }
            }
        }
        return gc;
    }

    /**
     * Computes a JTS geometry from the given deegree geometry.
     *
     * Supported geometry types:
     * <ul>
     * <li>GM_Point</li>
     * <li>Curve types:
     * <ul>
     * <li>GM_Curve</li>
     * <li>GM_Ring</li>
     * <li>GM_LinearRing</li>
     * <li>GM_LineString</li>
     * <li>GM_OrientedCurve (orientation is ignored when computing the JTS geometry)</li>
     * </ul>
     * </li>
     * <li>Curve segment types (linearization will often be used):
     * <ul>
     * <li>GM_Arc (will be linearized)</li>
     * <li>GM_Circle (will be linearized)</li>
     * <li>GM_LineString</li>
     * <li>GM_CubicSpline (will be linearized)</li>
     * <li>GM_ArcString (will be linearized)</li>
     * <li>GM_GeodesicString (apparently no linearization - with code from deegree 3.4-pre22-SNAPSHOT)</li>
     * </ul>
     * </li>
     * <li>Surface types:
     * <ul>
     * <li>GM_Surface</li>
     * <li>GM_PolyhedralSurface</li>
     * <li>GM_OrientableSurface (orientation is ignored when computing the JTS geometry)</li>
     * </ul>
     * </li>
     * <li>Surface patch types (also if a surface has more than one patch):
     * <ul>
     * <li>GM_Polygon</li>
     * </ul>
     * </li>
     * <li>Composite types:
     * <ul>
     * <li>GM_Composite (implemented as deegree CompositeGeometry)</li>
     * <li>GM_CompositePoint</li>
     * <li>GM_CompositeCurve</li>
     * <li>GM_CompositeSurface</li>
     * </ul>
     * </li>
     * <li>Multi geometry types:
     * <ul>
     * <li>GM_Aggregate (implemented as deegree MultiGeometry)</li>
     * <li>GM_MultiPoint</li>
     * <li>GM_MultiCurve</li>
     * <li>GM_MultiSurface</li>
     * </ul>
     * </li>
     * </ul>
     * <p>
     * Geometry types that are NOT supported:
     * <ul>
     * <li>GM_Solid</li>
     * <li>Curve segment types:
     * <ul>
     * <li>GM_ArcByBulge</li>
     * <li>ArcByCenterPoint</li>
     * <li>GM_ArcStringByBulge</li>
     * <li>GM_Bezier</li>
     * <li>GM_BSplineCurve</li>
     * <li>CircleByCenterPoint</li>
     * <li>GM_Clothoid</li>
     * <li>GM_Geodesic</li>
     * <li>GM_OffsetCurve</li>
     * <li>GM_Conic</li>
     * </ul>
     * </li>
     * <li>Surface types:
     * <ul>
     * <li>GM_TriangulatedSurface</li>
     * <li>GM_Tin</li>
     * <li>GM_ParametricCurveSurface</li>
     * <li>GM_GriddedSurface</li>
     * <li>GM_BilinearGrid</li>
     * <li>GM_BicubicGrid</li>
     * <li>GM_Cone</li>
     * <li>GM_Cylinder</li>
     * <li>GM_Sphere</li>
     * <li>GM_BSplineSurface</li>
     * </ul>
     * </li>
     * <li>Composite types:
     * <ul>
     * <li>GM_CompositeSolid</li>
     * </ul>
     * </li>
     * <li>Multi geometry types:
     * <ul>
     * <li>GM_MultiSolid</li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param geom
     *            the deegree geometry
     * @return the resulting JTS geometry
     * @throws UnsupportedGeometryTypeException
     *             if transformation to a JTS geometry is not supported for this type of deegree geometry
     */
    public @NotNull com.vividsolutions.jts.geom.Geometry toJTSGeometry(final @NotNull Geometry geom)
            throws UnsupportedGeometryTypeException {

        if (geom instanceof Surface) {

            // covers CompositeSurface, OrientableSurface, Polygon, ...

            /*
             * Deegree does not support spatial operations for surfaces with more than one patch - or rather: it ignores all
             * patches except the first one. So we need to detect and handle this case ourselves.
             *
             * In fact, it is DefaultSurface that does not support multiple patches. So we could check that the geometry is an
             * instance of DefaultSurface. However, it is not planned to create another Geometry-Implementation for deegree.
             * Thus we treat each Surface as having the issue of not supporting JTS geometry creation if it has multiple
             * patches.
             *
             * Because we compute the JTS geometry of a surface directly from its patch(es), we don't need a special treatment
             * for the case of an OrientableSurface. Much like for OrientableCurve, the deegree framework returns null when
             * OrientableSurface.getJTSGeometry() is called (with code from deegree 3.4-pre22-SNAPSHOT).
             */

            final Surface s = (Surface) geom;
            final List<? extends SurfacePatch> patches = s.getPatches();
            if (patches.size() > 1) {
                /* compute union - only supportd if all patches are polygon patches */
                final List<com.vividsolutions.jts.geom.Polygon> polygons = new ArrayList<>(
                        patches.size());
                for (SurfacePatch sp : patches) {
                    if (sp instanceof PolygonPatch) {
                        final com.vividsolutions.jts.geom.Polygon p = toJTSPolygon((PolygonPatch) sp);
                        polygons.add(p);
                    } else {
                        throw new UnsupportedGeometryTypeException(
                                "Surface contains multiple surface patches. At least one patch is not a polygon patch. Cannot create a JTS geometry.");
                    }
                }
                // create union and return it
                return CascadedPolygonUnion.union(polygons);
            } else {
                final SurfacePatch sp = patches.get(0);
                if (sp instanceof PolygonPatch) {
                    return toJTSPolygon((PolygonPatch) sp);
                } else {
                    throw new UnsupportedGeometryTypeException(
                            "Surface contains a single surface patch that is not a polygon patch. Cannot create a JTS geometry.");
                }
            }

        } else if (geom instanceof OrientableCurve) {
            // special treatment is necessary because OrientableCurve.getJTSGeometry()
            // returns null (with code from deegree
            // 3.4-pre22-SNAPSHOT).
            try {
                final OrientableCurve oc = (OrientableCurve) geom;
                final Curve baseCurve = oc.getBaseCurve();

                /* NOTE: JTS geometry is built using IICurve.buildJTSGeometry() */
                return ((AbstractDefaultGeometry) baseCurve).getJTSGeometry();

            } catch (IllegalArgumentException e) {
                throw new UnsupportedGeometryTypeException(
                        "Curve (or one of its segments) is of a type for which computation of the JTS geometry is not supported.");
            }
        } else if (geom instanceof Curve) {
            try {
                return ((AbstractDefaultGeometry) geom).getJTSGeometry();
            } catch (IllegalArgumentException e) {
                throw new UnsupportedGeometryTypeException(
                        "Curve (or one of its segments) is of a type for which computation of the JTS geometry is not supported.");
            }
        } else if (geom instanceof Point) {
            try {
                return ((AbstractDefaultGeometry) geom).getJTSGeometry();
            } catch (UnsupportedOperationException e) {
                throw new UnsupportedGeometryTypeException(
                        "Computation of the JTS geometry for a point is only supported if the number of coordinates is either 2 or 3. A different number of coordinates was encountered.");
            }

        } else if (geom instanceof MultiSolid) {
            throw new UnsupportedGeometryTypeException(
                    "Computation of the JTS geometry for a MultiSolid is not supported.");

        } else if (geom instanceof MultiGeometry) {

            @SuppressWarnings("rawtypes")
            final MultiGeometry mg = (MultiGeometry) geom;
            final List<com.vividsolutions.jts.geom.Geometry> gList = new ArrayList<>(mg.size());
            for (Object o : mg) {
                final Geometry geo = (Geometry) o;
                final com.vividsolutions.jts.geom.Geometry g = toJTSGeometry(geo);
                gList.add(g);
            }
            return toJTSGeometryCollection(gList, false);

        } else if (geom instanceof CompositeSolid) {
            throw new UnsupportedGeometryTypeException(
                    "Computation of the JTS geometry for a CompositeSolid is not supported.");

        } else if (geom instanceof CompositeGeometry) {

            @SuppressWarnings("rawtypes")
            final CompositeGeometry cg = (CompositeGeometry) geom;
            final List<com.vividsolutions.jts.geom.Geometry> gList = new ArrayList<>();
            for (Object o : cg) {
                final Geometry geo = (Geometry) o;
                com.vividsolutions.jts.geom.Geometry g = toJTSGeometry(geo);
                gList.add(g);
            }
            return toJTSGeometryCollection(gList, true);

        } else {
            throw new UnsupportedGeometryTypeException("Computation of JTS geometry for deegree geometry type '"
                    + geom.getClass().getName() + "' is not supported.");
        }
    }

    /**
     * Computes a JTS geometry from the given node (which must represent a GML geometry).
     * <p>
     * See {{@link #toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param node
     *            the geometry element
     * @return the resulting JTS geometry
     * @throws GmlGeoXException
     *             if an exception occurred
     */
    @NotNull
    public com.vividsolutions.jts.geom.Geometry toJTSGeometry(final @NotNull ANode node) throws GmlGeoXException {
        final Geometry geom = deegreeTransformer.parseGeometry(node);
        return toJTSGeometry(geom);
    }

    @NotNull
    public com.vividsolutions.jts.geom.Geometry toJTSGeometry(final @Nullable Object o) throws Exception {
        if (o == null) {
            return emptyJTSGeometry();
        } else if (o instanceof Value) {
            final Value v = (Value) o;
            if (v.size() > 1) {
                final List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<>((int) v.size());
                for (final Item i : v) {
                    geoms.add(toJTSGeometry(i));
                }
                return toJTSGeometryCollection(geoms, true);

            } else {
                return singleObjectToJTSGeometry(o);
            }
        } else if (o instanceof Object[]) {
            final List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<>(
                    ((Object[]) o).length);
            for (final Object os : (Object[]) o) {
                com.vividsolutions.jts.geom.Geometry geom = toJTSGeometry(os);
                geoms.add(geom);
            }
            return toJTSGeometryCollection(geoms, true);
        } else {
            return singleObjectToJTSGeometry(o);
        }
    }

    @NotNull
    public com.vividsolutions.jts.geom.Geometry singleObjectToJTSGeometry(final @Nullable Object o)
            throws GmlGeoXException {
        if (o == null) {
            throw new IllegalArgumentException(
                    "Argument is <null> and thus cannot be converted to a single JTS geometry.");
        } else if (o instanceof ANode) {
            return toJTSGeometry((ANode) o);
        } else if (o instanceof BXElem) {
            final BXElem elem = (BXElem) o;
            final ANode node = elem.getNode();
            return toJTSGeometry(node);
        } else if (o instanceof Value) {
            final Value v = (Value) o;
            if (v.size() > 1) {
                throw new IllegalArgumentException("Single value expected where multiple were provided.");
            } else {
                if (v instanceof Jav) {
                    return (com.vividsolutions.jts.geom.Geometry) ((Jav) v).toJava();
                } else {
                    throw new IllegalArgumentException("Conversion of BaseX Value type "
                            + v.getClass().getCanonicalName() + " to geometry is not supported.");
                }
            }
        } else if (o instanceof com.vividsolutions.jts.geom.Geometry) {
            return (com.vividsolutions.jts.geom.Geometry) o;
        } else {
            throw new IllegalArgumentException("Argument cannot be converted to a single JTS geometry.");
        }
    }

    public com.vividsolutions.jts.geom.Geometry emptyJTSGeometry() {
        return jtsFactory.createGeometryCollection(null);
    }

    /**
     * Adds a geometry to a collection. If the geometry is a GeometryCollection (but not a MultiPoint, -LineString, or
     * -Polygon) its members are added (recursively scanning for GeometryCollections).
     *
     * NOTE: Spatial relationship operators cannot be performed for a JTS GeometryCollection, but for (Multi)Point,
     * (Multi)LineString, and (Multi)Polygon.
     *
     * @param geom
     *            the geometry to flatten
     * @return the list of JTS geometries (without GeometryCollection objects)
     */
    public static Collection<com.vividsolutions.jts.geom.Geometry> flattenGeometryCollections(
            final com.vividsolutions.jts.geom.Geometry geom) {
        if (geom == null) {
            return new ArrayList<>();
        } else if (isGeometryCollectionButNotASubtype(geom)) {
            final List<com.vividsolutions.jts.geom.Geometry> geoms = new ArrayList<>(geom.getNumGeometries());
            for (int i = 0; i < geom.getNumGeometries(); i++) {
                geoms.addAll(flattenGeometryCollections(geom.getGeometryN(i)));
            }
            return geoms;
        } else {
            return Collections.singleton(geom);
        }
    }

    public static boolean isGeometryCollectionButNotASubtype(final com.vividsolutions.jts.geom.Geometry geom) {
        return geom instanceof GeometryCollection && !(geom instanceof com.vividsolutions.jts.geom.MultiPoint
                || geom instanceof com.vividsolutions.jts.geom.MultiLineString
                || geom instanceof com.vividsolutions.jts.geom.MultiPolygon);
    }

    private static String fallbackDim2Note(final ICRS crs) {
        if (crs == null) {
            return ". Please note that the SRS is not configured and dimension 2 has been uses as fallback.";
        } else {
            return "";
        }
    }

    public JtsSridCoordinates parseArcStringControlPoints(final ANode arcStringNode) throws GmlGeoXException {

        final CRS crs = srsLookup.getSrsForGeometryComponentNode(arcStringNode);
        final int dimension;
        if (crs == null) {
            // fallback
            dimension = 2;
        } else {
            dimension = crs.getDimension();
            if (dimension != 2 && dimension != 3) {
                final String srsName = srsLookup.determineSrsName(arcStringNode);
                throw new GmlGeoXException("SRS determined to be " + srsName + " with dimension " + dimension
                        + ". Expected dimension of 2 or 3.");
            }
        }

        String positions = null;
        for (final ANode child : arcStringNode.childIter()) {
            if (Arrays.equals("posList".getBytes(), Token.local(child.name()))) {
                positions = child.toJava().getTextContent();
                break;
            }
        }

        final String[] coordinateStrArr = StringUtils.split(positions);
        if (coordinateStrArr == null) {
            throw new GmlGeoXException("Could not parse posList for arc ControlPoints.");
        } else if (coordinateStrArr.length % dimension != 0) {
            final String srsName = srsLookup.determineSrsName(arcStringNode);
            throw new GmlGeoXException("SRS determined to be " + srsName + " with dimension " + dimension
                    + ". Number of coordinates does not match. Found " + coordinateStrArr.length + " coordinates in: "
                    + positions
                    + fallbackDim2Note(crs));
        }

        final int numberOfPoints = coordinateStrArr.length / dimension;
        if (numberOfPoints < 3 || numberOfPoints % 2 != 1) {
            throw new GmlGeoXException("Found " + numberOfPoints
                    + " points. Expected three or greater odd number of points.." + fallbackDim2Note(crs));
        }

        final double[] coordinates = new double[coordinateStrArr.length];
        for (int i = 0; i < coordinateStrArr.length; i++) {
            coordinates[i] = Double.parseDouble(coordinateStrArr[i]);
        }

        final Coordinate[] points = new Coordinate[numberOfPoints];
        for (int i = 0; i < numberOfPoints; i++) {
            int index = i * dimension;
            final Coordinate coord;
            if (dimension == 2) {
                coord = new Coordinate(coordinates[index], coordinates[index + 1]);
            } else {
                // dimension == 3
                coord = new Coordinate(coordinates[index], coordinates[index + 1], coordinates[index + 2]);
            }
            points[i] = coord;
        }
        return new JtsSridCoordinates(crs, points);
    }

    /**
     * Flattens the given geometry if it is a geometry collection. Members that are not geometry collections are added to
     * the result. Thus, MultiPoint, -LineString, and -Polygon will also be flattened. Contained geometry collections are
     * recursively scanned for relevant members.
     *
     * @param geom
     *            a JTS geometry, can be a JTS GeometryCollection
     * @return a sequence of JTS geometry objects that are not collection types; can be empty but not null
     */
    public static com.vividsolutions.jts.geom.Geometry[] flattenAllGeometryCollections(
            final com.vividsolutions.jts.geom.Geometry geom) {

        if (geom == null) {
            return new com.vividsolutions.jts.geom.Geometry[]{};
        } else {
            final List<com.vividsolutions.jts.geom.Geometry> geoms;
            if (geom instanceof GeometryCollection) {
                final GeometryCollection col = (GeometryCollection) geom;
                geoms = new ArrayList<>(col.getNumGeometries());
                for (int i = 0; i < col.getNumGeometries(); i++) {
                    geoms.addAll(Arrays.asList(flattenAllGeometryCollections(col.getGeometryN(i))));
                }
            } else {
                geoms = Collections.singletonList(geom);
            }
            return geoms.toArray(new com.vividsolutions.jts.geom.Geometry[0]);
        }
    }

    /**
     * Retrieves the JTS representations of all curve components contained in the geometry node.
     *
     * @param geomNode
     *            the geometry element
     * @param context
     *            tbd
     * @return An array with the JTS representations of the curve components of the geometry (node); can be empty if the
     *         given geometry node does not contain any curve component.
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry[] curveComponents(final ANode geomNode,
            final GeoXContext context) throws GmlGeoXException {

        final Collection<Curve> curves = context.deegreeTransformer.getCurveComponents(geomNode);
        final List<com.vividsolutions.jts.geom.Geometry> res = new ArrayList<>(curves.size());
        for (Curve c : curves) {
            res.add(context.jtsTransformer.toJTSGeometry(c));
        }
        return res.toArray(new com.vividsolutions.jts.geom.Geometry[0]);
    }

    /**
     * Retrieves a granular JTS representations of all curve components contained in the geometry node. LineStringSegments
     * of curve components are split into multiple linestrings. So that each returned linestring, parsed from a
     * LineStringSegment, consists of exactly two points. Individual arcs are returned as a single interpolated linestring.
     * Curves defining multiple arcs will be split into multiple interpolated Linestrings. One linestring for each arc.
     *
     * @param geomNode
     *            the geometry element
     * @param context
     *            tbd
     * @return An array with the JTS representations of the curve components of the geometry (node); can be empty if the
     *         given geometry node does not contain any curve component.
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry[] curveComponentsGranular(final ANode geomNode,
            final GeoXContext context) throws GmlGeoXException {

        final Collection<Curve> curves = context.deegreeTransformer.getCurveComponents(geomNode);
        final List<com.vividsolutions.jts.geom.Geometry> res = new ArrayList<>(curves.size());

        for (Curve c : curves) {

            final List<CurveSegment> curveSegments = c.getCurveSegments();
            for (CurveSegment cs : curveSegments) {

                if (cs instanceof DefaultLineStringSegment) {

                    final ArrayList<Point> controlPoints = new ArrayList<>();
                    for (Point p : ((DefaultLineStringSegment) cs).getControlPoints()) {
                        controlPoints.add(p);
                    }

                    for (int i = 0; i < controlPoints.size() - 1; i++) {
                        final Point startPoint = controlPoints.get(i);
                        final Point endPoint = controlPoints.get(i + 1);
                        final Coordinate[] coordinates = {
                                new Coordinate(startPoint.get0(), startPoint.get1()),
                                new Coordinate(endPoint.get0(), endPoint.get1())};
                        final LineString lineString = context.jtsFactory.createLineString(coordinates);
                        res.add(lineString);
                    }

                } else if (cs instanceof DefaultArcString) {

                    final int numArcs = ((DefaultArcString) cs).getNumArcs();
                    if (numArcs == 1) {
                        final IICurve iiCurve = new IICurve(c.getId(), c.getCoordinateSystem(), c.getPrecision(),
                                Collections.singletonList(cs), context.geometryFactory());
                        res.add(iiCurve.getJTSGeometry());

                    } else {

                        final Points controlPoints = ((DefaultArcString) cs).getControlPoints();
                        for (int i = 1; i <= numArcs; i++) {
                            final DefaultArc arc = new DefaultArc(controlPoints.get(2 * i - 2), controlPoints.get(2 * i - 1),
                                    controlPoints.get(2 * i));
                            final IICurve iiCurve = new IICurve(c.getId(), c.getCoordinateSystem(), c.getPrecision(),
                                    Collections.singletonList(arc), context.geometryFactory());
                            res.add(iiCurve.getJTSGeometry());
                        }
                    }

                } else if (cs instanceof DefaultArc) {

                    final IICurve iiCurve = new IICurve(c.getId(), c.getCoordinateSystem(), c.getPrecision(),
                            Collections.singletonList(cs), context.geometryFactory());
                    res.add(iiCurve.getJTSGeometry());
                }
            }
        }
        return res.toArray(new com.vividsolutions.jts.geom.Geometry[0]);
    }

    /**
     * Retrieves the end points of the curve represented by the geometry node.
     * <p>
     * NOTE: This is different to computing the boundary of a curve in case that the curve end points are equal (in that
     * case, the curve does not have a boundary).
     *
     * @param geomNode
     *            the geometry element
     * @param context
     *            tbd
     * @return An array with the two end points of the curve geometry (node); can be empty if the given geometry nodes does
     *         not represent a single curve.
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Point[] curveEndPoints(final ANode geomNode, final GeoXContext context)
            throws GmlGeoXException {

        try {

            final Geometry geom = context.deegreeTransformer.parseGeometry(geomNode);
            final List<com.vividsolutions.jts.geom.Point> res = new ArrayList<>();

            if (geom instanceof Curve) {
                final Curve curve = (Curve) geom;
                final Points curveControlPoints = context.deegreeTransformer.getControlPoints(curve);
                final Point pStart = curveControlPoints.get(0);
                final Point pEnd = curveControlPoints.get(curveControlPoints.size() - 1);
                res.add((com.vividsolutions.jts.geom.Point) context.jtsTransformer.toJTSGeometry(pStart));
                res.add((com.vividsolutions.jts.geom.Point) context.jtsTransformer.toJTSGeometry(pEnd));
            }

            return res.toArray(new com.vividsolutions.jts.geom.Point[res.size()]);

        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * Computes the envelope of a geometry.
     *
     * @param geometry
     *            the geometry
     * @return The bounding box, as an array { x1, y1, x2, y2 }
     */
    public static Object[] envelopeGeom(final com.vividsolutions.jts.geom.Geometry geometry) {
        if (geometry == null) {
            return null;
        } else {
            final Envelope env = geometry.getEnvelopeInternal();
            final Object[] res = {env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()};
            return res;
        }
    }

    /**
     * Parse a geometry.
     *
     * @param v
     *            - either a geometry node or a JTS geometry
     * @return a JTS geometry
     * @throws GmlGeoXException
     *             If an exception occurred while parsing the geometry.
     */
    public com.vividsolutions.jts.geom.Geometry parseGeometry(final Value v) throws GmlGeoXException {

        if (v instanceof ANode) {
            ANode node = (ANode) v;
            return toJTSGeometry(node);
        } else if (v instanceof Jav && ((Jav) v).toJava() instanceof com.vividsolutions.jts.geom.Geometry) {
            return (com.vividsolutions.jts.geom.Geometry) ((Jav) v).toJava();
        } else {
            throw new GmlGeoXException("First argument is neither a single node nor a JTS geometry.");
        }
    }
}
