/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2011, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geoserver.data.versioning.decorator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.geogit.api.NodeRef;
import org.geogit.api.ObjectId;
import org.geogit.api.RevCommit;
import org.geogit.api.RevTree;
import org.geogit.api.porcelain.LogOp;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.filter.FilterFactoryImpl;
import org.junit.Test;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.Version;

public class VersionedTest extends DecoratedTestCase {

    @Test
    public void testNoHistory() throws Exception {
        SimpleFeatureIterator feats = null;
        try {
            SimpleFeatureSource source = versioned.getFeatureSource(sampleName);
            assertNotNull(source);

            Query query = new Query(sampleName);
            query.setVersion("ALL");
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            List<SimpleFeature> featList = getOriginalFeatures(sampleName);
            assertEquals(featList.size(), collection.size());
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                LOGGER.info(feat.toString());
                assertTrue(containsFeature(feat, featList));
            }
            LOGGER.info("End testNoHistory");
        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testResourceId() throws Exception {
        SimpleFeatureIterator feats = null;
        List<SimpleFeature> featList = new ArrayList<SimpleFeature>(1);
        featList.add(sample1);
        try {
            SimpleFeatureSource source = versioned.getFeatureSource(sampleName);
            assertNotNull(source);

            FilterFactory2 ff = new FilterFactoryImpl();
            Filter filter = ff.id(ff.resourceId(idS1, sampleFid1.getFeatureVersion(), null));

            Query query = new Query(sampleName, filter);
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            assertEquals(featList.size(), collection.size());
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                LOGGER.info(feat.toString());
                assertTrue(feat.toString() + " is expected.", containsFeature(feat, featList));
            }

        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testResourceIdHistory() throws Exception {
        updateSampleFeatures();
        SimpleFeatureIterator feats = null;
        List<SimpleFeature> featList = new ArrayList<SimpleFeature>(1);
        featList.add(sample2);
        try {
            SimpleFeatureSource source = versioned.getFeatureSource(sampleName);
            assertNotNull(source);

            FilterFactory2 ff = new FilterFactoryImpl();
            Filter filter = ff.id(ff.resourceId(idS2, sampleFid2.getFeatureVersion(), null));

            Query query = new Query(sampleName, filter);
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            assertEquals(featList.size(), collection.size());
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                LOGGER.info(feat.toString());
                assertTrue(feat.toString() + " is expected.", containsFeature(feat, featList));
            }
        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testResourceIdTrivialQuery() throws Exception {
        updateSampleFeatures();
        SimpleFeatureIterator feats = null;
        List<SimpleFeature> featList = new ArrayList<SimpleFeature>(1);
        featList.add(sample1);
        try {
            SimpleFeatureSource source = versioned.getFeatureSource(sampleName);
            assertNotNull(source);

            FilterFactory2 ff = new FilterFactoryImpl();
            Filter filter = ff.id(ff.resourceId(idS1, sampleFid1.getFeatureVersion(), null));

            Query query = new Query(sampleName, filter);
            query.setVersion(Version.Action.ALL);
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            assertEquals(featList.size(), collection.size());
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                LOGGER.info(feat.toString());
                assertTrue(feat.toString() + " is expected.", containsFeature(feat, featList));
            }
        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testResourceIdReinforcingQuery() throws Exception {
        updateSampleFeatures();
        SimpleFeatureIterator feats = null;
        List<SimpleFeature> featList = new ArrayList<SimpleFeature>(1);
        featList.add(sample1);
        try {
            SimpleFeatureSource source = versioned.getFeatureSource(sampleName);
            assertNotNull(source);

            FilterFactory2 ff = new FilterFactoryImpl();
            Filter filter = ff.id(ff.resourceId(idS1, sampleFid1.getFeatureVersion(), null));

            Query query = new Query(sampleName, filter);
            query.setVersion(Version.Action.FIRST);
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            assertEquals(featList.size(), collection.size());
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                LOGGER.info(feat.toString());
                assertTrue(feat.toString() + " is expected.", containsFeature(feat, featList));
            }
        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testResourceIdContradictoryQuery() throws Exception {
        updateSampleFeatures();
        SimpleFeatureIterator feats = null;
        try {
            SimpleFeatureSource source = versioned.getFeatureSource(sampleName);
            assertNotNull(source);

            FilterFactory2 ff = new FilterFactoryImpl();
            Filter filter = ff.id(ff.resourceId(idS1, sampleFid1.getFeatureVersion(), null));

            Query query = new Query(sampleName, filter);
            query.setVersion(Version.Action.LAST);
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            assertEquals(0, collection.size());
        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testFullHistory() throws Exception {
        updateTestFeatures();
        SimpleFeatureIterator feats = null;
        try {
            SimpleFeatureSource source = versioned.getFeatureSource(testName);
            assertNotNull(source);

            Query query = new Query(testName);
            query.setVersion("ALL");
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            List<SimpleFeature> featList = getAllFeatures(testName);
            LOGGER.info(" " + featList.size());
            LOGGER.info(" " + collection.size());
            assertEquals(featList.size(), collection.size());
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                assertTrue(containsFeature(feat, featList));
            }
        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testBBoxHistory() throws Exception {
        updateTestFeatures();
        SimpleFeatureIterator feats = null;
        try {

            List<SimpleFeature> featList = new ArrayList<SimpleFeature>(4);
            featList.add(test1);
            featList.add(test3);

            SimpleFeatureSource source = versioned.getFeatureSource(testName);
            assertNotNull(source);

            FilterFactory2 ff = new FilterFactoryImpl();
            Filter filter = ff.bbox("ln", -1.5, -1.5, 1.5, 1.5, "srid=4326");
            Query query = new Query(testName, filter);
            query.setVersion("FIRST");
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            assertEquals(featList.size(), collection.size());
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                LOGGER.info(feat.toString());
                assertTrue(feat.toString() + " is expected.", containsFeature(feat, featList));
            }
        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testBBoxFullHistory() throws Exception {

        SimpleFeatureIterator feats = null;
        try {
            updateTestFeatures();

            List<SimpleFeature> featList = new ArrayList<SimpleFeature>(4);
            featList.add(test1);
            featList.add(test3);
            featList.add(test1b);
            featList.add(test3b);

            SimpleFeatureSource source = versioned.getFeatureSource(testName);
            assertNotNull(source);

            FilterFactory2 ff = new FilterFactoryImpl();
            Filter filter = ff.bbox("ln", -1.5, -1.5, 1.5, 1.5, "srid=4326");
            Query query = new Query(testName, filter);
            query.setVersion("ALL");
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            assertEquals(featList.size(), collection.size());
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                LOGGER.info(feat.toString());
                assertTrue(feat.toString() + " is expected.", containsFeature(feat, featList));
            }
        } finally {
            if (feats != null)
                feats.close();
        }
    }

    @Test
    public void testConsistencyNoHistory() throws Exception {
        verifyVersionedConsistency();
        verifyUnversionedConsistency();
    }

    @Test
    public void testConsistencyHistory() throws Exception {
        updateTestFeatures();
        updateSampleFeatures();
        finalTestFeatureUpdate();
        verifyVersionedConsistency();
        verifyUnversionedConsistency();
    }

    private void verifyUnversionedConsistency() throws Exception {
        SimpleFeatureIterator feats = null;
        try {
            List<SimpleFeature> decoratedFeatures = new ArrayList<SimpleFeature>();

            SimpleFeatureSource source = unversioned.getFeatureSource(testName);
            assertNotNull(source);

            Query query = new Query(testName);
            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                decoratedFeatures.add(feat);
            }

            List<SimpleFeature> versionedFeatures = getLatestFeatures(testType);
            compareFeatureLists(decoratedFeatures, versionedFeatures);

        } finally {
            if (feats != null)
                feats.close();
        }

    }

    private void verifyVersionedConsistency() throws Exception {
        SimpleFeatureIterator feats = null;
        try {
            List<SimpleFeature> decoratedFeatures = new ArrayList<SimpleFeature>();

            SimpleFeatureSource source = versioned.getFeatureSource(testName);
            assertNotNull(source);

            Query query = new Query(testName);
            query.setVersion("ALL");

            SimpleFeatureCollection collection = source.getFeatures(query);
            assertNotNull(collection);
            feats = collection.features();
            assertNotNull(feats);
            while (feats.hasNext()) {
                SimpleFeature feat = feats.next();
                assertNotNull(feat);
                decoratedFeatures.add(feat);
            }

            List<SimpleFeature> versionedFeatures = getAllFeatures(testType);
            compareFeatureLists(decoratedFeatures, versionedFeatures);

        } finally {
            if (feats != null)
                feats.close();
        }
    }

    private void compareFeatureLists(List<SimpleFeature> left, List<SimpleFeature> right) {
        assertEquals(left.size(), right.size());
        Iterator<SimpleFeature> it = left.iterator();
        while (it.hasNext()) {
            SimpleFeature lfeat = it.next();
            LOGGER.info(lfeat.toString());
            assertTrue(containsFeature(lfeat, right));
        }
    }

    private List<SimpleFeature> getLatestFeatures(SimpleFeatureType featureType) {
        Name typeName = featureType.getName();

        LogOp logOp = geogit.command(LogOp.class);
        logOp.addPath(typeName.getLocalPart()).setLimit(1);
        return getFeaturesFromLog(logOp, featureType);
    }

    private List<SimpleFeature> getAllFeatures(SimpleFeatureType featureType) {
        Name typeName = featureType.getName();

        LogOp logOp = geogit.command(LogOp.class);
        logOp.addPath(typeName.getLocalPart());
        return getFeaturesFromLog(logOp, featureType);
    }

    private List<SimpleFeature> getFeaturesFromLog(LogOp logOp, SimpleFeatureType featureType) {
        Name typeName = featureType.getName();
        try {
            Set<NodeRef> refs = new HashSet<NodeRef>();
            Iterator<RevCommit> featureCommits = logOp.call();
            while (featureCommits.hasNext()) {
                RevCommit cmt = featureCommits.next();
                refs.addAll(getNodeRefsByCommit(cmt, typeName));
            }
            List<SimpleFeature> feats = new ArrayList<SimpleFeature>();
            for (NodeRef ref : refs) {
                SimpleFeature feat = (SimpleFeature) repo.getFeature(featureType, ref.getName(),
                        ref.getObjectId());
                feats.add(feat);
            }
            return feats;
        } catch (Exception ex) {
            ex.printStackTrace();
            /*
             * Need some logging.
             */
            return Collections.emptyList();
        }
    }

    private List<NodeRef> getNodeRefsByCommit(RevCommit commit, Name typeName) {
        List<NodeRef> treeNodeRefs = new ArrayList<NodeRef>();
        if (commit != null) {
            ObjectId commitTreeId = commit.getTreeId();
            RevTree commitTree = repo.getTree(commitTreeId);
            NodeRef typeNodeRef = commitTree.get(typeName.getLocalPart());
            RevTree typeTree = repo.getTree(typeNodeRef.getObjectId());
            Iterator<NodeRef> it = typeTree.iterator(null);

            while (it.hasNext()) {
                NodeRef nextNodeRef = it.next();
                treeNodeRefs.add(nextNodeRef);
            }
        }
        return treeNodeRefs;
    }
}
