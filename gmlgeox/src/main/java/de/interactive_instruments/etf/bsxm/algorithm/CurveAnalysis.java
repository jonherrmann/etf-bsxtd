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
import java.util.List;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;

import de.interactive_instruments.etf.bsxm.GmlGeoXException;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class CurveAnalysis {

    private CurveAnalysis() {}

    /**
     * Identifies points of the given line, where the segment that ends in a point and the following segment that starts
     * with that point form a change in direction whose angular value is within a given interval.
     *
     * @param geom
     *            A LineString which shall be checked for directional changes whose value is within the given interval.
     * @param minAngle
     *            Minimum directional change to be considered, in degrees. 0<=minAngle<=maxAngle<=180
     * @param maxAngle
     *            Maximum directional change to be considered, in degrees. 0<=minAngle<=maxAngle<=180
     * @return The point(s) where the line has a directional change within the given change interval. Can be empty in case
     *         that the given geometry is <code>null</code> or only has one segment, but not <code>null</code>.
     * @throws GmlGeoXException
     *             If the given geometry is not a LineString, or the minimum and maximum values are incorrect.
     */
    public static com.vividsolutions.jts.geom.Point[] directionChanges(final com.vividsolutions.jts.geom.Geometry geom,
            final Object minAngle, final Object maxAngle) throws GmlGeoXException {

        final List<com.vividsolutions.jts.geom.Point> result = new ArrayList<>();

        if (geom != null) {

            if (!(geom instanceof LineString || geom instanceof MultiLineString)) {

                throw new GmlGeoXException("Geometry must be a LineString. Found: " + geom.getClass().getName());

            } else {

                double min;
                double max;

                if (minAngle instanceof Number && maxAngle instanceof Number) {

                    min = ((Number) minAngle).doubleValue();
                    max = ((Number) maxAngle).doubleValue();

                    if (min < 0 || max < 0 || min > 180 || max > 180 || min > max) {
                        throw new GmlGeoXException("0 <= minAngle <= maxAngle <= 180. Found minAngle '" + minAngle
                                + "', maxAngle '" + maxAngle + "'.");
                    }
                } else {
                    throw new GmlGeoXException("minAngle and maxAngle must be numbers");
                }

                final Coordinate[] coords = geom.getCoordinates();

                if (coords.length >= 3) {

                    final GeometryFactory fac = geom.getFactory();

                    for (int i = 0; i < coords.length - 2; i++) {

                        final Coordinate coord1 = coords[i];
                        final Coordinate coord2 = coords[i + 1];
                        final Coordinate coord3 = coords[i + 2];

                        final double diff_deg = Angles.directionChangeInDegree(coord1, coord2, coord3);

                        if (diff_deg >= min && diff_deg <= max) {
                            com.vividsolutions.jts.geom.Point p = fac.createPoint(coord2);
                            result.add(p);
                        }
                    }
                }

            }
        }

        return result.toArray(new com.vividsolutions.jts.geom.Point[result.size()]);
    }

    /**
     * Identifies points of the given line, where the segment that ends in a point and the following segment that starts
     * with that point form a change in direction whose angular value is greater than the given limit.
     *
     * @param geom
     *            A LineString which shall be checked for directional changes that are greater than the given limit.
     * @param limitAngle
     *            Angular value of directional change that defines the limit, in degrees. 0 <= limitAngle <= 180
     * @return The point(s) where the line has a directional change that is greater than the given limit. Can be empty in
     *         case that the given geometry is <code>null</code> or only has one segment, but not <code>null</code>.
     * @throws GmlGeoXException
     *             If the given geometry is not a LineString, or the limit value is incorrect.
     */
    public static com.vividsolutions.jts.geom.Point[] directionChangesGreaterThanLimit(
            final com.vividsolutions.jts.geom.Geometry geom, final Object limitAngle) throws GmlGeoXException {

        final List<com.vividsolutions.jts.geom.Point> result = new ArrayList<>();

        if (geom != null) {

            if (!(geom instanceof LineString || geom instanceof MultiLineString)) {

                throw new GmlGeoXException("Geometry must be a LineString. Found: " + geom.getClass().getName());

            } else {

                double limit;

                if (limitAngle instanceof Number) {

                    limit = ((Number) limitAngle).doubleValue();

                    if (limit < 0 || limit > 180) {
                        throw new GmlGeoXException("0 <= limitAngle <= 180. Found limitAngle '" + limitAngle + "'.");
                    }
                } else {
                    throw new GmlGeoXException("limitAngle must be a number");
                }

                final Coordinate[] coords = geom.getCoordinates();

                if (coords.length >= 3) {

                    final GeometryFactory fac = geom.getFactory();

                    for (int i = 0; i < coords.length - 2; i++) {

                        Coordinate coord1 = coords[i];
                        Coordinate coord2 = coords[i + 1];
                        Coordinate coord3 = coords[i + 2];

                        final double diff_deg = Angles.directionChangeInDegree(coord1, coord2, coord3);

                        if (diff_deg > limit) {
                            com.vividsolutions.jts.geom.Point p = fac.createPoint(coord2);
                            result.add(p);
                        }
                    }
                }
            }
        }

        return result.toArray(new com.vividsolutions.jts.geom.Point[result.size()]);
    }
}
