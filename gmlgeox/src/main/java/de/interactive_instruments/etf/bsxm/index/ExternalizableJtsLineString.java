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
package de.interactive_instruments.etf.bsxm.index;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;

/**
 * ExternalizableJtsLineString
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class ExternalizableJtsLineString implements ExternalizableJtsGeometry {

    private double[] coordinates;

    public ExternalizableJtsLineString() {}

    ExternalizableJtsLineString(final LineString lineString) {
        coordinates = ExternalizableJtsGeometry.simplify(lineString.getCoordinateSequence());
    }

    @Override
    public Geometry toJtsGeometry(final GeometryFactory factory) {
        return new LineString(new CoordinateArraySequence(ExternalizableJtsGeometry.toCoordinateArray(this.coordinates)),
                factory);
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeInt(coordinates.length);
        for (int i = 0; i < coordinates.length; i++) {
            out.writeDouble(coordinates[i]);
        }
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException {
        coordinates = new double[in.readInt()];
        for (int i = 0; i < coordinates.length; i++) {
            coordinates[i] = in.readDouble();
        }
    }
}
