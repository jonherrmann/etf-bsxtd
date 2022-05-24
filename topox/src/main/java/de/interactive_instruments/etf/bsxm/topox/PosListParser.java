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
 * An interface for parsing the direct positions of geometric objects
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface PosListParser {

    /**
     * Parse the direct positions of a byte array.
     *
     * @param byteSequence
     *            byte array containing direct positions
     * @param location
     *            location information of the direct positions, e.g. an ID
     * @param geoType
     *            Geometry type
     */
    void parseDirectPositions(final byte[] byteSequence, final long location, final int geoType);

    /**
     * Parse the direct positions of a byte array.
     *
     * @param sequence
     *            char sequence containing direct positions
     * @param location
     *            location information of the direct positions, e.g. an ID
     * @param geoType
     *            Geometry type
     */
    void parseDirectPositions(final CharSequence sequence, final long location, final int geoType);

    /**
     * Parse the direct positions of a byte array. The second argument overrides a previous dimension() call temporarily.
     *
     * @param byteSequence
     *            byte array containing direct positions
     * @threeDCoordinates set to true if the dimension is 3, false for 2D coordinates
     * @param location
     *            location information of the direct positions, e.g. an ID
     * @param geoType
     *            Geometry type
     */
    void parseDirectPositions(final byte[] byteSequence, final boolean threeDCoordinates, final long location,
            final int geoType);

    /**
     * Parse the direct positions of a byte array. The second argument overrides a previous dimension() call temporarily.
     *
     * @param sequence
     *            char sequence containing direct positions
     * @threeDCoordinates set to true if the dimension is 3, false for 2D coordinates
     * @param location
     *            location information of the direct positions, e.g. an ID
     * @param geoType
     *            Geometry type
     */
    void parseDirectPositions(final CharSequence sequence, final boolean threeDCoordinates, final long location,
            final int geoType);

    /**
     * Set the dimension of the next parsed coordinates.
     *
     * @param threeDCoordinates
     *            set to true if the dimension is 3, false for 2D coordinates
     */
    void dimension(final boolean threeDCoordinates);

    /**
     * Parse the next geometric object.
     */
    void nextGeometricObject();

    /**
     * Parse the next interior geometry.
     */
    void nextInterior();
}
