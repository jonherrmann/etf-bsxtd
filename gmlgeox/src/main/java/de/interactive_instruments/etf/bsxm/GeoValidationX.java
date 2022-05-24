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

import static de.interactive_instruments.etf.bsxm.validator.GeometryValidator.VALIDATE_ALL;

import org.basex.query.QueryResource;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.FElem;

import de.interactive_instruments.etf.bsxm.parser.BxNamespaceHolder;
import de.interactive_instruments.etf.bsxm.validator.GeometryValidator;

/**
 * This module supports the validation of geometries.
 *
 * <p>
 * IMPORTANT: Method {@link #init(String, String[], Integer, Double)} must be called before calling any validation
 * methods.
 *
 * <p>
 * By default validation is only performed for the following GML geometry elements: Point, Polygon, Surface, Curve,
 * LinearRing, MultiPolygon, MultiGeometry, MultiSurface, MultiCurve, Ring, and LineString. The set of GML elements to
 * validate can be modified while initialising the module (via method {@link #init(String, String[], Integer, Double)}).
 *
 * <p>
 * The following validation tests are available:
 *
 * <p>
 *
 * <table border="1">
 * <tr>
 * <th>Position</th>
 * <th>Test Name</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>1</td>
 * <td>General Validation</td>
 * <td>This test validates the given geometry using the validation functionality of both deegree and JTS. More
 * specifically:
 * <p>
 * <p>
 * <span style="text-decoration: underline;"><strong>deegree based validation:</strong></span>
 * </p>
 * <ul>
 * <li>primitive geometry (point, curve, ring, surface):
 * <ul>
 * <li>point: no specific validation</li>
 * <li>curve:
 * <ul>
 * <li>duplication of successive control points (only for linear curve segments)</li>
 * <li>segment discontinuity</li>
 * <li>self intersection (based on JTS isSimple())</li>
 * </ul>
 * </li>
 * <li>ring:
 * <ul>
 * <li>Same as curve.</li>
 * <li>In addition, test if ring is closed</li>
 * </ul>
 * </li>
 * <li>surface:
 * <ul>
 * <li>only checks PolygonPatch, individually:</li>
 * <li>applies ring validation to interior and exterior rings</li>
 * <li>checks ring orientation (ignored for GML 3.1):
 * <ul>
 * <li>must be counter-clockwise for exterior ring</li>
 * <li>must be clockwise for interior ring</li>
 * </ul>
 * </li>
 * <li>interior ring intersects exterior</li>
 * <li>interior ring outside of exterior ring</li>
 * <li>interior rings intersection</li>
 * <li>interior rings are nested</li>
 * </ul>
 * </li>
 * </ul>
 * </li>
 * <li>composite geometry: member geometries are validated individually</li>
 * <li>multi geometry: member geometries are validated individually</li>
 * </ul>
 * <p>
 * NOTE: There's some overlap with JTS validation. The following invalid situations are reported by the JTS validation:
 * </p>
 * <ul>
 * <li>curve self intersection</li>
 * <li>interior ring intersects exterior</li>
 * <li>interior ring outside of exterior ring</li>
 * <li>interior rings intersection</li>
 * <li>interior rings are nested</li>
 * <li>interior rings touch</li>
 * <li>interior ring touches exterior</li>
 * </ul>
 * <p>
 * <span style="text-decoration: underline;"><strong>JTS based validation</strong></span>:
 * </p>
 * <ul>
 * <li>Point:
 * <ul>
 * <li>invalid coordinates</li>
 * </ul>
 * </li>
 * <li>LineString:
 * <ul>
 * <li>invalid coordinates</li>
 * <li>too few points</li>
 * </ul>
 * </li>
 * <li>LinearRing:
 * <ul>
 * <li>invalid coordinates</li>
 * <li>closed ring</li>
 * <li>too few points</li>
 * <li>no self intersecting rings</li>
 * </ul>
 * </li>
 * <li>Polygon
 * <ul>
 * <li>invalid coordinates</li>
 * <li>closed ring</li>
 * <li>too few points</li>
 * <li>consistent area</li>
 * <li>no self intersecting rings</li>
 * <li>holes in shell</li>
 * <li>holes not nested</li>
 * <li>connected interiors</li>
 * </ul>
 * </li>
 * <li>MultiPoint:
 * <ul>
 * <li>invalid coordinates</li>
 * </ul>
 * </li>
 * <li>MultiLineString:
 * <ul>
 * <li>Each contained LineString is validated on its own.</li>
 * </ul>
 * </li>
 * <li>MultiPolygon:
 * <ul>
 * <li>Per polygon:
 * <ul>
 * <li>invalid coordinates</li>
 * <li>closed ring</li>
 * <li>holes in shell</li>
 * <li>holes not nested</li>
 * </ul>
 * </li>
 * <li>too few points</li>
 * <li>consistent area</li>
 * <li>no self intersecting rings</li>
 * <li>shells not nested</li>
 * <li>connected interiors</li>
 * </ul>
 * </li>
 * <li>GeometryCollection:
 * <ul>
 * <li>Each member of the collection is validated on its own.</li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * General description of checks performed by JTS:
 * </p>
 * <ul>
 * <li>invalid coordinates: x and y are neither NaN or infinite)</li>
 * <li>closed ring: tests if ring is closed; empty rings are closed by definition</li>
 * <li>too few points: tests if length of coordinate array - after repeated points have been removed - is big enough
 * (e.g. &gt;= 4 for a ring, &gt;= 2 for a line string)</li>
 * <li>no self intersecting rings: Check that there is no ring which self-intersects (except of course at its
 * endpoints); required by OGC topology rules</li>
 * <li>consistent area: Checks that the arrangement of edges in a polygonal geometry graph forms a consistent area.
 * Includes check for duplicate rings.</li>
 * <li>holes in shell: Tests that each hole is inside the polygon shell (i.e. hole rings do not cross the shell
 * ring).</li>
 * <li>holes not nested: Tests that no hole is nested inside another hole.</li>
 * <li>connected interiors: Check that the holes do not split the interior of the polygon into at least two pieces.</li>
 * <li>shells not nested: Tests that no element polygon is wholly in the interior of another element polygon (of a
 * MultiPolygon).</li>
 * </ul>
 * </td>
 * </tr>
 * <tr>
 * <td>2</td>
 * <td>Polygon Patch Connectivity</td>
 * <td>Checks that multiple polygon patches within a single surface are connected.</td>
 * </tr>
 * <tr>
 * <td>3</td>
 * <td>Repetition of Position in CurveSegments</td>
 * <td>Checks that consecutive positions within a CurveSegment are not equal.</td>
 * </tr>
 * <tr>
 * <td>4</td>
 * <td>isSimple</td>
 * <td>
 * <p>
 * Tests whether a geometry is simple, based on JTS Geometry.isSimple(). In general, the OGC Simple Features
 * specification of simplicity follows the rule: A Geometry is simple if and only if the only self-intersections are at
 * boundary points.
 * </p>
 * <p>
 * Simplicity is defined for each JTS geometry type as follows:
 * </p>
 * <ul>
 * <li>Polygonal geometries are simple if their rings are simple (i.e., their rings do not self-intersect).
 * <ul>
 * <li>Note: This does not check if different rings of the geometry intersect, meaning that isSimple cannot be used to
 * fully test for (invalid) self-intersections in polygons. The JTS validity check fully tests for self-intersections in
 * polygons, and is part of the general validation in GmlGeoX.</li>
 * </ul>
 * </li>
 * <li>Linear geometries are simple iff they do not self-intersect at points other than boundary points.</li>
 * <li>Zero-dimensional (point) geometries are simple if and only if they have no repeated points.</li>
 * <li>Empty geometries are always simple, by definition.</li>
 * </ul>
 * </td>
 * </tr>
 * </table>
 *
 * <p>
 * Some validation methods automatically apply all of these tests. However, some validation methods have a parameter
 * with which the set of tests to execute is defined. The parameter value is a string that contains a so called test
 * mask, where the character '1' at the position of a specific test (referring to column "Position" in the previous
 * table, and assuming a 1-based index) specifies that the test shall be performed. If the mask does not contain a
 * character at the position of a specific test (because the mask is empty or the length is smaller than the position),
 * then the test will be executed.
 * <p>
 * Examples:
 *
 * <ul>
 * <li>The mask '0100' indicates that only the 'Polygon Patch Connectivity' test shall be performed.
 * <li>The mask '1110' indicates that all tests except the isSimple test shall be performed .
 * </ul>
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
final public class GeoValidationX extends GeoX implements QueryResource {

    public static final byte[] GEOVAL_NS = "https://modules.etf-validator.net/geovalidationx/1".getBytes();
    public static final byte[] GEOVAL_PREFIX = "geovalx".getBytes();

    private GeometryValidator geometryValidator;
    private GeoXContext context;
    private boolean initialised = false;

    public GeoValidationX() {
        super();
    }

    /**
     * Initialises the query module. Call this method before actually performing validation routines.
     *
     * @param srsName
     *            Name of the SRS to assign to a geometry if it does not have an srsName attribute itself. Can be
     *            <code>null</code>. Setting a standard SRS can improve performance, but should only be done if all geometry
     *            elements without srsName attribute have the same SRS.
     * @param gmlGeometries
     *            Names (simple, i.e. without namespace (prefix)) of GML geometry elements to validate. Can be
     *            <code>null</code> or empty. By default, validation is performed for the following geometry types: Point,
     *            Polygon, Surface, Curve, LinearRing, MultiPoint, MultiPolygon, MultiGeometry, MultiSurface, MultiCurve,
     *            Ring, and LineString.
     * @param maxNumPoints
     *            The maximum number of points to be created when interpolating an arc. Must be a number greater than 0, but
     *            can be <code>null</code> (default setting is 1000). The lower the maximum error (set via parameter
     *            maxError, the higher the number of points needs to be. Arc interpolation will never create more than the
     *            configured maximum number of points. However, the interpolation will also never create more points than
     *            needed to achieve the maximum error. In order to achieve interpolations with a very low maximum error, the
     *            maximum number of points needs to be increased accordingly.
     * @param maxError
     *            The maximum error (e.g. 0.00000001), i.e. the maximum difference between an arc and the interpolated line
     *            string - that shall be achieved when creating new arc interpolations. Must be a number greater than 0, but
     *            can be <code>null</code> (default setting is 0.00001). The lower the error (maximum difference), the more
     *            interpolation points will be needed. However, note that a maximum for the number of such points exists. It
     *            can be set via parameter maxNumPoints.
     * @throws GmlGeoXException
     *             In case of an invalid parameter.
     */
    @Requires(Permission.NONE)
    @ContextDependent
    public void init(final String srsName, final String[] gmlGeometries, final Integer maxNumPoints,
            final Double maxError) throws GmlGeoXException {

        final BxNamespaceHolder bxNamespaceHolder = BxNamespaceHolder.init(queryContext);

        this.context = new GeoXContext(bxNamespaceHolder);

        this.geometryValidator = new GeometryValidator(context);

        if (gmlGeometries != null && gmlGeometries.length > 0) {
            this.geometryValidator.unregisterAllGmlGeometries();
            for (String g : gmlGeometries) {
                this.geometryValidator.registerGmlGeometry(g);
            }
        }

        super.init(this.context, srsName, maxNumPoints, maxError);

        initialised = true;
    }

    /**
     * Validates the given (GML geometry) node, using all available tests.
     *
     * @param node
     *            Node
     * @return a mask with the test results, encoded as characters - one at each position (1-based index) of the available
     *         tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F'
     *         indicates that the test failed. 'S' indicates that the test was skipped.
     * @throws GmlGeoXException
     *             If the module has not been properly initialised yet (via method
     *             {@link #init(String, String[], Integer, Double)}).
     */
    @Deterministic
    @Requires(Permission.NONE)
    public String validate(final ANode node) throws GmlGeoXException {

        if (!initialised)
            throw new GmlGeoXException("The module must be initialised before calling any validation function.");

        return geometryValidator.validateWithSimplifiedResults(node, VALIDATE_ALL);
    }

    /**
     * Validates the given (GML geometry) node.
     *
     * @param node
     *            the GML geometry to validate
     * @param testMask
     *            test mask
     * @return a mask with the test results, encoded as characters - one at each position (1-based index) of the available
     *         tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F'
     *         indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVFF' shows
     *         that the first test was skipped, while the second test passed and the third and fourth failed.
     * @throws GmlGeoXException
     *             If the module has not been properly initialised yet (via method
     *             {@link #init(String, String[], Integer, Double)}).
     */
    @Deterministic
    @Requires(Permission.NONE)
    public String validate(final ANode node, final String testMask) throws GmlGeoXException {

        if (!initialised)
            throw new GmlGeoXException("The module must be initialised before calling any validation function.");

        return geometryValidator.validateWithSimplifiedResults(node, testMask.getBytes());
    }

    /**
     * Validates the given (GML geometry) node, using all available tests.
     *
     * @param node
     *            Node
     * @return a DOM element with the validation result (for details about its structure, see the description of the result
     *         in method {@link #validateAndReport(ANode, String)})
     * @throws GmlGeoXException
     *             If the module has not been properly initialised yet (via method
     *             {@link #init(String, String[], Integer, Double)}).
     */
    @Deterministic
    @Requires(Permission.NONE)
    public FElem validateAndReport(final ANode node) throws GmlGeoXException {

        if (!initialised)
            throw new GmlGeoXException("The module must be initialised before calling any validation function.");

        return geometryValidator.validate(node, VALIDATE_ALL);
    }

    /**
     * Validates the given (GML geometry) node. The validation tasks to perform are specified via the given mask.
     *
     * @param node
     *            The GML geometry element to validate.
     * @param testMask
     *            Defines which tests shall be executed.
     * @return a DOM element, with the validation result and validation message (providing further details about any
     *         errors). The validation result is encoded as a sequence of characters - one at each position (1-based index)
     *         of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that
     *         test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVFF'
     *         shows that the first test was skipped, while the second test passed and the third and fourth failed.
     *
     *         <pre>
     * {@code
     *         <geovalx:ValidationResult xmlns:geovalx=
    "https://modules.etf-validator.net/geovalidationx/1">
     *           <geovalx:valid>false</geovalx:valid>
     *           <geovalx:result>VFV</geovalx:result>
     *           <geovalx:errors>
     *             <etf:message
     *               xmlns:etf="http://www.interactive-instruments.de/etf/2.0"
     *               ref="TR.gmlgeox.validation.geometry.jts.5">
     *               <etf:argument token=
    "original">Invalid polygon. Two rings of the polygonal geometry intersect.</etf:argument>
     *               <etf:argument token="ID">DETHL56P0000F1TJ</etf:argument>
     *               <etf:argument token="context">Surface</etf:argument>
     *               <etf:argument token=
    "coordinates">666424.2393405803,5614560.422015165</etf:argument>
     *             </etf:message>
     *           </geovalx:errors>
     *         </geovalx:ValidationResult>
     * }
     * </pre>
     *
     * Where:
     * <ul>
     * <li>geovalx:valid - contains the boolean value indicating if the object passed all tests (defined by the testMask).
     * <li>geovalx:result - contains a string that is a mask with the test results, encoded as characters - one at each
     * position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid
     * according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped.
     * <li>geovalx:message (one for each message produced during validation) contains:
     * <ul>
     * <li>an XML attribute 'type' that indicates the severity level of the message ('FATAL', 'ERROR', 'WARNING', or
     * 'NOTICE')
     * <li>the actual validation message as text content
     * </ul>
     * </ul>
     * @throws GmlGeoXException
     *             If the module has not been properly initialised yet (via method
     *             {@link #init(String, String[], Integer, Double)}).
     *
     */
    @Deterministic
    @Requires(Permission.NONE)
    public FElem validateAndReport(final ANode node, final String testMask) throws GmlGeoXException {

        if (!initialised)
            throw new GmlGeoXException("The module must be initialised before calling any validation function.");

        return geometryValidator.validate(node, testMask.getBytes());
    }

    /**
     * Check if a given geometry node is valid, using all available tests.
     *
     * @param geometryNode
     *            the geometry element
     * @return <code>true</code> if the given node represents a valid geometry, else <code>false</code>.
     * @throws GmlGeoXException
     *             If the module has not been properly initialised yet (via method
     *             {@link #init(String, String[], Integer, Double)}).
     */
    @Deterministic
    @Requires(Permission.NONE)
    public boolean isValid(final ANode geometryNode) throws GmlGeoXException {

        if (!initialised)
            throw new GmlGeoXException("The module must be initialised before calling any validation function.");

        final String validationResult = validate(geometryNode);
        return validationResult.toLowerCase().indexOf('f') <= -1;
    }

    @Override
    public void close() {
        this.initialised = false;
        this.queryContext = null;
        this.staticContext = null;
        this.geometryValidator = null;
        this.context = null;
    }
}
