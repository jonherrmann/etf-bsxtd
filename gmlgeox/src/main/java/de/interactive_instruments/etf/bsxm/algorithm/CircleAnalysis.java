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

import com.vividsolutions.jts.algorithm.Angle;
import com.vividsolutions.jts.geom.Coordinate;

import org.basex.query.value.node.ANode;

import de.interactive_instruments.etf.bsxm.GeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXException;
import de.interactive_instruments.etf.bsxm.geometry.Circle;
import de.interactive_instruments.etf.bsxm.geometry.InvalidCircleException;
import de.interactive_instruments.etf.bsxm.geometry.JtsSridCoordinate;
import de.interactive_instruments.etf.bsxm.geometry.JtsSridCoordinates;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class CircleAnalysis {

    private CircleAnalysis() {}

    /**
     * Check that the three control points of a gml:Circle are at least a given amount of degrees apart from each other.
     *
     * @param circleNode
     *            A gml:Circle element, defined by three control points
     * @param minSeparationInDegree
     *            the minimum angle between each control point, in degree (0<=x<=120)
     * @param context
     *            tbd
     * @return The coordinate of a control point which does not have the minimum angle to one of the other control points,
     *         or <code>null</code> if the angles between all points are greater than or equal to the minimum separation
     *         angle
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static JtsSridCoordinate checkMinimumSeparationOfCircleControlPoints(final ANode circleNode,
            final Object minSeparationInDegree, final GeoXContext context) throws GmlGeoXException {

        double minSepDeg;
        if (minSeparationInDegree instanceof Number) {
            minSepDeg = ((Number) minSeparationInDegree).doubleValue();
        } else {
            throw new GmlGeoXException("Parameter minSeparationInDegree must be a number.");
        }

        if (minSepDeg < 0 || minSepDeg > 120) {
            throw new GmlGeoXException("Invalid parameter minSeparationInDegree (must be >= 0 and <= 120).");
        }

        final JtsSridCoordinates controlPoints = context.jtsTransformer.parseArcStringControlPoints(circleNode);
        final JtsSridCoordinate c1 = controlPoints.getSridCoordinate(0);
        final JtsSridCoordinate c2 = controlPoints.getSridCoordinate(1);
        final JtsSridCoordinate c3 = controlPoints.getSridCoordinate(2);

        Circle circle;
        try {
            circle = Circle.from3Coordinates(c1, c2, c3);
        } catch (InvalidCircleException e) {
            return c1;
        }
        final Coordinate center = circle.center();

        // NOTE: angle in radians (0,PI)
        double d12 = Angle.angleBetween(c1, center, c2);
        double d13 = Angle.angleBetween(c1, center, c3);
        double d23 = Angle.angleBetween(c2, center, c3);

        final double minSeparation = Angle.toRadians(minSepDeg);

        if (d12 < minSeparation) {
            return c1;
        } else if (d13 < minSeparation) {
            return c1;
        } else if (d23 < minSeparation) {
            return c2;
        } else {
            return null;
        }
    }
}
