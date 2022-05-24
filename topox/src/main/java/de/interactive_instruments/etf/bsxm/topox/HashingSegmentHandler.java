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
package de.interactive_instruments.etf.bsxm.topox;

/**
 * Segment handler
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface HashingSegmentHandler {

    void coordinate2d(final double x, final double y, final long hash, final long location, final int type);

    default void coordinates2d(final double[] coordinates, final long hashesAndLocations[], final int type) {
        for (int i = 0; i < coordinates.length; i += 2) {
            coordinate2d(coordinates[i], coordinates[i + 1], hashesAndLocations[i], hashesAndLocations[i + 1], type);
        }
    }

    void nextGeometricObject();

    void nextInterior();
}
