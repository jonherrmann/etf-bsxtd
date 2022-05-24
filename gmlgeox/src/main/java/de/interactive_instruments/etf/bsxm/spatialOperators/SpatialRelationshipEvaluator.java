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
package de.interactive_instruments.etf.bsxm.spatialOperators;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vividsolutions.jts.geom.GeometryCollection;

import org.basex.query.value.node.DBNode;
import org.basex.query.value.seq.Empty;
import org.deegree.geometry.Geometry;
import org.jetbrains.annotations.Contract;

import de.interactive_instruments.etf.bsxm.GmlGeoX;
import de.interactive_instruments.etf.bsxm.GmlGeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXException;
import de.interactive_instruments.etf.bsxm.JtsTransformer;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class SpatialRelationshipEvaluator {

    public static final Pattern INTERSECTIONPATTERN = Pattern.compile("[0-2*TF]{9}");

    /**
     * Compute the DE-9IM intersection matrix that describes how geom1 relates to geom2.
     *
     * @param geom1
     *            the first (JTS) geometry
     * @param geom2
     *            the second (JTS) geometry
     * @return the DE-9IM intersection matrix that describes how geom1 relates to geom2
     */
    public static String intersectionMatrix(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) {
        return geom1.relate(geom2).toString();
    }

    /**
     * Compares two objects that represent geometries. If one of these objects is an instance of BaseX Empty,
     * <code>false</code> will be returned. Cannot compare JTS GeometryCollection objects that are real collections (so not
     * one of the subtypes: MultiPoint, MultiLineString, MultiPolygon) - unless both are empty (then the result is
     * <code>false</code>).
     *
     * @param geom1x
     *            represents the first geometry, encoded as a GML geometry element or JTS geometry object
     * @param geom2x
     *            represents the second geometry, encoded as a GML geometry element or JTS geometry object
     * @param op
     *            The spatial relationship operator to check
     * @param context
     *            tbd
     * @return <code>true</code> if the two geometries have the spatial relationship, else <code>false</code>
     * @throws GmlGeoXException
     *             tbd
     */
    public static boolean applySpatialRelationshipOperation(final Object geom1x, final Object geom2x,
            final SpatialRelOp op, final GmlGeoXContext context) throws GmlGeoXException {

        if (geom1x == null || geom2x == null || geom1x instanceof Empty || geom2x instanceof Empty) {
            return false;
        }

        if ((geom1x instanceof GeometryCollection
                && JtsTransformer.isGeometryCollectionButNotASubtype((GeometryCollection) geom1x))
                || (geom2x instanceof GeometryCollection
                        && JtsTransformer.isGeometryCollectionButNotASubtype((GeometryCollection) geom2x))) {

            if (geom1x instanceof GeometryCollection && !((GeometryCollection) geom1x).isEmpty()) {
                throw new GmlGeoXException(
                        "GmlGeoX.applySpatialRelationshipOperation(..) - First argument is a non-empty geometry collection. This is not supported by this method.");
            } else if (geom2x instanceof GeometryCollection && !((GeometryCollection) geom2x).isEmpty()) {
                throw new GmlGeoXException(
                        "GmlGeoX.applySpatialRelationshipOperation(..) - Second argument is a non-empty geometry collection. This is not supported by this method.");
            }

            return false;
        }

        final com.vividsolutions.jts.geom.Geometry g1 = getCachedGeometryFromNodeOrTransform(geom1x, context);
        final com.vividsolutions.jts.geom.Geometry g2 = getCachedGeometryFromNodeOrTransform(geom2x, context);

        return op.call(g1, g2);
    }

    /**
     * Tests if one geometry has a certain spatial relationship with a list of other geometries. Whether a match is required
     * for all or just one of these is controlled via parameter.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element or JTS geometry object
     * @param arg2
     *            represents a list of geometries, encoded as GML geometry elements or a JTS geometry object (typically a
     *            JTS geometry collection)
     * @param op
     *            The spatial relationship operator to check
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else
     *            <code>false</code>
     * @param context
     *            tbd
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be
     *         returned if arg2 is empty.
     * @throws GmlGeoXException
     *             If an exception occurred while computing the spatial operation.
     */
    public static boolean applySpatialRelationshipOperation(final Object arg1, final Object arg2, final SpatialRelOp op,
            final boolean matchAll, final GmlGeoXContext context) throws GmlGeoXException {

        try {

            if (arg1 == null || arg2 == null || arg1 instanceof Empty || arg2 instanceof Empty
                    || (arg1 instanceof GeometryCollection && ((GeometryCollection) arg1).isEmpty())
                    || (arg2 instanceof GeometryCollection && ((GeometryCollection) arg2).isEmpty())) {

                return false;

            } else {

                final Collection<?> arg1_list = GmlGeoX.toObjectCollection(arg1);
                final Collection<?> arg2_list = GmlGeoX.toObjectCollection(arg2);

                boolean allMatch = true;

                outer: for (Object o1 : arg1_list) {
                    for (Object o2 : arg2_list) {

                        if (matchAll) {
                            if (!applySpatialRelationshipOperation(o1, o2, op, context)) {
                                allMatch = false;
                                break outer;
                            }
                            // check the next geometry pair to see if it also satisfies the spatial
                            // relationship
                        } else {
                            if (applySpatialRelationshipOperation(o1, o2, op, context)) {
                                return true;
                            }
                            // check the next geometry pair to see if it satisfies the spatial relationship
                        }
                    }
                }
                if (matchAll) {
                    return allMatch;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            throw new GmlGeoXException(
                    "Exception occurred while applying spatial relationship operation (with multiple geometries to compare). Message is: "
                            + e.getMessage(),
                    e);
        }
    }

    /**
     * Tests if the first geometry relates to the second geometry as defined by the given intersection pattern.
     *
     * @param nodeOrJtsGeometry1
     *            represents the first geometry, encoded as a JTS geometry object or GML geometry element
     * @param nodeOrJtsGeometry2
     *            represents the second geometry, encoded as a JTS geometry object or GML geometry element
     * @param intersectionPattern
     *            the pattern against which to check the intersection matrix for the two geometries
     *            (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
     * @param context
     *            tbd
     * @return <code>true</code> if the DE-9IM intersection matrix for the two geometries matches the
     *         <code>intersectionPattern</code>, else <code>false</code>.
     * @throws GmlGeoXException
     *             If an exception occurred while computing the spatial operation.
     */
    public static boolean applyRelate(final Object nodeOrJtsGeometry1, final Object nodeOrJtsGeometry2,
            final String intersectionPattern, final GmlGeoXContext context) throws GmlGeoXException {

        checkIntersectionPattern(intersectionPattern);

        final com.vividsolutions.jts.geom.Geometry g1 = getCachedGeometryFromNodeOrTransform(nodeOrJtsGeometry1,
                context);
        final com.vividsolutions.jts.geom.Geometry g2 = getCachedGeometryFromNodeOrTransform(nodeOrJtsGeometry2,
                context);
        return g1.relate(g2, intersectionPattern);
    }

    @Contract("null -> !null")
    public static com.vividsolutions.jts.geom.Geometry getCachedGeometryFromNodeOrTransform(final Object dbNodeOrOther,
            final GmlGeoXContext context) throws GmlGeoXException {

        if (dbNodeOrOther instanceof DBNode) {
            return context.geometryCache().getOrCacheGeometry((DBNode) dbNodeOrOther, context);
        } else {
            return context.jtsTransformer.singleObjectToJTSGeometry(dbNodeOrOther);
        }
    }

    /**
     * Tests if one geometry relates to a list of geometries as defined by the given intersection pattern. Whether a match
     * is required for all or just one of these is controlled via parameter.
     *
     * @param arg1
     *            represents the first geometry, encoded as a JTS geometry object or GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a JTS geometry object (typically a JTS geometry collection) or
     *            GML geometry elements
     * @param intersectionPattern
     *            the pattern against which to check the intersection matrix for the geometries
     *            (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else
     *            <code>false</code>
     * @param context
     *            tbd
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false
     *     </code> will also be returned if arg2 is empty.
     * @throws GmlGeoXException
     *             If an exception occurred while computing the spatial operation.
     */
    public static boolean relateMatch(final Object arg1, final Object arg2, final String intersectionPattern,
            final boolean matchAll, final GmlGeoXContext context) throws GmlGeoXException {

        checkIntersectionPattern(intersectionPattern);

        if (arg1 instanceof Empty || arg2 instanceof Empty) {
            return false;
        }

        try {
            final Collection<?> arg1_list = GmlGeoX.toObjectCollection(arg1);
            final Collection<?> arg2_list = GmlGeoX.toObjectCollection(arg2);

            boolean allMatch = true;
            outer: for (Object o1 : arg1_list) {
                for (Object o2 : arg2_list) {

                    if (matchAll) {
                        if (applyRelate(o1, o2, intersectionPattern, context)) {
                            /*
                             * check the next geometry pair to see if it also satisfies the spatial relationship
                             */
                        } else {
                            allMatch = false;
                            break outer;
                        }

                    } else {

                        if (applyRelate(o1, o2, intersectionPattern, context)) {
                            return true;
                        } else {
                            /*
                             * check the next geometry pair to see if it satisfies the spatial relationship
                             */
                        }
                    }
                }
            }

            if (matchAll) {
                return allMatch;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    public static void checkIntersectionPattern(final String intersectionPattern) throws GmlGeoXException {

        if (intersectionPattern == null) {
            throw new GmlGeoXException("intersectionPattern is null.");
        } else {
            final Matcher m = INTERSECTIONPATTERN.matcher(intersectionPattern.trim());
            if (!m.matches()) {
                throw new GmlGeoXException(
                        "intersectionPattern does not match the regular expression, which is: [0-2\\\\*TF]{9}");
            }
        }
    }
}
