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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.deegree.cs.persistence.CRSManager;

import de.interactive_instruments.etf.bsxm.geometry.IIGeometryFactory;
import de.interactive_instruments.etf.bsxm.index.GeometryCache;
import de.interactive_instruments.etf.bsxm.index.SpatialIndexRegister;
import de.interactive_instruments.etf.bsxm.node.DBNodeRefFactory;
import de.interactive_instruments.etf.bsxm.node.DBNodeRefLookup;
import de.interactive_instruments.etf.bsxm.parser.BxNamespaceHolder;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class GmlGeoXContext extends GeoXContext {

    public final DBNodeRefLookup dbNodeRefLookup;
    public final DBNodeRefFactory dbNodeRefFactory;
    private GeometryCache geometryCache = new GeometryCache();
    private SpatialIndexRegister indexRegister = new SpatialIndexRegister();

    public GmlGeoXContext(BxNamespaceHolder bxNamespaceHolder, DBNodeRefFactory dbNodeRefFactory,
            DBNodeRefLookup dbNodeRefLookup) {

        super(bxNamespaceHolder);

        this.dbNodeRefFactory = dbNodeRefFactory;
        this.dbNodeRefLookup = dbNodeRefLookup;
    }

    void write(final ObjectOutput out) throws IOException {
        out.writeObject(this.geometryFactory());
        out.writeObject(this.geometryCache);
        out.writeObject(this.indexRegister);
        final String standardSRS = this.srsLookup.getStandardSRS();
        out.writeUTF(standardSRS != null ? standardSRS : "");
    }

    void read(final ObjectInput in) throws IOException, ClassNotFoundException {
        setGeometryFactory((IIGeometryFactory) in.readObject());
        this.geometryCache = (GeometryCache) in.readObject();
        this.indexRegister = (SpatialIndexRegister) in.readObject();
        /*
         * NOTE: GmlGeoX.readExternal(..) ensures that the CRS manager is set up correctly
         */
        this.srsLookup.setStandardSRS(in.readUTF(), CRSManager.get("default"));
    }

    public GeometryCache geometryCache() {
        return this.geometryCache;
    }

    public SpatialIndexRegister indexRegister() {
        return this.indexRegister;
    }
}
