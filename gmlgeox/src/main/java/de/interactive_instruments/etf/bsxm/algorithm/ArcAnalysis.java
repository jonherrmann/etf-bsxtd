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
public class ArcAnalysis {

    private ArcAnalysis() {}

    /**
     * Checks that the second control point of each arc in the given $arcStringNode is positioned in the middle third of
     * that arc.
     *
     * @param arcStringNode
     *            A gml:Arc or gml:ArcString element
     * @param context
     *            tbd
     * @return The coordinate of the second control point of the first invalid arc, or <code>null
     *     </code> if all arcs are valid.
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static JtsSridCoordinate checkSecondControlPointInMiddleThirdOfArc(final ANode arcStringNode,
            final GeoXContext context) throws GmlGeoXException {

        final JtsSridCoordinates controlPoints = context.jtsTransformer.parseArcStringControlPoints(arcStringNode);

        for (int i = 2; i < controlPoints.size(); i = i + 2) {

            final JtsSridCoordinate c1 = controlPoints.getSridCoordinate(i - 2);
            final JtsSridCoordinate c2 = controlPoints.getSridCoordinate(i - 1);
            final JtsSridCoordinate c3 = controlPoints.getSridCoordinate(i);

            Circle circle;
            try {
                circle = Circle.from3Coordinates(c1, c2, c3);
            } catch (InvalidCircleException e) {
                return c2;
            }
            final Coordinate center = circle.center();
            final double d12 = Angle.angleBetweenOriented(c1, center, c2);
            final double d13 = Angle.angleBetweenOriented(c1, center, c3);

            final boolean controlPointsOrientedClockwise = controlPointsOrientedClockwise(d12, d13);

            final double fullAngle1to2 = fullAngle(d12, controlPointsOrientedClockwise);
            final double fullAngle1to3 = fullAngle(d13, controlPointsOrientedClockwise);

            final double thirdOfFullAngle1to3 = fullAngle1to3 / 3;

            final double middleThirdStart = thirdOfFullAngle1to3;
            final double middleThirdEnd = fullAngle1to3 - thirdOfFullAngle1to3;

            if (middleThirdStart > fullAngle1to2 || middleThirdEnd < fullAngle1to2) {
                return c2;
            }
        }

        return null;
    }

    private static double fullAngle(double angleBetweenOrientedInRadians, boolean controlPointsOrientedClockwise) {

        if (controlPointsOrientedClockwise) {
            if (angleBetweenOrientedInRadians >= 0) {
                return Math.PI * 2 - angleBetweenOrientedInRadians;
            } else {
                return Math.abs(angleBetweenOrientedInRadians);
            }
        } else {
            if (angleBetweenOrientedInRadians >= 0) {
                return angleBetweenOrientedInRadians;
            } else {
                return Math.PI * 2 - Math.abs(angleBetweenOrientedInRadians);
            }
        }
    }

    /**
     * Computes the sagittas (German: Pfeilhoehen) of the arcs defined by the given arc string.
     *
     * @param arcStringNode
     *            represents an arc string
     * @param context
     *            tbd
     * @return the sagittas for the arcs defined by the arc string (in order of arcs)
     * @throws GmlGeoXException
     *             If an exception occurred while parsing the arc string control points.
     */
    public static double[] sagittas(final ANode arcStringNode, final GeoXContext context) throws GmlGeoXException {

        final Coordinate[] controlPoints = context.jtsTransformer.parseArcStringControlPoints(arcStringNode).getCoordinates();

        final double[] sagittas = new double[(controlPoints.length - 1) / 2];

        for (int i = 2; i < controlPoints.length; i = i + 2) {

            final int arcIndex = (i / 2) - 1;

            final Coordinate c1 = controlPoints[i - 2];
            final Coordinate c2 = controlPoints[i - 1];
            final Coordinate c3 = controlPoints[i];

            Circle circle;
            try {
                circle = Circle.from3Coordinates(c1, c2, c3);
            } catch (InvalidCircleException e) {
                // collinear: sagitta = 0
                sagittas[arcIndex] = 0;
                continue;
            }

            final Coordinate center = circle.center();
            final double d12 = Angle.angleBetweenOriented(c1, center, c2);
            final double d13 = Angle.angleBetweenOriented(c1, center, c3);

            final boolean controlPointsOrientedClockwise = controlPointsOrientedClockwise(d12, d13);

            final double fullAngle1to3 = fullAngle(d13, controlPointsOrientedClockwise);

            sagittas[arcIndex] = sagitta(circle.radius(), fullAngle1to3);
        }

        return sagittas;
    }

    /**
     * Compute the 'sagitta' (German: Pfeilhoehe) of an arc.
     *
     * @param circleRadius
     *            radius of the circle
     * @param fullArcAngleInRadians
     *            angle spanned by the arc, in radians
     * @return the sagitta computed for the arc
     */
    public static double sagitta(final double circleRadius, final double fullArcAngleInRadians) {
        return circleRadius * (1 - Math.cos(fullArcAngleInRadians / 2));
    }

    public static double arcLength(final double circleRadius, final double fullArcAngleInRadians) {
        return circleRadius * fullArcAngleInRadians;
    }

    private static boolean controlPointsOrientedClockwise(double d12, double d13) {

        if (d12 >= 0 && d13 >= 0) {
            if (d12 > d13) {
                // CW
                return true;
            } else {
                // CCW
                return false;
            }
        } else if (d12 >= 0 && d13 < 0) {
            // CCW
            return false;
        } else if (d12 < 0 && d13 >= 0) {
            // CW
            return true;
        } else {
            // d12 < 0 && d13 < 0
            if (d12 < d13) {
                // CCW
                return false;
            } else {
                // CW
                return true;
            }
        }
    }
}
