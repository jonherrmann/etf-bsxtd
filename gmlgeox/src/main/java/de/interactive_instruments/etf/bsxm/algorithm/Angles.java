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

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class Angles {

    private Angles() {}

    /**
     * Computes the directional change between two line segments (defined by three consecutive coordinates), expressed as an
     * angular value in degree.
     *
     * @param coord1
     *            first coordinate
     * @param coord2
     *            second coordinate
     * @param coord3
     *            third coordinate
     * @return the directional change between the two line segments
     */
    public static double directionChangeInDegree(Coordinate coord1, Coordinate coord2, Coordinate coord3) {

        final double angleVector1to2 = Angle.angle(coord1, coord2);
        final double angleVector2to3 = Angle.angle(coord2, coord3);

        final double diff_rad = Angle.diff(angleVector1to2, angleVector2to3);

        return Angle.toDegrees(diff_rad);
    }

}
