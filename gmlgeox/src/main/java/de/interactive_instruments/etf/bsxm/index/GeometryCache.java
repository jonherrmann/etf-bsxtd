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
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.interactive_instruments.etf.bsxm.GeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoX;
import de.interactive_instruments.etf.bsxm.GmlGeoXContext;
import de.interactive_instruments.etf.bsxm.GmlGeoXException;
import de.interactive_instruments.etf.bsxm.JtsTransformer;
import de.interactive_instruments.etf.bsxm.node.DBNodeRef;
import de.interactive_instruments.etf.bsxm.node.ExternalizableDBNodeRefMap;

/**
 * The GeometryManager builds and maintains an in-memory cache for JTS geometries that can be used with the GmlGeoX
 * module. The cache is filled during the indexing of the geometries and updated when geometries are accessed using the
 * {@link GmlGeoX#getOrCacheGeometry(ANode)} function.
 *
 * @author Clemens Portele (portele <at> interactive-instruments <dot> de)
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 */
public final class GeometryCache implements Externalizable {

    private static final Logger logger = LoggerFactory.getLogger(GeometryCache.class);

    // Max cache entries as number
    private static final String ETF_GEOCACHE_SIZE = "etf.gmlgeox.geocache.size";

    // Record hitcounts and misscounts as boolean
    private static final String ETF_GEOCACHE_REC_STATS = "etf.gmlgeox.geocache.statistics";

    /**
     * Geometry cache, where a key is the ID of a database node that represents a geometry, and the value is the JTS
     * geometry parsed from that node.
     */
    private Cache<DBNodeRef, Geometry> geometryCache = null;
    private HashMap<DBNodeRef, Envelope> envelopeByDBNodeEntry = new HashMap<>();
    private int maxSizeOfGeometryCache;

    public GeometryCache() {
        resetCache(Integer.valueOf(System.getProperty(ETF_GEOCACHE_SIZE, "100000")));
    }

    /**
     * Resets the geometry cache, by replacing the existing cache with a new cache of the given size. That means that all
     * cached geometries will be lost.
     *
     * @param maxSize
     *            new cache size
     */
    public void resetCache(final int maxSize) {
        try {
            if (logger.isDebugEnabled() || Boolean.valueOf(System.getProperty(ETF_GEOCACHE_REC_STATS, "false"))) {
                geometryCache = Caffeine.newBuilder().recordStats().maximumSize(maxSize).build();
            } else {
                geometryCache = Caffeine.newBuilder().maximumSize(maxSize).build();
            }
            this.maxSizeOfGeometryCache = maxSize;
        } catch (Exception e) {
            throw new IllegalArgumentException("Cache for geometries could not be initialized: " + e.getMessage());
        }
    }

    /**
     * Get a geometry from the cache
     *
     * @param dbNode
     *            tbd
     * @return the parsed geometry of the geometry node, or <code>null</code> if no geometry was found
     */
    @Nullable
    public com.vividsolutions.jts.geom.Geometry getGeometry(final DBNodeRef dbNode) {
        return geometryCache.getIfPresent(dbNode);
    }

    /**
     * Returns the number of all read accesses to the cache
     *
     * @return number of read accesses to the cache
     */
    public long getCount() {
        return geometryCache.stats().hitCount();
    }

    /**
     * Returns the number of all failed read accesses to the cache
     *
     * @return number of failed read accesses to the cache
     */
    public long getMissCount() {
        return geometryCache.stats().missCount();
    }

    /**
     * Put a feature geometry in the cache
     *
     * @param nodeRef
     *            the reference of the database node that represents the geometry
     * @param geom
     *            the geometry to cache
     */
    public void cacheGeometry(final DBNodeRef nodeRef, final com.vividsolutions.jts.geom.Geometry geom) {
        geometryCache.put(nodeRef, geom);
    }

    /**
     * Computes the envelope of a geometry.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(org.deegree.geometry.Geometry)} for a list of supported and unsupported
     * geometry types.
     *
     * @param geometryNode
     *            represents the geometry
     * @param context
     *            tbd
     * @return The bounding box, an array { x1, y1, x2, y2 }
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public Object[] envelope(final ANode geometryNode, final GmlGeoXContext context) throws GmlGeoXException {
        /* Try lookup in envelope map first. */
        final DBNodeRef geometryNodeEntry = context.dbNodeRefFactory.createDBNodeEntry((DBNode) geometryNode);
        Envelope env;
        if (hasEnvelope(geometryNodeEntry)) {
            env = getEnvelope(geometryNodeEntry);
        } else {
            /* Get JTS geometry and cache the envelope. */
            com.vividsolutions.jts.geom.Geometry geom = getOrCacheGeometry(geometryNode, geometryNodeEntry, context);
            env = geom.getEnvelopeInternal();
            addEnvelope(geometryNodeEntry, env);
        }
        final Object[] res = {env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()};
        return res;
    }

    /**
     * @param entry
     *            tbd
     * @return <code>true</code> if a mapping to an envelope exists for the given DBNode entry, else <code>false</code>s
     */
    public boolean hasEnvelope(final DBNodeRef entry) {
        return envelopeByDBNodeEntry.containsKey(entry);
    }

    /**
     * @param entry
     *            tbd
     * @return The envelope stored for the given entry, or <code>null</code> if the manager does not contain a mapping for
     *         the given entry.
     */
    public Envelope getEnvelope(final DBNodeRef entry) {
        return envelopeByDBNodeEntry.get(entry);
    }

    /**
     * Adds a mapping from the given entry to the given envelope to this manager.
     *
     * @param entry
     *            tbd
     * @param env
     *            tbd
     */
    public void addEnvelope(final DBNodeRef entry, final Envelope env) {
        envelopeByDBNodeEntry.put(entry, env);
    }

    /**
     * Get the current size of the geometry cache.
     *
     * @return the size of the geometry cache
     */
    public int getCacheSize() {
        return this.maxSizeOfGeometryCache;
    }

    @Override
    public void writeExternal(final ObjectOutput out) throws IOException {
        out.writeInt(maxSizeOfGeometryCache);
        final ExternalizableDBNodeRefMap dbNodeRefMap = new ExternalizableDBNodeRefMap();
        // Geometries
        {
            final ConcurrentMap<DBNodeRef, Geometry> geometryCacheMap = geometryCache.asMap();
            out.writeInt(geometryCacheMap.size());
            int posGeo = 0;
            final int[] geometryCacheDBNodeRefPositions = dbNodeRefMap.addAndGetRefPositions(geometryCacheMap.keySet());
            for (final Geometry geometry : geometryCacheMap.values()) {
                out.writeInt(geometryCacheDBNodeRefPositions[posGeo++]);
                out.writeObject(ExternalizableJtsGeometry.create(geometry));
            }
        }
        // Envelopes
        {
            int posEnvelope = 0;
            final int[] geometryCacheDBNodeRefPositions = dbNodeRefMap
                    .addAndGetRefPositions(envelopeByDBNodeEntry.keySet());
            out.writeInt(envelopeByDBNodeEntry.size());
            for (final Envelope geometry : envelopeByDBNodeEntry.values()) {
                out.writeInt(geometryCacheDBNodeRefPositions[posEnvelope++]);
                out.writeObject(new ExternalizableJtsEnvelope(geometry));
            }
        }
        out.writeObject(dbNodeRefMap);
    }

    @Override
    public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
        this.maxSizeOfGeometryCache = in.readInt();

        final GeometryFactory jtsGeomFactory = new GeometryFactory();

        // Prepare Geometries
        final int[] geometryCacheDBNodeRefPositions = new int[in.readInt()];
        final List<Geometry> geometries = new ArrayList<>(geometryCacheDBNodeRefPositions.length);
        for (int i = 0; i < geometryCacheDBNodeRefPositions.length; i++) {
            geometryCacheDBNodeRefPositions[i] = in.readInt();
            geometries.add(((ExternalizableJtsGeometry) in.readObject()).toJtsGeometry(jtsGeomFactory));
        }

        // Prepare Envelopes
        final int[] envelopeDBNodeRefPositions = new int[in.readInt()];
        final List<Envelope> envelopes = new ArrayList<>(geometryCacheDBNodeRefPositions.length);
        for (int i = 0; i < envelopeDBNodeRefPositions.length; i++) {
            envelopeDBNodeRefPositions[i] = in.readInt();
            envelopes.add(((ExternalizableJtsEnvelope) in.readObject()).toEnvelope());
        }

        // Restore DBNodeRefs
        final ExternalizableDBNodeRefMap dbNodeRefMap = ((ExternalizableDBNodeRefMap) in.readObject());

        // Restore Geometries
        {
            resetCache(this.maxSizeOfGeometryCache);
            final DBNodeRef[] geometryCacheDBNodeRefs = dbNodeRefMap.getByRefPositions(geometryCacheDBNodeRefPositions);
            for (int i = 0; i < geometryCacheDBNodeRefs.length; i++) {
                this.geometryCache.put(geometryCacheDBNodeRefs[i], geometries.get(i));
            }
        }

        // Restore Envelopes
        {
            final DBNodeRef[] envelopeDBNodeRefs = dbNodeRefMap.getByRefPositions(envelopeDBNodeRefPositions);
            this.envelopeByDBNodeEntry = new HashMap<>(envelopeDBNodeRefs.length);
            for (int i = 0; i < envelopeDBNodeRefs.length; i++) {
                this.envelopeByDBNodeEntry.put(envelopeDBNodeRefs[i], envelopes.get(i));
            }
        }
    }

    /**
     * Retrieve the geometry represented by a given node as a JTS geometry. First try the cache and if it is not in the
     * cache construct it from the XML.
     *
     * <p>
     * See {@link JtsTransformer#toJTSGeometry(org.deegree.geometry.Geometry)} for a list of supported and unsupported
     * geometry types.
     *
     * @param geomNode
     *            the node that represents the geometry
     * @param context
     *            tbd
     * @return the geometry of the node; can be an empty geometry if the node does not represent a geometry
     * @throws GmlGeoXException
     *             In case an exception occurred.
     */
    public com.vividsolutions.jts.geom.Geometry getOrCacheGeometry(final ANode geomNode, final GmlGeoXContext context)
            throws GmlGeoXException {

        com.vividsolutions.jts.geom.Geometry geom;
        if (geomNode instanceof DBNode) {
            final DBNodeRef geomNodeRef = context.dbNodeRefFactory.createDBNodeEntry((DBNode) geomNode);
            geom = getGeometry(geomNodeRef);
            if (geom == null) {
                geom = context.jtsTransformer.singleObjectToJTSGeometry(geomNode);
                cacheGeometry(geomNodeRef, geom);
            }
        } else {
            geom = context.jtsTransformer.singleObjectToJTSGeometry(geomNode);
        }

        return geom;
    }

    public com.vividsolutions.jts.geom.Geometry getOrCacheGeometry(final ANode geomNode, final DBNodeRef geomNodeRef,
            final GeoXContext context) throws GmlGeoXException {

        com.vividsolutions.jts.geom.Geometry geom = getGeometry(geomNodeRef);
        if (geom == null) {
            geom = context.jtsTransformer.singleObjectToJTSGeometry(geomNode);
            if (geom != null) {
                // geom.setUserData(geomNodeRef);
                cacheGeometry(geomNodeRef, geom);
            }
        }
        if (geom == null) {
            geom = context.jtsTransformer.emptyJTSGeometry();
        }

        return geom;
    }
}
