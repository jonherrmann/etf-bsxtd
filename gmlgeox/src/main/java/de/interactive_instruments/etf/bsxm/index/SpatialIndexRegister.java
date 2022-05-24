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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Point;
import com.github.davidmoten.rtree.geometry.Rectangle;
import com.github.davidmoten.rtree.internal.EntryDefault;
import com.vividsolutions.jts.geom.Envelope;

import org.basex.query.QueryModule.Deterministic;
import org.basex.query.QueryModule.Permission;
import org.basex.query.QueryModule.Requires;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;
import org.deegree.commons.xml.XMLParsingException;
import org.deegree.geometry.Geometry;
import org.jetbrains.annotations.NotNull;

import de.interactive_instruments.etf.bsxm.GmlGeoX;
import de.interactive_instruments.etf.bsxm.GmlGeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXException;
import de.interactive_instruments.etf.bsxm.JtsTransformer;
import de.interactive_instruments.etf.bsxm.algorithm.GeometryPointsAnalysis;
import de.interactive_instruments.etf.bsxm.node.DBNodeRef;
import de.interactive_instruments.etf.bsxm.node.DBNodeRefLookup;
import de.interactive_instruments.etf.bsxm.node.ExternalizableDBNodeRefMap;

/**
 * Builds and maintains spatial indexes.
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
public class SpatialIndexRegister implements Externalizable {

    public static String DEFAULT_SPATIAL_INDEX = "";

    private Map<String, RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> rtreeByIndexName = new HashMap<>();
    private final Map<String, List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>>> geomIndexEntriesByIndexName = new HashMap<>();

    /**
     * Index a geometry
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @param entry
     *            the entry referencing the BaseX node (typically of a feature)
     * @param geometry
     *            the geometry to index
     */
    public void index(@NotNull final String indexName, final DBNodeRef entry,
            final com.github.davidmoten.rtree.geometry.Geometry geometry) {
        RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> rtree = rtreeByIndexName.get(indexName);
        if (rtree == null) {
            rtree = RTree.star().create();
        }
        rtree = rtree.add(entry, geometry);
        rtreeByIndexName.put(indexName, rtree);
    }

    /**
     * Report current size of the named spatial index
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @return size of the spatial index; can be 0 if no index with given name was found
     */
    public int indexSize(@NotNull final String indexName) {
        if (rtreeByIndexName.containsKey(indexName)) {
            return rtreeByIndexName.get(indexName).size();
        } else {
            return 0;
        }
    }

    /**
     * return all entries in the named spatial index
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @param lookup
     *            tbd
     * @return iterator over all entries; can be <code>null</code> if no index with given name was found
     */
    public List<DBNode> getAll(@NotNull final String indexName, final DBNodeRefLookup lookup) {
        if (rtreeByIndexName.containsKey(indexName)) {
            return lookup
                    .collect(rtreeByIndexName.get(indexName).entries().map(com.github.davidmoten.rtree.Entry::value));
        } else {
            return null;
        }
    }

    /**
     * Return all entries in the named spatial index whose bounding box intersects with the given bounding box
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @param bbox
     *            the bounding box / rectangle
     * @param lookup
     *            tbd
     * @return iterator over all detected entries; can be <code>null</code> if no index with given name was found
     */
    @NotNull
    public List<DBNode> search(@NotNull final String indexName, final Rectangle bbox, final DBNodeRefLookup lookup) {
        if (rtreeByIndexName.containsKey(indexName)) {
            return lookup.collect(
                    rtreeByIndexName.get(indexName).search(bbox).map(com.github.davidmoten.rtree.Entry::value));
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Returns the nearest k entries (k=maxCount) to the given point where the entries are strictly less than a given
     * maximum distance from the point.
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @param point
     *            the position at which nearest entries are searched.
     * @param maxDistance
     *            as the maximum distance to search nearest entries.
     * @param maxCount
     *            tbd
     * @return iterator over all detected entries; can be empty - if no index with given name was found, or no entries
     *         within the maximum distance - but not <code>null</code>
     */
    public List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> nearest(
            final String indexName, final Point point, final double maxDistance, final int maxCount) {

        if (rtreeByIndexName.containsKey(indexName)) {

            return rtreeByIndexName.get(indexName).nearest(point, maxDistance, maxCount).toList().toBlocking().single();

        } else {

            return new ArrayList<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>>();
        }
    }

    /**
     * Create the named spatial index by bulk loading, using the STR method. Before the index can be built, entries must be
     * added by calling {@link #prepareSpatialIndex(String, DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry)}.
     *
     * <p>
     * According to https://github.com/ambling/rtree-benchmark, creating an R*-tree using bulk loading is faster than doing
     * so without bulk loading. Furthermore, according to https://en.wikipedia.org/wiki/R-tree, an STR bulk loaded R*-tree
     * is a "very efficient tree".
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @throws GmlGeoXException
     *             If the index has already been built.
     */
    public void buildIndexUsingBulkLoading(@NotNull final String indexName) throws GmlGeoXException {
        if (rtreeByIndexName.containsKey(indexName)) {
            throw new GmlGeoXException("Spatial index '" + indexName + "' has already been built.");
        } else if (geomIndexEntriesByIndexName.containsKey(indexName)) {
            RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> rtree = RTree.star()
                    .create(geomIndexEntriesByIndexName.get(indexName));
            rtreeByIndexName.put(indexName, rtree);
            geomIndexEntriesByIndexName.remove(indexName);
        }
        // Else: No entries for that index have been added using
        // prepareSpatialIndex(...) -> ignore
    }

    /**
     * Returns the minimum bounding rectangle of a given rtree by index name.
     *
     * @param indexName
     *            as String of the indexed rtree
     * @return minimum bounding rectangle
     */
    public Rectangle getIndexMbr(@NotNull final String indexName) {
        return rtreeByIndexName.get(indexName).mbr().get();
    }

    /**
     * Prepares spatial indexing by caching an entry for the named spatial index.
     *
     * <p>
     * With an explicit call to {@link #buildIndexUsingBulkLoading(String)}, that index is built.
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @param nodeEntry
     *            represents the node of the element to be indexed
     * @param geometry
     *            the geometry to use in the index
     */
    public void prepareSpatialIndex(@NotNull final String indexName, final DBNodeRef nodeEntry,
            final com.github.davidmoten.rtree.geometry.Geometry geometry) {
        final List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> geomIndexEntries;
        if (geomIndexEntriesByIndexName.containsKey(indexName)) {
            geomIndexEntries = geomIndexEntriesByIndexName.get(indexName);
        } else {
            geomIndexEntries = new ArrayList<>();
            geomIndexEntriesByIndexName.put(indexName, geomIndexEntries);
        }
        geomIndexEntries.add(new EntryDefault<>(nodeEntry, geometry));
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        final ExternalizableDBNodeRefMap dbNodeRefMap = new ExternalizableDBNodeRefMap();
        // Rtrees
        {
            out.writeInt(rtreeByIndexName.size());
            for (final Entry<String, RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> rtreeIndex : rtreeByIndexName
                    .entrySet()) {
                out.writeUTF(rtreeIndex.getKey());
                final List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> rtreeEntrySet = rtreeIndex
                        .getValue().entries().toList().toBlocking().single();
                final int[] rtreeDBNodeRefPositions = dbNodeRefMap
                        .addAndGetRefPositions(rtreeEntrySet.stream().map(com.github.davidmoten.rtree.Entry::value)
                                .collect(Collectors.toCollection(() -> new ArrayList<>(rtreeEntrySet.size()))));
                out.writeInt(rtreeDBNodeRefPositions.length);
                int posRtree = 0;
                for (final com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> entry : rtreeEntrySet) {
                    out.writeInt(rtreeDBNodeRefPositions[posRtree++]);
                    out.writeObject(ExternalizableRtreeGeometry.create(entry.geometry()));
                }
            }
        }
        out.writeObject(dbNodeRefMap);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {

        // Prepare Rtrees
        final String[] indexnames = new String[in.readInt()];
        final List<int[]> rtreeDBNodeRefPositions = new ArrayList<>(indexnames.length);
        final List<com.github.davidmoten.rtree.geometry.Geometry[]> rtreeGeometries = new ArrayList<>(
                indexnames.length);
        for (int i = 0; i < indexnames.length; i++) {
            indexnames[i] = in.readUTF();
            final int[] positions = new int[in.readInt()];
            final com.github.davidmoten.rtree.geometry.Geometry[] rtreeGeos = new com.github.davidmoten.rtree.geometry.Geometry[positions.length];
            for (int p = 0; p < positions.length; p++) {
                positions[p] = in.readInt();
                rtreeGeos[p] = ((ExternalizableRtreeGeometry) in.readObject()).toRtreeGeometry();
            }
            rtreeDBNodeRefPositions.add(positions);
            rtreeGeometries.add(rtreeGeos);
        }

        // Restore DBNodeRefs
        final ExternalizableDBNodeRefMap dbNodeRefMap = ((ExternalizableDBNodeRefMap) in.readObject());

        // Restore Rtrees
        {
            this.rtreeByIndexName = new HashMap<>(indexnames.length);
            for (int i = 0; i < indexnames.length; i++) {

                final DBNodeRef[] rtreeDBNodeRefs = dbNodeRefMap.getByRefPositions(rtreeDBNodeRefPositions.get(i));
                rtreeDBNodeRefPositions.set(i, null);
                final com.github.davidmoten.rtree.geometry.Geometry[] currentRtreeGeometries = rtreeGeometries.get(i);
                final List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> entries = new ArrayList<>(
                        rtreeDBNodeRefs.length);
                for (int p = 0; p < rtreeDBNodeRefs.length; p++) {
                    entries.add(new EntryDefault<>(rtreeDBNodeRefs[p], currentRtreeGeometries[p]));
                }
                rtreeGeometries.set(i, null);
                this.rtreeByIndexName.put(indexnames[i], RTree.star().create(entries));
            }
        }
    }

    /**
     * Returns an index entry for the spatial index.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param node
     *            represents the node of the feature to be indexed
     * @param geometry
     *            represents the GML geometry to index
     * @param context
     *            tbd
     * @return rtree entry that can be used by the spatial index
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> spatialIndexEntry(
            final ANode node, final ANode geometry, final GmlGeoXContext context) throws GmlGeoXException {

        final com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> entry;
        if (node instanceof DBNode && geometry instanceof DBNode) {
            final com.vividsolutions.jts.geom.Geometry _geom = context.geometryCache().getOrCacheGeometry(geometry,
                    context);
            final Envelope env = _geom.getEnvelopeInternal();
            if (!env.isNull()) {
                // cache the index entry
                final DBNodeRef geometryNodeEntry;
                if (_geom.getUserData() != null) {
                    geometryNodeEntry = (DBNodeRef) _geom.getUserData();
                } else {
                    geometryNodeEntry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) node);
                }
                final com.github.davidmoten.rtree.geometry.Geometry treeGeom;
                if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {
                    treeGeom = Geometries.point(env.getMinX(), env.getMinY());
                } else {
                    treeGeom = rTreeRectangle(env);
                }
                entry = new EntryDefault<>(geometryNodeEntry, treeGeom);
            } else {
                throw new GmlGeoXException("Envelope determined for the given geometry node is empty "
                        + "(ensure that the given node is a geometry node that represents a non-empty geometry). "
                        + "Cannot create a spatial index entry based upon an empty envelope.");
            }
        } else {
            throw new GmlGeoXException("Parameter without type DBNode.");
        }
        return entry;
    }

    /**
     * Control points of the given geometry nodes are parsed and returned as an array of index entries. The behavior for
     * retrieving control points can be influenced by the parameter controlPointSearchBehavior.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param node
     *            represents the node of the feature to be indexed
     * @param geometry
     *            represents the GML geometry to index
     * @param controlPointSearchBehavior
     *            codes to influence to resulting set of control points (multiple values separated by commas):
     *            'IGNORE_ARC_MID_POINT' - to ignore the middle point of an arc (in Arc and ArcString)
     * @param context
     *            tbd
     * @return rtree entries that can be used by the spatial index
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public static com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>[] spatialPointIndexEntries(
            final ANode node, final ANode geometry, final String controlPointSearchBehavior,
            final GmlGeoXContext context) throws GmlGeoXException {

        final List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> entries = new ArrayList<>();
        if (node instanceof DBNode && geometry instanceof DBNode) {

            final List<com.vividsolutions.jts.geom.Point> points = GeometryPointsAnalysis.getControlPoints(geometry,
                    controlPointSearchBehavior, context);
            if (!points.isEmpty()) {
                final DBNodeRef geometryNodeEntry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) node);

                for (com.vividsolutions.jts.geom.Point p : points) {
                    final com.github.davidmoten.rtree.geometry.Point treeGeom;
                    treeGeom = Geometries.point(p.getX(), p.getY());

                    entries.add(new EntryDefault<>(geometryNodeEntry, treeGeom));
                }
            }
        }
        return entries.toArray(new com.github.davidmoten.rtree.Entry[0]);
    }

    /**
     * Builds and returns the spatial index for a sequence of index entries.
     *
     * @param entriesIn
     *            represents the entries to be indexed
     * @return spatial index as an RTree Object
     */
    public static RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> spatialIndex(final Object entriesIn) {

        final Collection<?> entries = GmlGeoX.toObjectCollection(entriesIn);

        final List<com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>> entriesList = new ArrayList<>();

        for (Object entry : entries) {
            entriesList.add(
                    (com.github.davidmoten.rtree.Entry<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry>) entry);
        }

        return RTree.star().create(entriesList);
    }

    /**
     * Searches the default spatial r-tree index for items whose minimum bounding box intersects with the rectangle defined
     * by the given coordinates.
     *
     * @param minx
     *            represents the minimum value on the first coordinate axis; a number
     * @param miny
     *            represents the minimum value on the second coordinate axis; a number
     * @param maxx
     *            represents the maximum value on the first coordinate axis; a number
     * @param maxy
     *            represents the maximum value on the second coordinate axis; a number
     * @param context
     *            tbd
     * @return the node set of all items in the envelope
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public DBNode[] search(final Object minx, final Object miny, final Object maxx, final Object maxy,
            final GmlGeoXContext context) throws GmlGeoXException {
        return search(DEFAULT_SPATIAL_INDEX, minx, miny, maxx, maxy, context);
    }

    /**
     * Searches the named spatial r-tree index for items whose minimum bounding box intersects with the rectangle defined by
     * the given coordinates.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param minx
     *            represents the minimum value on the first coordinate axis; a number
     * @param miny
     *            represents the minimum value on the second coordinate axis; a number
     * @param maxx
     *            represents the maximum value on the first coordinate axis; a number
     * @param maxy
     *            represents the maximum value on the second coordinate axis; a number
     * @param context
     *            tbd
     * @return the node set of all items in the envelope
     */
    public DBNode[] search(final String indexName, final Object minx, final Object miny, final Object maxx,
            final Object maxy, final GmlGeoXContext context) {
        return performSearch(indexName, toDoubleOrZero(minx), toDoubleOrZero(miny), toDoubleOrZero(maxx),
                toDoubleOrZero(maxy), context);
    }

    private static double toDoubleOrZero(final Object dbl) {
        if (dbl instanceof Number) {
            return ((Number) dbl).doubleValue();
        }
        return 0.0;
    }

    @NotNull
    private DBNode[] performSearch(final String indexName, final double x1, final double y1, final double x2,
            final double y2, final GmlGeoXContext context) {
        final List<DBNode> nodelist = search(indexName, Geometries.rectangle(x1, y1, x2, y2), context.dbNodeRefLookup);
        return nodelist.toArray(new DBNode[0]);
    }

    /**
     * Searches the default spatial r-tree index for items whose minimum bounding box intersects with the the minimum
     * bounding box of the given geometry node.
     *
     * @param geometryNode
     *            the geometry element
     * @param context
     *            tbd
     * @return the node set of all items in the envelope
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public DBNode[] search(final ANode geometryNode, final GmlGeoXContext context) throws GmlGeoXException {
        return search(DEFAULT_SPATIAL_INDEX, geometryNode, context);
    }

    /**
     * Searches the named spatial r-tree index for items whose minimum bounding box intersects with the the minimum bounding
     * box of the given geometry node.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param geometryNode
     *            the geometry element
     * @param context
     *            tbd
     * @return the node set of all items in the envelope
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    @Requires(Permission.NONE)
    @Deterministic
    public DBNode[] search(final String indexName, final ANode geometryNode, final GmlGeoXContext context)
            throws GmlGeoXException {
        if (geometryNode == null) {
            return new DBNode[0];
        }
        /* Try lookup in envelope map first. */
        final DBNodeRef entry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) geometryNode);
        if (context.geometryCache().hasEnvelope(entry)) {
            return search(indexName, context.geometryCache().getEnvelope(entry), context);
        } else {
            /* Get JTS geometry and cache the envelope. */
            final com.vividsolutions.jts.geom.Geometry geom = context.geometryCache().getOrCacheGeometry(geometryNode,
                    entry, context);
            if (geom.isEmpty()) {
                throw new GmlGeoXException("Geometry determined for the given node is empty "
                        + "(ensure that the given node is a geometry node that represents a non-empty geometry). "
                        + "Cannot perform a search based upon an empty geometry.");
            }
            context.geometryCache().addEnvelope(entry, geom.getEnvelopeInternal());
            return searchGeom(indexName, geom, context);
        }
    }

    /**
     * Searches the default spatial r-tree index for items whose minimum bounding box intersects with the the minimum
     * bounding box of the given geometry.
     *
     * @param geom
     *            the geometry
     * @param context
     *            tbd
     * @return the node set of all items in the envelope
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public DBNode[] searchGeom(final com.vividsolutions.jts.geom.Geometry geom, final GmlGeoXContext context)
            throws GmlGeoXException {
        return searchGeom(DEFAULT_SPATIAL_INDEX, geom, context);
    }

    /**
     * Searches the named spatial r-tree index for items whose minimum bounding box intersects with the the minimum bounding
     * box of the given geometry.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param geom
     *            the geometry
     * @param context
     *            tbd
     * @return the node set of all items in the envelope
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public DBNode[] searchGeom(final String indexName, com.vividsolutions.jts.geom.Geometry geom,
            GmlGeoXContext context) throws GmlGeoXException {
        if (geom.isEmpty()) {
            throw new GmlGeoXException("Geometry is empty. Cannot perform a search based upon an empty geometry.");
        }
        return search(indexName, geom.getEnvelopeInternal(), context);
    }

    private DBNode[] search(final String indexName, final Envelope env, final GmlGeoXContext context) {
        double x1 = env.getMinX();
        double x2 = env.getMaxX();
        double y1 = env.getMinY();
        double y2 = env.getMaxY();
        return performSearch(indexName, x1, y1, x2, y2, context);
    }

    /**
     * Returns all items in the default spatial r-tree index.
     *
     * @param context
     *            tbd
     *
     * @return the node set of all items in the index
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public DBNode[] search(final GmlGeoXContext context) throws GmlGeoXException {
        // Do we really search here???
        return searchInIndex(DEFAULT_SPATIAL_INDEX, context);
    }

    /**
     * Returns all items in the named spatial r-tree index.
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @param context
     *            tbd
     * @return the node set of all items in the index
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    @Requires(Permission.NONE)
    public DBNode[] searchInIndex(final String indexName, final GmlGeoXContext context) throws GmlGeoXException {
        // Do we really search here???
        try {
            final List<DBNode> nodelist = getAll(indexName, context.dbNodeRefLookup);
            return nodelist.toArray(new DBNode[0]);
        } catch (Exception e) {
            throw new GmlGeoXException(e);
        }
    }

    /**
     * Indexes a feature geometry, using the default index.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param node
     *            represents the indexed item node (can be the gml:id of a GML feature element, or the element itself)
     * @param geometry
     *            represents the GML geometry to index
     * @param context
     *            tbd
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public void index(final ANode node, final ANode geometry, final GmlGeoXContext context) throws GmlGeoXException {
        index(DEFAULT_SPATIAL_INDEX, node, geometry, context);
    }

    /**
     * Indexes a feature geometry, using the named spatial index.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @param node
     *            represents the indexed item node (can be the gml:id of a GML feature element, or the element itself)
     * @param geometry
     *            represents the GML geometry to index
     * @param context
     *            tbd
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public void index(final String indexName, final ANode node, final ANode geometry, final GmlGeoXContext context)
            throws GmlGeoXException {

        if (node instanceof DBNode && geometry instanceof DBNode) {
            try {
                final com.vividsolutions.jts.geom.Geometry _geom = context.geometryCache().getOrCacheGeometry(geometry,
                        context);
                final Envelope env = _geom.getEnvelopeInternal();

                if (!env.isNull()) {
                    final DBNodeRef nodeEntry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) node);
                    if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {
                        context.indexRegister().index(indexName, nodeEntry,
                                Geometries.point(env.getMinX(), env.getMinY()));
                    } else {
                        context.indexRegister().index(indexName, nodeEntry, rTreeRectangle(env));
                    }

                    // also cache the envelope
                    final DBNodeRef geomNodeEntry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) geometry);
                    context.geometryCache().addEnvelope(geomNodeEntry, env);
                }
            } catch (final XMLParsingException e) {
                throw new GmlGeoXException(e);
            }
        }
    }

    /**
     * Prepares spatial indexing of a feature geometry, for the default spatial index.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param node
     *            represents the node of the feature to be indexed
     * @param geometry
     *            represents the GML geometry to index
     * @param context
     *            tbd
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public void prepareSpatialIndex(final ANode node, final ANode geometry, final GmlGeoXContext context)
            throws GmlGeoXException {
        prepareSpatialIndex(DEFAULT_SPATIAL_INDEX, node, geometry, context);
    }

    /**
     * Prepares spatial indexing of a feature geometry, for the named spatial index.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @param node
     *            represents the node of the feature to be indexed
     * @param geometry
     *            represents the GML geometry to index
     * @param context
     *            tbd
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public void prepareSpatialIndex(final String indexName, final ANode node, final ANode geometry,
            final GmlGeoXContext context) throws GmlGeoXException {

        if (node instanceof DBNode && geometry instanceof DBNode) {
            final com.vividsolutions.jts.geom.Geometry _geom = context.geometryCache().getOrCacheGeometry(geometry,
                    context);
            final Envelope env = _geom.getEnvelopeInternal();
            if (!env.isNull()) {
                // cache the index entry
                final DBNodeRef geometryNodeEntry;
                if (_geom.getUserData() != null) {
                    geometryNodeEntry = (DBNodeRef) _geom.getUserData();
                } else {
                    geometryNodeEntry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) node);
                }
                final com.github.davidmoten.rtree.geometry.Geometry treeGeom;
                if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {
                    treeGeom = Geometries.point(env.getMinX(), env.getMinY());
                } else {
                    treeGeom = rTreeRectangle(env);
                }
                prepareSpatialIndex(indexName, geometryNodeEntry, treeGeom);
                // also cache the envelope
                context.geometryCache().addEnvelope(geometryNodeEntry, env);
            }
        }
    }

    /**
     * Prepares spatial indexing of the control points of a feature geometry, for a spatial index with given name. The
     * behavior for retrieving control points can be influenced by the parameter controlPointSearchBehavior.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param indexName
     *            Identifies the index.
     * @param node
     *            represents the node of the feature to be indexed
     * @param geometry
     *            represents the GML geometry to index
     * @param controlPointSearchBehavior
     *            codes to influence to resulting set of control points (multiple values separated by commas):
     *            'IGNORE_ARC_MID_POINT' - to ignore the middle point of an arc (in Arc and ArcString)
     * @param context
     *            tbd
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public void prepareSpatialPointIndex(final @NotNull String indexName, final ANode node, final ANode geometry,
            final String controlPointSearchBehavior, final GmlGeoXContext context) throws GmlGeoXException {

        if (node instanceof DBNode && geometry instanceof DBNode) {

            final List<com.vividsolutions.jts.geom.Point> points = GeometryPointsAnalysis.getControlPoints(geometry,
                    controlPointSearchBehavior, context);

            if (!points.isEmpty()) {
                final DBNodeRef geometryNodeEntry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) node);

                for (com.vividsolutions.jts.geom.Point p : points) {
                    // cache the index entry
                    final com.github.davidmoten.rtree.geometry.Point treeGeom;
                    treeGeom = Geometries.point(p.getX(), p.getY());
                    prepareSpatialIndex(indexName, geometryNodeEntry, treeGeom);
                }
            }
        }
    }

    /**
     * Prepares spatial indexing of a feature geometry, for the default and a named spatial index.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param indexName
     *            Identifies the index. The empty string identifies the default index.
     * @param node
     *            represents the node of the feature to be indexed
     * @param geometry
     *            represents the GML geometry to index
     * @param context
     *            tbd
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public void prepareDefaultAndSpecificSpatialIndex(final String indexName, final ANode node, final ANode geometry,
            final GmlGeoXContext context) throws GmlGeoXException {
        if (node instanceof DBNode && geometry instanceof DBNode) {
            final com.vividsolutions.jts.geom.Geometry _geom = context.geometryCache().getOrCacheGeometry(geometry,
                    context);
            final Envelope env = _geom.getEnvelopeInternal();
            if (!env.isNull()) {
                // cache the index entry
                final DBNodeRef geometryNodeEntry;
                if (_geom.getUserData() != null) {
                    geometryNodeEntry = (DBNodeRef) _geom.getUserData();
                } else {
                    geometryNodeEntry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) node);
                }
                final com.github.davidmoten.rtree.geometry.Geometry treeGeom;
                if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {
                    treeGeom = Geometries.point(env.getMinX(), env.getMinY());
                } else {
                    treeGeom = rTreeRectangle(env);
                }
                prepareSpatialIndex(indexName, geometryNodeEntry, treeGeom);
                prepareSpatialIndex(DEFAULT_SPATIAL_INDEX, geometryNodeEntry, treeGeom);
                // also cache the envelope
                context.geometryCache().addEnvelope(geometryNodeEntry, env);
            }
        }
    }

    /**
     * Create the default spatial index using bulk loading.
     *
     * <p>
     * Uses the index entries that have been prepared using method(s) prepareSpatialIndex(...).
     *
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public void buildSpatialIndex() throws GmlGeoXException {
        buildSpatialIndex(DEFAULT_SPATIAL_INDEX);
    }

    /**
     * Create the named spatial index using bulk loading.
     *
     * <p>
     * Uses the index entries that have been prepared using method(s) prepareSpatialIndex(...).
     *
     * @param indexName
     *            Identifies the index. <code>null</code> or the empty string identifies the default index.
     * @throws GmlGeoXException
     *             If the index has already been built.
     */
    public void buildSpatialIndex(final String indexName) throws GmlGeoXException {
        buildIndexUsingBulkLoading(indexName);
    }

    /**
     * Search the spatial index for minimal intersecting bounding boxes with the bounding box of geometryNode.
     *
     * @param index
     *            represents the spatial index as an RTree Object
     * @param geometryNode
     *            represents the GML geometry through which the index is searched
     * @param context
     *            tbd
     * @return the node set of all items in the envelope of the input node
     * @throws GmlGeoXException
     *             tbd
     */
    public DBNode[] searchIndex(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            final ANode geometryNode, final GmlGeoXContext context) throws GmlGeoXException {

        final Rectangle rTreeRectangle = rTreeRectangle(geometryNode, context);
        return context.dbNodeRefLookup
                .collect(index.search(rTreeRectangle).map(com.github.davidmoten.rtree.Entry::value))
                .toArray(new DBNode[0]);
    }

    /**
     * Search the index for intersecting bounding boxes with the bounding box of jtsGeom.
     *
     * @param index
     *            represents the spatial index as an RTree Object
     * @param jtsGeom
     *            represents the jts geometry through which the index is searched
     * @param context
     *            tbd
     * @return the node set of all items in the envelope of the input node
     */
    public DBNode[] searchIndexGeom(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            final com.vividsolutions.jts.geom.Geometry jtsGeom, final GmlGeoXContext context) {

        final Rectangle rTreeRectangle = rTreeRectangle(jtsGeom);
        return context.dbNodeRefLookup
                .collect(index.search(rTreeRectangle).map(com.github.davidmoten.rtree.Entry::value))
                .toArray(new DBNode[0]);
    }

    /**
     * Returns the nearest k entries (k=maxCount) to the bounding box of the given geometry where the entries are strictly
     * less than a given maximum distance from the bounding box.
     *
     * @param index
     *            represents the spatial index as an RTree Object
     * @param geometryNode
     *            represents the GML geometry through which the index is searched
     * @param maxDistance
     *            max distance for returned entries
     * @param maxCount
     *            max number of entries to return
     * @param context
     *            tbd
     * @return maxCount nearest entries to the bounding box of the geometryNode
     * @throws GmlGeoXException
     *             tbd
     */
    public DBNode[] nearestSearchIndex(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            final ANode geometryNode, final double maxDistance, final int maxCount, final GmlGeoXContext context)
            throws GmlGeoXException {

        final Rectangle rTreeRectangle = rTreeRectangle(geometryNode, context);
        return context.dbNodeRefLookup.collect(
                index.nearest(rTreeRectangle, maxDistance, maxCount).map(com.github.davidmoten.rtree.Entry::value))
                .toArray(new DBNode[0]);
    }

    /**
     * Returns the nearest k entries (k=maxCount) to the bounding box of the given geometry where the entries are strictly
     * less than a given maximum distance from the bounding box.
     *
     * @param index
     *            represents the spatial index as an RTree Object
     * @param jtsGeom
     *            represents the jts geometry through which the index is searched
     * @param maxDistance
     *            max distance for returned entries
     * @param maxCount
     *            max number of entries to return
     * @param context
     *            tbd
     * @return maxCount nearest entries to the bounding box of the geometryNode
     */
    @Deterministic
    @Requires(Permission.NONE)
    public DBNode[] nearestSearchIndexGeom(final RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            final com.vividsolutions.jts.geom.Geometry jtsGeom, final double maxDistance, final int maxCount,
            final GmlGeoXContext context) {

        final Rectangle rTreeRectangle = rTreeRectangle(jtsGeom);
        return context.dbNodeRefLookup.collect(
                index.nearest(rTreeRectangle, maxDistance, maxCount).map(com.github.davidmoten.rtree.Entry::value))
                .toArray(new DBNode[0]);
    }

    private Rectangle rTreeRectangle(final ANode geometryNode, final GmlGeoXContext context) throws GmlGeoXException {
        final DBNodeRef entry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) geometryNode);
        if (!context.geometryCache().hasEnvelope(entry)) {
            /* Get JTS geometry and cache the envelope. */
            final com.vividsolutions.jts.geom.Geometry geom = context.geometryCache().getOrCacheGeometry(geometryNode,
                    entry, context);
            if (geom.isEmpty()) {
                throw new GmlGeoXException("Geometry determined for the given node is empty "
                        + "(ensure that the given node is a geometry node that represents a non-empty geometry). "
                        + "Cannot perform a search based upon an empty geometry.");
            }
            context.geometryCache().addEnvelope(entry, geom.getEnvelopeInternal());
        }
        return rTreeRectangle(context.geometryCache().getEnvelope(entry));
    }

    private static Rectangle rTreeRectangle(final com.vividsolutions.jts.geom.Geometry jtsGeom) {
        return rTreeRectangle(jtsGeom.getEnvelopeInternal());
    }

    private static Rectangle rTreeRectangle(final com.vividsolutions.jts.geom.Envelope jtsEnvelope) {
        double x1 = jtsEnvelope.getMinX();
        double x2 = jtsEnvelope.getMaxX();
        double y1 = jtsEnvelope.getMinY();
        double y2 = jtsEnvelope.getMaxY();
        return Geometries.rectangle(x1, y1, x2, y2);
    }

    /**
     * Retrieve all entries contained within the given index.
     *
     * @param index
     *            tbd
     * @param context
     *            tbd
     * @return the index entries; can be empty but not <code>null</code>
     */
    public DBNode[] entriesOfIndex(RTree<DBNodeRef, com.github.davidmoten.rtree.geometry.Geometry> index,
            GmlGeoXContext context) {

        final List<DBNode> result = new ArrayList<>();

        if (index != null && !index.isEmpty()) {

            result.addAll(context.dbNodeRefLookup
                    .collect(index.entries().map(com.github.davidmoten.rtree.Entry::value)));
        }

        return result.toArray(new DBNode[0]);
    }
}
