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
package de.interactive_instruments.etf.bsxm.geometry;

import java.util.List;

import javax.vecmath.Point3d;

import org.deegree.cs.CRSCodeType;
import org.deegree.cs.components.IAxis;
import org.deegree.cs.components.IDatum;
import org.deegree.cs.components.IGeodeticDatum;
import org.deegree.cs.components.IUnit;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.transformations.Transformation;
import org.jetbrains.annotations.NotNull;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public interface CRS extends ICRS {
    long internalCode();

    class CrsWithInternalCode implements CRS {
        private final ICRS crs;
        private final long internalCode;

        public CrsWithInternalCode(final @NotNull ICRS crs) {
            this.crs = crs;
            long code = 0;
            try {
                code = Long.parseLong(this.crs.getCode().getCode());
            } catch (NumberFormatException ignore) {
                final CRSCodeType[] codes = this.crs.getCodes();
                if (codes != null && codes.length > 1) {
                    for (final CRSCodeType crsCodeType : codes) {
                        try {
                            code = Long.parseLong(crsCodeType.getCode());
                        } catch (NumberFormatException ignore2) {
                            continue;
                        }
                        break;
                    }
                }
            }
            this.internalCode = code;
        }

        @Override
        public long internalCode() {
            return this.internalCode;
        }

        @Override
        public int getDimension() {
            return crs.getDimension();
        }

        @Override
        public org.deegree.cs.coordinatesystems.CRS.CRSType getType() {
            return crs.getType();
        }

        @Override
        public IAxis[] getAxis() {
            return crs.getAxis();
        }

        @Override
        public IGeodeticDatum getGeodeticDatum() {
            return crs.getGeodeticDatum();
        }

        @Override
        public IDatum getDatum() {
            return crs.getDatum();
        }

        @Override
        public IUnit[] getUnits() {
            return crs.getUnits();
        }

        @Override
        public boolean hasDirectTransformation(final ICRS icrs) {
            return crs.hasDirectTransformation(icrs);
        }

        @Override
        public Transformation getDirectTransformation(final ICRS icrs) {
            return crs.getDirectTransformation(icrs);
        }

        @Override
        public List<Transformation> getTransformations() {
            return crs.getTransformations();
        }

        @Override
        public int getEasting() {
            return crs.getEasting();
        }

        @Override
        public int getNorthing() {
            return crs.getNorthing();
        }

        @Override
        public double[] getValidDomain() {
            return crs.getValidDomain();
        }

        @Override
        public Point3d convertToAxis(final Point3d point3d, final IUnit[] iUnits, final boolean b) {
            return crs.convertToAxis(point3d, iUnits, b);
        }

        @Override
        public String getAlias() {
            return crs.getAlias();
        }

        @Override
        public boolean equalsWithFlippedAxis(final Object o) {
            return crs.equalsWithFlippedAxis(o);
        }

        @Override
        public String getAreaOfUse() {
            return crs.getAreaOfUse();
        }

        @Override
        public String getDescription() {
            return crs.getDescription();
        }

        @Override
        public CRSCodeType getCode() {
            return crs.getCode();
        }

        @Override
        public String getName() {
            return crs.getName();
        }

        @Override
        public String getVersion() {
            return crs.getVersion();
        }

        @Override
        public String getCodeAndName() {
            return crs.getCodeAndName();
        }

        @Override
        public String[] getAreasOfUse() {
            return crs.getAreasOfUse();
        }

        @Override
        public String[] getDescriptions() {
            return crs.getDescriptions();
        }

        @Override
        public CRSCodeType[] getCodes() {
            return crs.getCodes();
        }

        @Override
        public String[] getOrignalCodeStrings() {
            return crs.getOrignalCodeStrings();
        }

        @Override
        public String[] getNames() {
            return crs.getNames();
        }

        @Override
        public String[] getVersions() {
            return crs.getVersions();
        }

        @Override
        public boolean hasCode(final CRSCodeType crsCodeType) {
            return crs.hasCode(crsCodeType);
        }

        @Override
        public boolean hasIdOrName(final String s, final boolean b, final boolean b1) {
            return crs.hasIdOrName(s, b, b1);
        }

        @Override
        public boolean hasId(final String s, final boolean b, final boolean b1) {
            return crs.hasId(s, b, b1);
        }

        @Override
        public double[] getAreaOfUseBBox() {
            return crs.getAreaOfUseBBox();
        }

        @Override
        public void setDefaultId(final CRSCodeType crsCodeType, final boolean b) {
            crs.setDefaultId(crsCodeType, b);
        }

        @Override
        public void setDefaultAreaOfUse(final double[] doubles) {
            crs.setDefaultAreaOfUse(doubles);
        }

        @Override
        public void addAreaOfUse(final String s) {
            crs.addAreaOfUse(s);
        }

        @Override
        public void addName(final String s) {
            crs.addName(s);
        }

        @Override
        public void setDefaultName(final String s, final boolean b) {
            crs.setDefaultName(s, b);
        }

        @Override
        public void setDefaultDescription(final String s, final boolean b) {
            crs.setDefaultDescription(s, b);
        }

        @Override
        public void setDefaultVersion(final String s, final boolean b) {
            crs.setDefaultVersion(s, b);
        }

        @Override
        public String getId() {
            return crs.getId();
        }
    }
}
