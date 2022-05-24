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

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.basex.query.QueryModule;
import org.deegree.cs.CRSCodeType;
import org.deegree.cs.persistence.CRSManager;

import de.interactive_instruments.IFile;
import de.interactive_instruments.IoUtils;
import de.interactive_instruments.properties.PropertyUtils;

/**
 * Abstract class for query modules that need to parse GML geometries, and require correctly configured SRS information
 * to do so.
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public abstract class GeoX extends QueryModule {

    public static final String ETF_GMLGEOX_SRSCONFIG_DIR = "etf.testdrivers.bsx.gmlgeox.srsconfig.dir";

    public GeoX() {}

    protected final void init(final GeoXContext context, final String srsName, final Integer maxNumPoints,
            final Double maxError) throws GmlGeoXException {

        // ensure that the CRSManager has been initialised
        initCRSManager();

        context.srsLookup.setStandardSRS(srsName, CRSManager.get("default"));

        if (maxNumPoints != null) {
            if (maxNumPoints <= 0) {
                throw new GmlGeoXException(
                        "Value of parameter maxNumPoints must be greater than zero. Was: " + maxNumPoints + ".");
            } else {
                context.geometryFactory().setMaxNumPoints(maxNumPoints);
            }
        }
        if (maxError != null) {
            if (maxError <= 0) {
                throw new GmlGeoXException(
                        "Value of parameter maxError must be greater than zero. Was: " + maxError + ".");
            } else {
                context.geometryFactory().setMaxError(maxError);
            }
        }
    }

    protected final void initCRSManager() {

        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            synchronized (GeoX.class) {
                if (CRSManager.get("default") == null || CRSManager.get("default")
                        .getCRSByCode(CRSCodeType.valueOf("urn:adv:crs:ETRS89_UTM32")) == null) {
                    loadGmlGeoXSrsConfiguration();
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    private void loadGmlGeoXSrsConfiguration() {

        final String srsConfigDirPath = PropertyUtils.getenvOrProperty(ETF_GMLGEOX_SRSCONFIG_DIR, null);
        final CRSManager crsMgr = new CRSManager();
        /*
         * If the configuration for EPSG 5555 can be accessed, the CRSManger is already configured by the test driver.
         */
        if (srsConfigDirPath != null) {
            final IFile srsConfigDirectory = new IFile(srsConfigDirPath, ETF_GMLGEOX_SRSCONFIG_DIR);
            try {
                srsConfigDirectory.expectDirIsReadable();
                crsMgr.init(srsConfigDirectory);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Could not load SRS configuration files from directory: " + e.getMessage());
            }
        } else {
            try {
                /*
                 * We use the same folder each time an instance of GmlGeoX is created. The configuration files will not be
                 * deleted upon exit. That shouldn't be a problem since we always use the same folder.
                 */
                final String tempDirPath = System.getProperty("java.io.tmpdir");
                final File tempDir = new File(tempDirPath, "gmlGeoX2SrsConfig");

                if (tempDir.exists()) {
                    FileUtils.deleteQuietly(tempDir);
                }
                tempDir.mkdirs();

                IoUtils.copyResourceToFile(this, "/srsconfig/default.xml", new IFile(tempDir, "default.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/ntv2/beta2007.gsb",
                        new IFile(tempDir, "deegree/d3/config/ntv2/beta2007.gsb"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/parser-files.xml",
                        new IFile(tempDir, "deegree/d3/parser-files.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/crs-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/crs-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/datum-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/datum-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/ellipsoid-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/ellipsoid-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/pm-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/pm-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/projection-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/projection-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/transformation-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/transformation-definitions.xml"));

                crsMgr.init(tempDir);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Exception occurred while extracting the SRS configuration files provided by GmlGeoX to a temporary "
                                + "directory and loading them from there. Exception message is: " + e.getMessage(),
                        e);
            }
        }
    }
}
