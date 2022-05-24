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

import de.interactive_instruments.etf.bsxm.geometry.IIGeometryFactory;
import de.interactive_instruments.etf.bsxm.parser.BxNamespaceHolder;

/**
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class GeoXContext {

    public final com.vividsolutions.jts.geom.GeometryFactory jtsFactory = new com.vividsolutions.jts.geom.GeometryFactory();
    private IIGeometryFactory geometryFactory = new IIGeometryFactory();
    public final SrsLookup srsLookup = new SrsLookup();
    public final JtsTransformer jtsTransformer;
    public final DeegreeTransformer deegreeTransformer;
    public final BxNamespaceHolder bxNamespaceHolder;

    public GeoXContext(BxNamespaceHolder bxNamespaceHolder) {
        this.bxNamespaceHolder = bxNamespaceHolder;
        this.deegreeTransformer = new DeegreeTransformer(this.geometryFactory, bxNamespaceHolder, this.srsLookup);
        this.jtsTransformer = new JtsTransformer(this.deegreeTransformer, this.jtsFactory, this.srsLookup);
    }

    public IIGeometryFactory geometryFactory() {
        return this.geometryFactory;
    }

    public void setGeometryFactory(IIGeometryFactory factory) {
        this.geometryFactory = factory;
    }
}
