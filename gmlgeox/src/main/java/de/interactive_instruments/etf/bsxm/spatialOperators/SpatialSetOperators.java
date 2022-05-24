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

import java.util.ArrayList;
import java.util.List;

import org.basex.query.value.Value;
import org.basex.query.value.item.Item;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;
import org.deegree.geometry.Geometry;

import de.interactive_instruments.etf.bsxm.GeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXException;
import de.interactive_instruments.etf.bsxm.JtsTransformer;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class SpatialSetOperators {

    /**
     * Computes the difference between the first and the second geometry node.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry1
     *            represents the first geometry
     * @param geometry2
     *            represents the second geometry
     * @param context
     *            tbd
     * @return the closure of the point-set of the points contained in geometry1 that are not contained in geometry2.
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry difference(final ANode geometry1, final ANode geometry2,
            final GmlGeoXContext context) throws GmlGeoXException {
        try {
            final com.vividsolutions.jts.geom.Geometry geom1 = context.geometryCache().getOrCacheGeometry(geometry1,
                    context);
            final com.vividsolutions.jts.geom.Geometry geom2 = context.geometryCache().getOrCacheGeometry(geometry2,
                    context);
            return geom1.difference(geom2);
        } catch (final Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * Create the union of the given geometry nodes.
     *
     * NOTE: Does NOT merge line strings!
     *
     * @param val
     *            a single or collection of geometry nodes.
     * @param context
     *            tbd
     * @return the union of the geometries - can be a JTS geometry collection
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry union(final Value val, final GmlGeoXContext context)
            throws GmlGeoXException {

        try {

            // first get all values
            final List<Item> items = new ArrayList<>((int) val.size());

            for (Item i : val) {
                items.add(i);
            }

            /* Now, create unions from partitions of the list of items. */
            final List<com.vividsolutions.jts.geom.Geometry> unions = new ArrayList<>((int) Math.sqrt(items.size()));

            int x = (int) (Math.ceil((double) items.size() / 1000) - 1);

            for (int groupIndex = 0; groupIndex <= x; groupIndex++) {

                final int groupStart = (x - groupIndex) * 1000;
                int groupEnd = ((x - groupIndex) + 1) * 1000;

                if (groupEnd > items.size()) {
                    groupEnd = items.size();
                }

                final List<Item> itemsSublist = items.subList(groupStart, groupEnd);

                final List<com.vividsolutions.jts.geom.Geometry> geomsInSublist = new ArrayList<>(itemsSublist.size());

                for (Item i : itemsSublist) {
                    final com.vividsolutions.jts.geom.Geometry geom;
                    if (i instanceof DBNode) {
                        geom = context.geometryCache().getOrCacheGeometry((DBNode) i, context);
                    } else {
                        geom = context.jtsTransformer.toJTSGeometry(i);
                    }
                    geomsInSublist.add(geom);
                }

                com.vividsolutions.jts.geom.GeometryCollection sublistGc = context.jtsTransformer
                        .toJTSGeometryCollection(geomsInSublist, true);

                unions.add(sublistGc.union());
            }

            /* Finally, create a union from the list of unions. */
            com.vividsolutions.jts.geom.GeometryCollection unionsGc = context.jtsTransformer
                    .toJTSGeometryCollection(unions, true);

            return unionsGc.union();

        } catch (Exception e) {
            throw new GmlGeoXException("Exception occurred while applying union(Value)). Message is: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Create the union of the given geometry objects.
     *
     * NOTE: Does NOT merge line strings!
     *
     * @param geoms
     *            a collection of JTS geometry objects.
     * @param context
     *            tbd
     * @return the union of the geometries - can be a JTS geometry collection
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry unionGeom(final List<com.vividsolutions.jts.geom.Geometry> geoms,
            GeoXContext context) throws GmlGeoXException {

        try {
            com.vividsolutions.jts.geom.GeometryCollection gc = context.jtsTransformer.toJTSGeometryCollection(geoms,
                    true);

            return gc.union();
        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * Computes the difference between the first and the second geometry.
     *
     * @param geometry1
     *            the first geometry
     * @param geometry2
     *            the second geometry
     * @return the closure of the point-set of the points contained in geometry1 that are not contained in geometry2.
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry differenceGeomGeom(
            final com.vividsolutions.jts.geom.Geometry geometry1, final com.vividsolutions.jts.geom.Geometry geometry2)
            throws GmlGeoXException {
        try {
            return geometry1.difference(geometry2);
        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    // public com.vividsolutions.jts.geom.Geometry detailedDifferenceGeomGeom(final
    // com.vividsolutions.jts.geom.Geometry
    // geometry1,
    // final com.vividsolutions.jts.geom.Geometry geometry2) throws QueryException {
    //
    // com.vividsolutions.jts.geom.Geometry[] g1geoms =
    // flattenAllGeometryCollections(
    // geometry1);
    // com.vividsolutions.jts.geom.Geometry[] g2geoms =
    // flattenAllGeometryCollections(
    // geometry2);
    //
    // List<com.vividsolutions.jts.geom.Geometry> result = new ArrayList<>();
    //
    // for (com.vividsolutions.jts.geom.Geometry g1g : g1geoms) {
    //
    // com.vividsolutions.jts.geom.Geometry diffResult = g1g;
    //
    // for (com.vividsolutions.jts.geom.Geometry g2g : g2geoms) {
    //
    // try {
    // diffResult = diffResult.difference(g2g);
    // } catch (Exception e) {
    // throw new QueryException(e.getClass().getName()
    // + " while computing the difference. Message is: " + e.getMessage());
    // }
    //
    // if (diffResult.isEmpty()) {
    // break;
    // }
    // }
    //
    // if (!diffResult.isEmpty()) {
    // result.add(diffResult);
    // }
    // }
    //
    // if (result.isEmpty()) {
    // return null;
    // } else {
    // return unionGeom(result);
    // }
    // }

    /**
     * Computes the intersection between the first and the second geometry node.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry1
     *            represents the first geometry
     * @param geometry2
     *            represents the second geometry
     * @param context
     *            tbd
     * @return the point-set common to the two geometries
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry intersection(final ANode geometry1, final ANode geometry2,
            final GmlGeoXContext context) throws GmlGeoXException {
        try {
            final com.vividsolutions.jts.geom.Geometry geom1 = context.geometryCache().getOrCacheGeometry(geometry1,
                    context);
            final com.vividsolutions.jts.geom.Geometry geom2 = context.geometryCache().getOrCacheGeometry(geometry2,
                    context);
            return geom1.intersection(geom2);
        } catch (final Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * Computes the intersection between the first and the second geometry.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry1
     *            the first geometry
     * @param geometry2
     *            the second geometry
     * @return the point-set common to the two geometries
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.vividsolutions.jts.geom.Geometry intersectionGeomGeom(
            final com.vividsolutions.jts.geom.Geometry geometry1, final com.vividsolutions.jts.geom.Geometry geometry2)
            throws GmlGeoXException {
        try {
            return geometry1.intersection(geometry2);
        } catch (final Exception e) {
            throw new GmlGeoXException(e);
        }
    }
}
