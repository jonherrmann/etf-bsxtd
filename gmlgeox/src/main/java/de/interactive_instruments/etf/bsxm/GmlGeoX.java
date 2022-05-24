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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.jar.Manifest;

import com.github.davidmoten.rtree.RTree;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

import org.basex.query.QueryResource;
import org.basex.query.value.Value;
import org.basex.query.value.item.Item;
import org.basex.query.value.item.Jav;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;
import org.basex.query.value.node.FElem;
import org.jetbrains.annotations.NotNull;

import rx.plugins.RxJavaHooks;
import rx.plugins.RxJavaPlugins;

import de.interactive_instruments.etf.bsxm.algorithm.ArcAnalysis;
import de.interactive_instruments.etf.bsxm.algorithm.CircleAnalysis;
import de.interactive_instruments.etf.bsxm.algorithm.CurveAnalysis;
import de.interactive_instruments.etf.bsxm.algorithm.CurveComponentAnalysis;
import de.interactive_instruments.etf.bsxm.algorithm.GeometryAnalysis;
import de.interactive_instruments.etf.bsxm.algorithm.GeometryPointsAnalysis;
import de.interactive_instruments.etf.bsxm.geometry.JtsSridCoordinate;
import de.interactive_instruments.etf.bsxm.index.SpatialIndexRegister;
import de.interactive_instruments.etf.bsxm.node.DBNodeRef;
import de.interactive_instruments.etf.bsxm.node.DBNodeRefFactory;
import de.interactive_instruments.etf.bsxm.node.DBNodeRefLookup;
import de.interactive_instruments.etf.bsxm.parser.BxNamespaceHolder;
import de.interactive_instruments.etf.bsxm.spatialOperators.SpatialRelOp;
import de.interactive_instruments.etf.bsxm.spatialOperators.SpatialRelationshipEvaluator;
import de.interactive_instruments.etf.bsxm.spatialOperators.SpatialSetOperators;

/**
 * This module supports parsing geometries as well as computing the spatial relationship between geometries.
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
final public class GmlGeoX extends GeoX implements Externalizable, QueryResource {

    public static final byte[] GMLGEOX_RESULT_NS = "https://modules.etf-validator.net/gmlgeox/result".getBytes();
    public static final byte[] GMLGEOX_RESULT_PREFIX = "geoxr".getBytes();

    private GmlGeoXContext context;

    public GmlGeoX() {
        super();
    }

    /**
     * Initialises the query module. Call this method before actually executing any method that does not belong to the
     * categories 'module management' or 'developer'.
     *
     * @param databaseName
     *            Name of the database that will be queried.
     * @param srsName
     *            Name of the SRS to assign to a geometry if it does not have an srsName attribute itself. Can be
     *            <code>null</code>. Setting a standard SRS can improve performance, but should only be done if all geometry
     *            elements without srsName attribute have the same SRS.
     * @param maxNumPoints
     *            The maximum number of points to be created when interpolating an arc. Must be a number greater than 0, but
     *            can be <code>null</code> (default setting is 1000). The lower the maximum error (set via parameter
     *            maxError, the higher the number of points needs to be. Arc interpolation will never create more than the
     *            configured maximum number of points. However, the interpolation will also never create more points than
     *            needed to achieve the maximum error. In order to achieve interpolations with a very low maximum error, the
     *            maximum number of points needs to be increased accordingly.
     * @param maxError
     *            The maximum error (e.g. 0.00000001), i.e. the maximum difference between an arc and the interpolated line
     *            string - that shall be achieved when creating new arc interpolations. Must be a number greater than 0, but
     *            can be <code>null</code> (default setting is 0.00001). The lower the error (maximum difference), the more
     *            interpolation points will be needed. However, note that a maximum for the number of such points exists. It
     *            can be set via parameter maxNumPoints.
     * @throws GmlGeoXException
     *             In case of an invalid parameter.
     */
    @ContextDependent
    @Requires(Permission.NONE)
    public void init(final String databaseName, final String srsName, final Integer maxNumPoints, final Double maxError)
            throws GmlGeoXException {

        final BxNamespaceHolder bxNamespaceHolder = BxNamespaceHolder.init(queryContext);

        final DBNodeRefFactory dbNodeRefFactory = DBNodeRefFactory.create(databaseName);
        final DBNodeRefLookup dbNodeRefLookup = new DBNodeRefLookup(this.queryContext, dbNodeRefFactory);

        this.context = new GmlGeoXContext(bxNamespaceHolder, dbNodeRefFactory, dbNodeRefLookup);

        super.init(this.context, srsName, maxNumPoints, maxError);
    }

    /**
     * Retrieve the Well-Known-Text representation of a given JTS geometry.
     *
     * @param geom
     *            a JTS geometry
     * @return the WKT representation of the given geometry, or '&lt;null&gt;' if the geometry is <code>null</code>.
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String toWKT(final com.vividsolutions.jts.geom.Geometry geom) {
        if (geom == null) {
            return "<null>";
        } else {
            return geom.toText();
        }
    }

    /**
     * Parse the Well-Known-Text to a JTS geometry.
     *
     * @param geomWkt
     *            geometry as well-known-text
     * @return the JTS geometry parsed from the wkt, or <code>null</code> if the geometry wkt is <code>null</code>.
     * @throws GmlGeoXException
     *             if an exception occurred while parsing the WKT string
     */
    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry fromWKT(final @NotNull String geomWkt) throws GmlGeoXException {

        WKTReader reader = new WKTReader(this.context.jtsFactory);
        try {
            return reader.read(geomWkt);
        } catch (ParseException e) {
            throw new GmlGeoXException("Could not parse the parameter as WKT.", e);
        }
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry[] flattenAllGeometryCollections(
            final com.vividsolutions.jts.geom.Geometry geom) {
        return JtsTransformer.flattenAllGeometryCollections(geom);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry[] flattenGeometryCollections(
            final com.vividsolutions.jts.geom.Geometry geom) {
        return JtsTransformer.flattenGeometryCollections(geom).toArray(new com.vividsolutions.jts.geom.Geometry[0]);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry[] geometriesWithDimension(
            final com.vividsolutions.jts.geom.Geometry geom, final int dimension) throws GmlGeoXException {
        return GeometryAnalysis.geometriesWithDimension(geom, dimension);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Point[] directionChanges(final com.vividsolutions.jts.geom.Geometry geom,
            final Object minAngle, final Object maxAngle) throws GmlGeoXException {
        return CurveAnalysis.directionChanges(geom, minAngle, maxAngle);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Point[] directionChangesGreaterThanLimit(
            final com.vividsolutions.jts.geom.Geometry geom, final Object limitAngle) throws GmlGeoXException {
        return CurveAnalysis.directionChangesGreaterThanLimit(geom, limitAngle);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean contains(final ANode geom1, final ANode geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CONTAINS,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean contains(final Value arg1, final Value arg2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.CONTAINS,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean containsGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CONTAINS,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public String intersectionMatrix(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) {
        return SpatialRelationshipEvaluator.intersectionMatrix(geom1, geom2);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean containsGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CONTAINS,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean crosses(final ANode geom1, final ANode geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CROSSES,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean crosses(final Value arg1, final Value arg2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.CROSSES,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean crossesGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CROSSES,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean crossesGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.CROSSES,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean equals(final ANode geom1, final ANode geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.EQUALS,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean equals(final Value arg1, final Value arg2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.EQUALS, matchAll,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean equalsGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.EQUALS,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean equalsGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.EQUALS,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersects(final ANode geom1, final ANode geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.INTERSECTS,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersects(final Value arg1, final Value arg2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.INTERSECTS,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersectsGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.INTERSECTS,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersectsGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.INTERSECTS,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public String determineSrsName(final ANode geometryNode) {
        return this.context.srsLookup.determineSrsName(geometryNode);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public String determineSrsNameForGeometryComponent(final ANode geometryComponentNode) {
        return this.context.srsLookup.determineSrsNameForGeometryComponent(geometryComponentNode);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry parseGeometry(final Value v) throws GmlGeoXException {
        return this.context.jtsTransformer.parseGeometry(v);
    }

    /**
     * @param o
     *            Value, an Object[], a JTS geometry (also a geometry collection) or anything else
     * @return a list of single objects (Value of size > 1 is flattened to a list of Items, a JTS geometry collection is
     *         flattened as well)
     */
    public static Collection<?> toObjectCollection(final @NotNull Object o) {
        if (o instanceof Value) {
            final Value v = (Value) o;
            if (v.size() > 1) {
                final List<Object> result = new ArrayList<>((int) v.size());
                for (Item i : v) {
                    result.addAll(toObjectCollection(i));
                }
                return result;
            } else if (v instanceof Jav) {
                return Collections.singleton(((Jav) o).toJava());
            } else {
                return Collections.singleton(o);
            }
        } else if (o instanceof Object[]) {
            final List<Object> result = new ArrayList<>(((Object[]) o).length);
            for (Object os : (Object[]) o) {
                result.addAll(toObjectCollection(os));
            }
            return result;
        } else {
            if (o instanceof com.vividsolutions.jts.geom.Geometry) {
                final com.vividsolutions.jts.geom.Geometry geom = (com.vividsolutions.jts.geom.Geometry) o;
                return JtsTransformer.flattenGeometryCollections(geom);
            } else {
                return Collections.singleton(o);
            }
        }
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjoint(final ANode geom1, final ANode geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISDISJOINT,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjoint(final Value arg1, final Value arg2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.ISDISJOINT,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjointGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISDISJOINT,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjointGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISDISJOINT,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithin(final ANode geom1, final ANode geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISWITHIN,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithin(final Value arg1, final Value arg2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.ISWITHIN,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithinGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISWITHIN,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithinGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.ISWITHIN,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlaps(final ANode geom1, final ANode geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.OVERLAPS,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlaps(final Value arg1, final Value arg2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.OVERLAPS,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlapsGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.OVERLAPS,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlapsGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.OVERLAPS,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean touches(final ANode geom1, final ANode geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.TOUCHES,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean touches(final Value arg1, final Value arg2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(arg1, arg2, SpatialRelOp.TOUCHES,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean touchesGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.TOUCHES,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean touchesGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applySpatialRelationshipOperation(geom1, geom2, SpatialRelOp.TOUCHES,
                matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry union(final Value val) throws GmlGeoXException {
        return SpatialSetOperators.union(val, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry[] mergeLinesGeom(final com.vividsolutions.jts.geom.Geometry geom)
            throws GmlGeoXException {
        return GeometryAnalysis.mergeLinesGeom(geom, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isEmptyGeom(final com.vividsolutions.jts.geom.Geometry geom) {
        return GeometryAnalysis.isEmptyGeom(geom);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public JtsSridCoordinate checkSecondControlPointInMiddleThirdOfArc(final ANode arcStringNode) throws GmlGeoXException {
        return ArcAnalysis.checkSecondControlPointInMiddleThirdOfArc(arcStringNode, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public JtsSridCoordinate checkMinimumSeparationOfCircleControlPoints(final ANode circleNode,
            final Object minSeparationInDegree) throws GmlGeoXException {
        return CircleAnalysis.checkMinimumSeparationOfCircleControlPoints(circleNode, minSeparationInDegree,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosedGeom(final com.vividsolutions.jts.geom.Geometry geom) throws GmlGeoXException {
        return isClosedGeom(geom, true);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosed(final ANode geom) throws GmlGeoXException {
        return isClosed(geom, true);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosedGeom(final com.vividsolutions.jts.geom.Geometry geom, final boolean onlyCheckCurveGeometries)
            throws GmlGeoXException {
        return GeometryAnalysis.isClosedGeom(geom, onlyCheckCurveGeometries);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosed(final ANode geomNode, final boolean onlyCheckCurveGeometries) throws GmlGeoXException {
        return isClosedGeom(getOrCacheGeometry(geomNode), onlyCheckCurveGeometries);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry holes(final ANode geometryNode) throws GmlGeoXException {
        return holesGeom(getOrCacheGeometry(geometryNode));
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry holesGeom(final com.vividsolutions.jts.geom.Geometry geom) {
        return GeometryAnalysis.holesGeom(geom, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry holesAsGeometryCollection(
            final com.vividsolutions.jts.geom.Geometry geom) {
        return GeometryAnalysis.holesAsGeometryCollection(geom, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean relate(final ANode arg1, final ANode arg2, final String intersectionPattern)
            throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applyRelate(arg1, arg2, intersectionPattern, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean relate(final Value value1, final Value value2, final String intersectionPattern,
            final boolean matchAll) throws GmlGeoXException {
        return SpatialRelationshipEvaluator.relateMatch(value1, value2, intersectionPattern, matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean relateGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final String intersectionPattern)
            throws GmlGeoXException {
        return SpatialRelationshipEvaluator.applyRelate(geom1, geom2, intersectionPattern, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean relateGeomGeom(final com.vividsolutions.jts.geom.Geometry geom1,
            final com.vividsolutions.jts.geom.Geometry geom2, final String intersectionPattern, final boolean matchAll)
            throws GmlGeoXException {
        return SpatialRelationshipEvaluator.relateMatch(geom1, geom2, intersectionPattern, matchAll, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry intersection(final ANode geometry1, final ANode geometry2)
            throws GmlGeoXException {
        return SpatialSetOperators.intersection(geometry1, geometry2, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry intersectionGeomGeom(
            final com.vividsolutions.jts.geom.Geometry geometry1, final com.vividsolutions.jts.geom.Geometry geometry2)
            throws GmlGeoXException {
        return SpatialSetOperators.intersectionGeomGeom(geometry1, geometry2);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry difference(final ANode geometry1, final ANode geometry2)
            throws GmlGeoXException {
        return SpatialSetOperators.difference(geometry1, geometry2, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry differenceGeomGeom(final com.vividsolutions.jts.geom.Geometry geometry1,
            final com.vividsolutions.jts.geom.Geometry geometry2) throws GmlGeoXException {
        return SpatialSetOperators.differenceGeomGeom(geometry1, geometry2);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry boundary(final ANode geometryNode) throws GmlGeoXException {
        return boundaryGeom(getOrCacheGeometry(geometryNode));
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry boundaryGeom(final com.vividsolutions.jts.geom.Geometry geometry) {
        return GeometryAnalysis.boundaryGeom(geometry, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public double area(final ANode geometryNode) throws GmlGeoXException {
        return areaGeom(getOrCacheGeometry(geometryNode));
    }

    @Requires(Permission.NONE)
    @Deterministic
    public double areaGeom(final com.vividsolutions.jts.geom.Geometry geometry) {
        return GeometryAnalysis.areaGeom(geometry);
    }

    /**
     * Returns the length of the given geometry. For a linear geometry, its length is returned. For an areal geometry, the
     * length of its perimeter is returned. For (multi-) point geometries, 0.0 is returned.
     *
     * @param jtsGeom
     *            the JTS geometry object
     * @return the length of the geometry
     */
    @Requires(Permission.NONE)
    @Deterministic
    public double lengthGeom(final com.vividsolutions.jts.geom.Geometry jtsGeom) {
        if (jtsGeom == null) {
            return 0;
        } else {
            return jtsGeom.getLength();
        }
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Point centroid(final ANode geometryNode) throws GmlGeoXException {
        return centroidGeom(getOrCacheGeometry(geometryNode));
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Point centroidGeom(final com.vividsolutions.jts.geom.Geometry geometry) {
        return GeometryAnalysis.centroidGeom(geometry);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public Object[] envelope(final ANode geometryNode) throws GmlGeoXException {
        return this.context.geometryCache().envelope(geometryNode, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public Object[] envelopeGeom(final com.vividsolutions.jts.geom.Geometry geometry) {
        return JtsTransformer.envelopeGeom(geometry);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Point[] curveEndPoints(final ANode geomNode) throws GmlGeoXException {
        return JtsTransformer.curveEndPoints(geomNode, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry[] curveComponents(final ANode geomNode) throws GmlGeoXException {
        return JtsTransformer.curveComponents(geomNode, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry[] curveComponentsGranular(final ANode geomNode) throws GmlGeoXException {
        return JtsTransformer.curveComponentsGranular(geomNode, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(final Object minx, final Object miny, final Object maxx, final Object maxy)
            throws GmlGeoXException {
        return this.context.indexRegister().search(minx, miny, maxx, maxy, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(final String indexName, final Object minx, final Object miny, final Object maxx,
            final Object maxy) throws GmlGeoXException {
        return this.context.indexRegister().search(indexName, minx, miny, maxx, maxy, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(final ANode geometryNode) throws GmlGeoXException {
        return this.context.indexRegister().search(geometryNode, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(final String indexName, final ANode geometryNode) throws GmlGeoXException {
        return this.context.indexRegister().search(indexName, geometryNode, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] searchGeom(final com.vividsolutions.jts.geom.Geometry geom) throws GmlGeoXException {
        return this.context.indexRegister().searchGeom(geom, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] searchGeom(final String indexName, final com.vividsolutions.jts.geom.Geometry geom)
            throws GmlGeoXException {
        return this.context.indexRegister().searchGeom(indexName, geom, this.context);
    }

    @Requires(Permission.NONE)
    public DBNode[] search() throws GmlGeoXException {
        return this.context.indexRegister().search(this.context);
    }

    @Requires(Permission.NONE)
    public DBNode[] searchInIndex(final String indexName) throws GmlGeoXException {
        return this.context.indexRegister().searchInIndex(indexName, this.context);
    }

    @Requires(Permission.NONE)
    public DBNode[] entriesOfGivenIndex(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index)
            throws GmlGeoXException {
        return this.context.indexRegister().entriesOfIndex(index, this.context);
    }

    @Requires(Permission.NONE)
    public void cacheSize(final Object size) throws GmlGeoXException {
        final int newSize;
        if (size instanceof BigInteger) {
            newSize = ((BigInteger) size).intValue();
        } else if (size instanceof Integer) {
            newSize = (Integer) size;
        } else {
            throw new GmlGeoXException("Unsupported parameter type: " + size.getClass().getName());
        }
        this.context.geometryCache().resetCache(newSize);
    }

    @Requires(Permission.NONE)
    public int getCacheSize() {
        return this.context.geometryCache().getCacheSize();
    }

    @Requires(Permission.NONE)
    public void index(final ANode node, final ANode geometry) throws GmlGeoXException {
        this.context.indexRegister().index(node, geometry, this.context);
    }

    @Requires(Permission.NONE)
    public void index(final String indexName, final ANode node, final ANode geometry) throws GmlGeoXException {
        this.context.indexRegister().index(indexName, node, geometry, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean pointCoordInGeometryCoords(final com.vividsolutions.jts.geom.Point point,
            final com.vividsolutions.jts.geom.Geometry geometry) {
        return GeometryAnalysis.pointCoordInGeometryCoords(point, geometry);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry curveUnmatchedByIdenticalCurvesMin(final ANode geomNode,
            final Value otherGeomsNodes, final int minMatchesPerCurve) throws GmlGeoXException {
        return CurveComponentAnalysis.curveUnmatchedByIdenticalCurvesMin(geomNode, otherGeomsNodes, minMatchesPerCurve,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry curveUnmatchedByIdenticalCurvesMax(final ANode geomNode,
            final Value otherGeomsNodes, final int maxMatchesPerCurve) throws GmlGeoXException {
        return CurveComponentAnalysis.curveUnmatchedByIdenticalCurvesMax(geomNode, otherGeomsNodes, maxMatchesPerCurve,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry determineIncompleteCoverageByIdenticalCurveComponents(
            final ANode geomNode, final Value otherGeomNodes) throws GmlGeoXException {
        return CurveComponentAnalysis.determineIncompleteCoverageByIdenticalCurveComponents(geomNode, otherGeomNodes,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry determineInteriorIntersectionOfCurveComponents(final ANode geomNode1,
            final ANode geomNode2) throws GmlGeoXException {
        return CurveComponentAnalysis.determineInteriorIntersectionOfCurveComponents(geomNode1, geomNode2,
                this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isValid1DimIntersectionsOfCurves(final ANode geomNode, final ANode otherGeomNode,
            final String curveSegmentMatchCriterium) throws GmlGeoXException {
        return CurveComponentAnalysis.isValid1DimIntersectionsOfCurves(geomNode, otherGeomNode,
                curveSegmentMatchCriterium, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    @NotNull
    public com.vividsolutions.jts.geom.Geometry getOrCacheGeometry(final ANode geomNode) throws GmlGeoXException {
        return this.context.geometryCache().getOrCacheGeometry(geomNode, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Point[] getControlPointsArray(final ANode geomNode,
            final String controlPointSearchBehavior) throws GmlGeoXException {
        final List<com.vividsolutions.jts.geom.Point> controlPoints = GeometryPointsAnalysis.getControlPoints(geomNode,
                controlPointSearchBehavior, this.context);
        return controlPoints.toArray(new com.vividsolutions.jts.geom.Point[0]);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public FElem determineDetailsOfPointsBetweenLinearCurveSegments(final ANode geomNode) throws GmlGeoXException {
        return CurveComponentAnalysis.determineDetailsOfPointsBetweenLinearCurveSegments(geomNode, this.context);
    }

    @Requires(Permission.NONE)
    public void prepareSpatialIndex(final ANode node, final ANode geometry) throws GmlGeoXException {
        this.context.indexRegister().prepareSpatialIndex(node, geometry, this.context);
    }

    @Requires(Permission.NONE)
    public void prepareSpatialIndex(final String indexName, final ANode node, final ANode geometry)
            throws GmlGeoXException {
        this.context.indexRegister().prepareSpatialIndex(indexName, node, geometry, this.context);
    }

    @Deterministic
    @Requires(Permission.NONE)
    public FElem detectNearestPoints(final Value featuresToSearchBy, final Value featuresToSearchByGeom,
            final Value featuresToSearchIn, final Value featuresToSearchInGeom, final String controlPointSearchBehavior,
            final double minDistance, final double maxDistance, final int limitErrors, final double tileLength,
            final double tileOverlap, final boolean ignoreIdenticalPoints) throws GmlGeoXException {

        return GeometryPointsAnalysis.detectNearestPoints(featuresToSearchBy, featuresToSearchByGeom,
                featuresToSearchIn, featuresToSearchInGeom, controlPointSearchBehavior, minDistance, maxDistance,
                limitErrors, tileLength, tileOverlap, ignoreIdenticalPoints, this.context);
    }

    @Requires(Permission.NONE)
    public void prepareSpatialPointIndex(final @NotNull String indexName, final ANode node, final ANode geometry,
            final String controlPointSearchBehavior) throws GmlGeoXException {
        this.context.indexRegister().prepareSpatialPointIndex(indexName, node, geometry, controlPointSearchBehavior,
                this.context);
    }

    @Deterministic
    @Requires(Permission.NONE)
    public com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> spatialIndexEntry(
            final ANode node, final ANode geometry) throws GmlGeoXException {
        return SpatialIndexRegister.spatialIndexEntry(node, geometry, this.context);
    }

    @Deterministic
    @Requires(Permission.NONE)
    public com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>[] spatialPointIndexEntries(
            final ANode node, final ANode geometry, final String controlPointSearchBehavior) throws GmlGeoXException {
        return SpatialIndexRegister.spatialPointIndexEntries(node, geometry, controlPointSearchBehavior, this.context);
    }

    @Deterministic
    @Requires(Permission.NONE)
    public RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> spatialIndex(final Object entries) {
        return SpatialIndexRegister.spatialIndex(entries);
    }

    @Deterministic
    @Requires(Permission.NONE)
    public DBNode[] searchIndex(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            final ANode geometryNode) throws GmlGeoXException {
        return this.context.indexRegister().searchIndex(index, geometryNode, this.context);
    }

    @Deterministic
    @Requires(Permission.NONE)
    public DBNode[] searchIndexGeom(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            final com.vividsolutions.jts.geom.Geometry jtsGeom) {
        return this.context.indexRegister().searchIndexGeom(index, jtsGeom, this.context);
    }

    @Deterministic
    @Requires(Permission.NONE)
    public DBNode[] nearestSearchIndex(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            final ANode geometryNode, final double maxDistance, final int maxCount) throws GmlGeoXException {
        return this.context.indexRegister().nearestSearchIndex(index, geometryNode, maxDistance, maxCount,
                this.context);
    }

    @Deterministic
    @Requires(Permission.NONE)
    public DBNode[] nearestSearchIndexGeom(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            final com.vividsolutions.jts.geom.Geometry jtsGeom, final double maxDistance, final int maxCount) {
        return this.context.indexRegister().nearestSearchIndexGeom(index, jtsGeom, maxDistance, maxCount, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public double distancePointToSurface(final @NotNull com.vividsolutions.jts.geom.Point point,
            final @NotNull ANode surfaceGeometry) throws GmlGeoXException {
        return GeometryAnalysis.distancePointToSurfaceBoundary(point, surfaceGeometry, this.context);
    }

    @Requires(Permission.NONE)
    @Deterministic
    public double distancePointToPoint(final @NotNull com.vividsolutions.jts.geom.Point point1,
            final @NotNull com.vividsolutions.jts.geom.Point point2) throws GmlGeoXException {
        return GeometryAnalysis.distancePointToPoint(point1, point2);
    }

    @Requires(Permission.NONE)
    public void prepareDefaultAndSpecificSpatialIndex(final String indexName, final ANode node, final ANode geometry)
            throws GmlGeoXException {
        this.context.indexRegister().prepareDefaultAndSpecificSpatialIndex(indexName, node, geometry, context);
    }

    @Requires(Permission.NONE)
    public void buildSpatialIndex() throws GmlGeoXException {
        this.context.indexRegister().buildSpatialIndex();
    }

    @Requires(Permission.NONE)
    public void buildSpatialIndex(final String indexName) throws GmlGeoXException {
        this.context.indexRegister().buildSpatialIndex(indexName);
    }

    /**
     * Retrieve the first two coordinates of a given geometry.
     *
     * @param geom
     *            the geometry
     * @return an empty array if the geometry is null or empty, otherwise an array with the x and y from the first
     *         coordinate of the geometry
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String[] georefFromGeom(final com.vividsolutions.jts.geom.Geometry geom) {
        if (geom == null || geom.isEmpty()) {
            return new String[]{};
        } else {
            Coordinate firstCoord = geom.getCoordinates()[0];
            return new String[]{"" + firstCoord.x, "" + firstCoord.y, "" + geom.getSRID()};
        }
    }

    /**
     * Retrieve x and y of the given coordinate, as strings without scientific notation.
     *
     * @param coord
     *            the coordinate
     * @return an array with the x and y of the given coordinate, as strings without scientific notation.
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String[] georefFromCoord(final JtsSridCoordinate coord) throws GmlGeoXException {
        return new String[]{"" + coord.x, "" + coord.y, "" + coord.srid()};
    }

    @Requires(Permission.NONE)
    @Deterministic
    public com.vividsolutions.jts.geom.Geometry interiorIntersectionsLinePolygon(
            final com.vividsolutions.jts.geom.Geometry line, final com.vividsolutions.jts.geom.Geometry polygon)
            throws GmlGeoXException {
        return GeometryAnalysis.interiorIntersectionsLinePolygon(line, polygon, this.context);
    }

    @Requires(Permission.READ)
    @Deterministic
    public String detailedVersion() {
        try {
            final URLClassLoader cl = (URLClassLoader) getClass().getClassLoader();
            final URL url = cl.findResource("META-INF/MANIFEST.MF");
            final Manifest manifest = new Manifest(url.openStream());
            final String version = manifest.getMainAttributes().getValue("Implementation-Version");
            final String buildTime = manifest.getMainAttributes().getValue("Build-Date").substring(2);
            return version + "-b" + buildTime;
        } catch (Exception E) {
            return "unknown";
        }
    }

    @Requires(Permission.ADMIN)
    public GmlGeoX getModuleInstance() {
        return this;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(this.context.dbNodeRefFactory.getDbNamePrefix());
        this.context.write(out);
    }

    @Override
    @ContextDependent
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {

        // ensure that the CRSManager has been initialised
        initCRSManager();

        // Todo read from meta
        final BxNamespaceHolder bxNamespaceHolder = BxNamespaceHolder.init(this.queryContext);

        String dbNamePrefix = in.readUTF();
        DBNodeRefFactory dbNodeRefFactory = DBNodeRefFactory.create(dbNamePrefix + "000");
        DBNodeRefLookup dbNodeRefLookup = new DBNodeRefLookup(this.queryContext, dbNodeRefFactory);

        this.context = new GmlGeoXContext(bxNamespaceHolder, dbNodeRefFactory, dbNodeRefLookup);
        this.context.read(in);
    }

    @Override
    public void close() {
        this.queryContext = null;
        this.staticContext = null;
        this.context = null;
        RxJavaPlugins.getInstance().reset();
        RxJavaHooks.clear();
    }
}
