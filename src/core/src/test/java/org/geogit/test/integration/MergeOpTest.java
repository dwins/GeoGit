/* Copyright (c) 2013 OpenPlans. All rights reserved.
 * This code is licensed under the BSD New License, available at the root
 * application directory.
 */
package org.geogit.test.integration;

import static org.geogit.api.NodeRef.appendChild;

import java.util.Iterator;
import java.util.List;

import org.geogit.api.NodeRef;
import org.geogit.api.ObjectId;
import org.geogit.api.Ref;
import org.geogit.api.RevCommit;
import org.geogit.api.RevFeature;
import org.geogit.api.RevFeatureBuilder;
import org.geogit.api.RevTree;
import org.geogit.api.plumbing.FindTreeChild;
import org.geogit.api.plumbing.RefParse;
import org.geogit.api.plumbing.RevObjectParse;
import org.geogit.api.plumbing.UpdateRef;
import org.geogit.api.plumbing.UpdateSymRef;
import org.geogit.api.plumbing.merge.Conflict;
import org.geogit.api.plumbing.merge.ConflictsReadOp;
import org.geogit.api.plumbing.merge.ReadMergeCommitMessageOp;
import org.geogit.api.porcelain.AddOp;
import org.geogit.api.porcelain.BranchCreateOp;
import org.geogit.api.porcelain.CheckoutOp;
import org.geogit.api.porcelain.CommitOp;
import org.geogit.api.porcelain.ConfigOp;
import org.geogit.api.porcelain.ConfigOp.ConfigAction;
import org.geogit.api.porcelain.LogOp;
import org.geogit.api.porcelain.MergeOp;
import org.geogit.api.porcelain.MergeOp.MergeReport;
import org.geogit.api.porcelain.NothingToCommitException;
import org.geogit.api.porcelain.PullOp;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;

public class MergeOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        // These values should be used during a commit to set author/committer
        // TODO: author/committer roles need to be defined better, but for
        // now they are the same thing.
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("groldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@opengeo.org").call();
    }

    @Test
    public void testMerge() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogit.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogit.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogit.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogit.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogit.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogit.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogit.command(RefParse.class).setName("branch1").call().get();
        MergeReport mergeReport = geogit.command(MergeOp.class)
                .addCommit(Suppliers.ofInstance(branch1.getObjectId()))
                .setMessage("My merge message.").call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(pointsName, points3.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(linesName, lines1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        Iterator<RevCommit> log = geogit.command(LogOp.class).call();

        // Merge Commit
        RevCommit logCmerge = log.next();
        assertEquals("My merge message.", logCmerge.getMessage());
        assertEquals(2, logCmerge.getParentIds().size());
        assertEquals(c4.getId(), logCmerge.getParentIds().get(0));
        assertEquals(c2.getId(), logCmerge.getParentIds().get(1));

        // Commit 4
        RevCommit logC4 = log.next();
        assertEquals(c4.getAuthor(), logC4.getAuthor());
        assertEquals(c4.getCommitter(), logC4.getCommitter());
        assertEquals(c4.getMessage(), logC4.getMessage());
        assertEquals(c4.getTreeId(), logC4.getTreeId());

        // Commit 3
        RevCommit logC3 = log.next();
        assertEquals(c3.getAuthor(), logC3.getAuthor());
        assertEquals(c3.getCommitter(), logC3.getCommitter());
        assertEquals(c3.getMessage(), logC3.getMessage());
        assertEquals(c3.getTreeId(), logC3.getTreeId());

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c2.getAuthor(), logC2.getAuthor());
        assertEquals(c2.getCommitter(), logC2.getCommitter());
        assertEquals(c2.getMessage(), logC2.getMessage());
        assertEquals(c2.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testSpecifyAuthor() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        geogit.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogit.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogit.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogit.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogit.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogit.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogit.command(RefParse.class).setName("branch1").call().get();
        geogit.command(MergeOp.class).setAuthor("Merge Author", "merge@author.com")
                .addCommit(Suppliers.ofInstance(branch1.getObjectId()))
                .setMessage("My merge message.").call();

        Iterator<RevCommit> log = geogit.command(LogOp.class).call();

        // Merge Commit
        RevCommit logCmerge = log.next();
        assertEquals("My merge message.", logCmerge.getMessage());
        assertEquals("Merge Author", logCmerge.getAuthor().getName().get());
        assertEquals("merge@author.com", logCmerge.getAuthor().getEmail().get());
        assertEquals(2, logCmerge.getParentIds().size());
        assertEquals(c4.getId(), logCmerge.getParentIds().get(0));
        assertEquals(c2.getId(), logCmerge.getParentIds().get(1));
    }

    @Test
    public void testMergeMultiple() throws Exception {
        // Create the following revision graph
        // . o
        // . |
        // . o - Points 1 added
        // . |\
        // . | o - branch1 - Points 2 added
        // . |
        // . o - Points 3 added
        // ./|
        // o | - branch 2 - Lines 1 added
        // . |
        // . o - master - HEAD - Lines 2 added
        insertAndAdd(points1);
        final RevCommit c1 = geogit.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogit.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogit.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master, then create branch2 and checkout
        geogit.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogit.command(CommitOp.class).setMessage("commit for " + idP3).call();
        geogit.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch2").call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogit.command(CommitOp.class).setMessage("commit for " + idL1).call();

        geogit.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(lines2);
        final RevCommit c5 = geogit.command(CommitOp.class).setMessage("commit for " + idL2).call();

        // Merge branch1 and branch2 into master to create the following revision graph
        // . o
        // . |
        // . o - Points 1 added
        // . |\
        // . | o - branch1 - Points 2 added
        // . | |
        // . o | - Points 3 added
        // ./| |
        // o | | - branch 2 - Lines 1 added
        // | | |
        // | o | - Lines 2 added
        // .\|/
        // . o - master - HEAD - Merge commit

        Ref branch1 = geogit.command(RefParse.class).setName("branch1").call().get();
        Ref branch2 = geogit.command(RefParse.class).setName("branch2").call().get();
        final MergeReport mergeReport = geogit.command(MergeOp.class)
                .addCommit(Suppliers.ofInstance(branch1.getObjectId()))
                .addCommit(Suppliers.ofInstance(branch2.getObjectId()))
                .setMessage("My merge message.").call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(pointsName, points2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(pointsName, points3.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(linesName, lines1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(linesName, lines2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        Iterator<RevCommit> log = geogit.command(LogOp.class).setFirstParentOnly(true).call();

        // Commit 4
        RevCommit logC4 = log.next();
        assertEquals("My merge message.", logC4.getMessage());
        assertEquals(3, logC4.getParentIds().size());
        assertEquals(c5.getId(), logC4.getParentIds().get(0));
        assertEquals(c2.getId(), logC4.getParentIds().get(1));
        assertEquals(c4.getId(), logC4.getParentIds().get(2));

        // Commit 3
        RevCommit logC3 = log.next();
        assertEquals(c5.getAuthor(), logC3.getAuthor());
        assertEquals(c5.getCommitter(), logC3.getCommitter());
        assertEquals(c5.getMessage(), logC3.getMessage());
        assertEquals(c5.getTreeId(), logC3.getTreeId());

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c3.getAuthor(), logC2.getAuthor());
        assertEquals(c3.getCommitter(), logC2.getCommitter());
        assertEquals(c3.getMessage(), logC2.getMessage());
        assertEquals(c3.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testMergeNoCommitMessage() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - Points 3 added
        // |
        // o - master - HEAD - Lines 1 added
        insertAndAdd(points1);
        final RevCommit c1 = geogit.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogit.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogit.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogit.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        final RevCommit c3 = geogit.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        final RevCommit c4 = geogit.command(CommitOp.class).setMessage("commit for " + idL1).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // | |
        // o | - Lines 1 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogit.command(RefParse.class).setName("branch1").call().get();
        final MergeReport mergeReport = geogit.command(MergeOp.class)
                .addCommit(Suppliers.ofInstance(branch1.getObjectId())).call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(pointsName, points3.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(linesName, lines1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        Iterator<RevCommit> log = geogit.command(LogOp.class).setFirstParentOnly(true).call();

        // Commit 4
        RevCommit logC4 = log.next();
        assertTrue(logC4.getMessage().contains("refs/heads/branch1"));
        assertEquals(2, logC4.getParentIds().size());
        assertEquals(c4.getId(), logC4.getParentIds().get(0));
        assertEquals(c2.getId(), logC4.getParentIds().get(1));

        // Commit 3
        RevCommit logC3 = log.next();
        assertEquals(c4.getAuthor(), logC3.getAuthor());
        assertEquals(c4.getCommitter(), logC3.getCommitter());
        assertEquals(c4.getMessage(), logC3.getMessage());
        assertEquals(c4.getTreeId(), logC3.getTreeId());

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c3.getAuthor(), logC2.getAuthor());
        assertEquals(c3.getCommitter(), logC2.getCommitter());
        assertEquals(c3.getMessage(), logC2.getMessage());
        assertEquals(c3.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testMergeTwice() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - master - HEAD - Points 3 added
        insertAndAdd(points1);
        geogit.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogit.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        geogit.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogit.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        geogit.command(CommitOp.class).setMessage("commit for " + idP3).call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // | |
        // o | - Points 3 added
        // |/
        // o - master - HEAD - Merge commit

        Ref branch1 = geogit.command(RefParse.class).setName("branch1").call().get();
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch1.getObjectId())).call();

        exception.expect(NothingToCommitException.class);
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch1.getObjectId())).call();
    }

    @Test
    public void testMergeFastForward() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - master - HEAD - Points 1 added
        // .\
        // . o - branch1 - Points 2 added
        insertAndAdd(points1);
        final RevCommit c1 = geogit.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogit.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        final RevCommit c2 = geogit.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogit.command(CheckoutOp.class).setSource("master").call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |
        // o - master - HEAD - branch1 - Points 2 added

        Ref branch1 = geogit.command(RefParse.class).setName("branch1").call().get();
        final MergeReport mergeReport = geogit.command(MergeOp.class)
                .addCommit(Suppliers.ofInstance(branch1.getObjectId())).call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        path = appendChild(pointsName, points2.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        Iterator<RevCommit> log = geogit.command(LogOp.class).call();

        // Commit 2
        RevCommit logC2 = log.next();
        assertEquals(c2.getAuthor(), logC2.getAuthor());
        assertEquals(c2.getCommitter(), logC2.getCommitter());
        assertEquals(c2.getMessage(), logC2.getMessage());
        assertEquals(c2.getTreeId(), logC2.getTreeId());

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testMergeFastForwardSecondCase() throws Exception {
        // Create the following revision graph
        // o - master - HEAD
        // .\
        // . o - branch1 - Points 1 added

        // create branch1 and checkout
        geogit.command(UpdateRef.class).setName(Ref.HEADS_PREFIX + "branch1")
                .setNewValue(ObjectId.NULL).call();
        geogit.command(UpdateSymRef.class).setName(Ref.HEAD)
                .setNewValue(Ref.HEADS_PREFIX + "branch1").call();
        insertAndAdd(points1);
        final RevCommit c1 = geogit.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // checkout master
        geogit.command(UpdateSymRef.class).setName(Ref.HEAD)
                .setNewValue(Ref.HEADS_PREFIX + "master").call();

        // Merge branch1 into master to create the following revision graph
        // o
        // |
        // o - master - HEAD - branch1 - Points 1 added

        Ref branch1 = geogit.command(RefParse.class).setName("branch1").call().get();
        final MergeReport mergeReport = geogit.command(MergeOp.class)
                .addCommit(Suppliers.ofInstance(branch1.getObjectId())).call();

        RevTree mergedTree = repo.getTree(mergeReport.getMergeCommit().getTreeId());

        String path = appendChild(pointsName, points1.getIdentifier().getID());
        assertTrue(repo.command(FindTreeChild.class).setParent(mergedTree).setChildPath(path)
                .call().isPresent());

        Iterator<RevCommit> log = geogit.command(LogOp.class).call();

        // Commit 1
        RevCommit logC1 = log.next();
        assertEquals(c1.getAuthor(), logC1.getAuthor());
        assertEquals(c1.getCommitter(), logC1.getCommitter());
        assertEquals(c1.getMessage(), logC1.getMessage());
        assertEquals(c1.getTreeId(), logC1.getTreeId());

    }

    @Test
    public void testMergeNoCommits() throws Exception {
        exception.expect(IllegalArgumentException.class);
        geogit.command(MergeOp.class).call();
    }

    @Test
    public void testMergeNullCommit() throws Exception {
        exception.expect(IllegalArgumentException.class);
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(ObjectId.NULL)).call();
    }

    @Test
    public void testMergeConflictingBranches() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1,2 added
        // |\
        // | o - TestBranch - Points 1 modified, 2 removed, 3 added
        // |
        // o - master - HEAD - Points 1 modifiedB, 2 removed
        insertAndAdd(points1, points2);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insert(points1Modified);
        delete(points2);
        insert(points3);
        geogit.command(AddOp.class).call();
        RevCommit masterCommit = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insert(points1ModifiedB);
        delete(points2);
        geogit.command(AddOp.class).call();
        RevCommit branchCommit = geogit.command(CommitOp.class).call();
        // Now try to merge branch into master
        geogit.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogit.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                    .call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        Optional<Ref> ref = geogit.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(masterCommit.getId(), ref.get().getObjectId());
        ref = geogit.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(branch.getObjectId(), ref.get().getObjectId());

        String msg = geogit.command(ReadMergeCommitMessageOp.class).call();
        assertFalse(Strings.isNullOrEmpty(msg));

        List<Conflict> conflicts = geogit.command(ConflictsReadOp.class).call();
        assertEquals(1, conflicts.size());
        String path = NodeRef.appendChild(pointsName, idP1);
        assertEquals(conflicts.get(0).getPath(), path);
        assertEquals(conflicts.get(0).getOurs(), new RevFeatureBuilder().build(points1Modified)
                .getId());
        assertEquals(conflicts.get(0).getTheirs(), new RevFeatureBuilder().build(points1ModifiedB)
                .getId());

        // try to commit
        try {
            geogit.command(CommitOp.class).call();
            fail();
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Cannot run operation while merge conflicts exist.");
        }

        // solve, and commit
        Feature points1Merged = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        insert(points1Merged);
        geogit.command(AddOp.class).call();
        RevCommit commit = geogit.command(CommitOp.class).call();
        assertTrue(commit.getMessage().contains(idP1));
        List<ObjectId> parents = commit.getParentIds();
        assertEquals(2, parents.size());
        assertEquals(masterCommit.getId(), parents.get(0));
        assertEquals(branchCommit.getId(), parents.get(1));
        Optional<RevFeature> revFeature = geogit.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(new RevFeatureBuilder().build(points1Merged), revFeature.get());
        path = NodeRef.appendChild(pointsName, idP2);
        revFeature = geogit.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        path = NodeRef.appendChild(pointsName, idP3);
        revFeature = geogit.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());

        ref = geogit.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());

    }

    @Test
    public void testConflictingOctopusMerge() throws Exception {
        // Create the following revision graph
        // . o
        // . |
        // . o - Points 1 added
        // . |\
        // . | o - branch1 - Points 1 modified
        // . |
        // . o - Points 2 added
        // ./|
        // o | - branch2 - Point 1 modified B
        // . |
        // . o - master - HEAD - Point 1 modified C
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("branch1").call();
        insertAndAdd(points2);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("branch2").call();
        Feature points1ModifiedC = feature(pointsType, idP1, "StringProp1_4", new Integer(3000),
                "POINT(1 3)");
        insertAndAdd(points1ModifiedC);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch1").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch2").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 2)");
        insertAndAdd(points1ModifiedB);
        geogit.command(CommitOp.class).call();

        // Now try to merge all branches into master
        geogit.command(CheckoutOp.class).setSource("master").call();
        Ref branch1 = geogit.command(RefParse.class).setName("branch1").call().get();
        Ref branch2 = geogit.command(RefParse.class).setName("branch2").call().get();
        MergeOp mergeOp = geogit.command(MergeOp.class);
        mergeOp.addCommit(Suppliers.ofInstance(branch1.getObjectId()));
        mergeOp.addCommit(Suppliers.ofInstance(branch2.getObjectId()));
        try {
            mergeOp.call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains(
                    "Cannot merge more than two commits when conflicts exist"));
        }

    }

    @Test
    public void testMergeConflictingBranchesOurs() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified and points 2 added
        // |
        // o - master - HEAD - Points 1 modifiedB
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogit.command(CommitOp.class).call();

        geogit.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogit.command(RefParse.class).setName("TestBranch").call().get();
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                .setOurs(true).call();

        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<RevFeature> revFeature = geogit.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(new RevFeatureBuilder().build(points1Modified), revFeature.get());
        path = NodeRef.appendChild(pointsName, idP2);
        revFeature = geogit.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(new RevFeatureBuilder().build(points2), revFeature.get());
    }

    @Test
    public void testMergeConflictingBranchesTheirs() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified
        // |
        // o - master - HEAD - Points 1 modifiedB
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        geogit.command(CommitOp.class).call();

        geogit.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogit.command(RefParse.class).setName("TestBranch").call().get();
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                .setTheirs(true).call();

        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<RevFeature> revFeature = geogit.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(new RevFeatureBuilder().build(points1ModifiedB), revFeature.get());
    }

    @Test
    public void testOursAndTheirs() throws Exception {
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogit.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                    .setTheirs(true).setOurs(true).call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testNoCommitMerge() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - branch1 - Points 2 added
        // |
        // o - master - HEAD - Points 3 added
        insertAndAdd(points1);
        geogit.command(CommitOp.class).setMessage("commit for " + idP1).call();

        // create branch1 and checkout
        geogit.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();
        insertAndAdd(points2);
        geogit.command(CommitOp.class).setMessage("commit for " + idP2).call();

        // checkout master
        geogit.command(CheckoutOp.class).setSource("master").call();
        insertAndAdd(points3);
        RevCommit lastCommit = geogit.command(CommitOp.class).setMessage("commit for " + idP3)
                .call();
        Ref branch = geogit.command(RefParse.class).setName("branch1").call().get();

        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                .setNoCommit(true).call();

        String path = NodeRef.appendChild(pointsName, idP2);
        Optional<RevFeature> revFeature = geogit.command(RevObjectParse.class)
                .setRefSpec(Ref.STAGE_HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(new RevFeatureBuilder().build(points2), revFeature.get());
        revFeature = geogit.command(RevObjectParse.class).setRefSpec(Ref.HEAD + ":" + path)
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        revFeature = geogit.command(RevObjectParse.class).setRefSpec(Ref.WORK_HEAD + ":" + path)
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(new RevFeatureBuilder().build(points2), revFeature.get());

        Optional<Ref> ref = geogit.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(lastCommit.getId(), ref.get().getObjectId());
        ref = geogit.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(branch.getObjectId(), ref.get().getObjectId());

    }

    @Test
    public void testConflictingMergeInterceptor() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified and points 2 added
        // |
        // o - master - HEAD - Points 1 modifiedB
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogit.command(CommitOp.class).call();

        geogit.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogit.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                    .call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        try {
            geogit.command(PullOp.class).call();
            fail();
        } catch (IllegalStateException e) {
            assertEquals(e.getMessage(), "Cannot run operation while merge conflicts exist.");
        }

    }

    @Test
    public void testMergeWithFeatureMerge() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified and points 2 added
        // |
        // o - master - HEAD - Points 1 modifiedB
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("TestBranch").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("TestBranch").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_1", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogit.command(CommitOp.class).call();

        geogit.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogit.command(RefParse.class).setName("TestBranch").call().get();
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId())).call();

        String path = appendChild(pointsName, points1.getIdentifier().getID());

        Optional<RevFeature> feature = repo.command(RevObjectParse.class)
                .setRefSpec(/*
                             * mergeCommit. getId ().toString () + ":" +
                             */"WORK_HEAD" + ":" + path).call(RevFeature.class);
        assertTrue(feature.isPresent());

        Feature mergedFeature = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        RevFeature expected = new RevFeatureBuilder().build(mergedFeature);
        assertEquals(expected, feature.get());

    }

    @Test
    public void testMergeTwoBranchesWithNewFeatureType() throws Exception {
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("branch1").call();
        insertAndAdd(lines1);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch1").call();
        insertAndAdd(poly1);
        RevCommit commit2 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("master").call();
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(commit2.getId())).call();

        Optional<NodeRef> ref = geogit.command(FindTreeChild.class).setChildPath(polyName).call();

        assertTrue(ref.isPresent());
        assertFalse(ref.get().getMetadataId().equals(ObjectId.NULL));
    }

    @Test
    public void testOctopusMerge() throws Exception {
        insertAndAdd(points1);
        RevCommit initialCommit = geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("branch1").call();
        geogit.command(BranchCreateOp.class).setName("branch2").call();
        geogit.command(BranchCreateOp.class).setName("branch3").call();
        geogit.command(BranchCreateOp.class).setName("branch4").call();
        geogit.command(BranchCreateOp.class).setName("branch5").call();
        geogit.command(BranchCreateOp.class).setName("branch6").call();
        geogit.command(CheckoutOp.class).setSource("branch1").call();
        ObjectId points2Id = insertAndAdd(points2);
        RevCommit branch1 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch2").call();
        ObjectId points3Id = insertAndAdd(points3);
        RevCommit branch2 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch3").call();
        ObjectId lines1Id = insertAndAdd(lines1);
        RevCommit branch3 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch4").call();
        ObjectId lines2Id = insertAndAdd(lines2);
        RevCommit branch4 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch5").call();
        ObjectId lines3Id = insertAndAdd(lines3);
        RevCommit branch5 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch6").call();
        ObjectId points1Id = insertAndAdd(points1_modified);
        RevCommit branch6 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("master").call();
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch1.getId()))
                .addCommit(Suppliers.ofInstance(branch2.getId()))
                .addCommit(Suppliers.ofInstance(branch3.getId()))
                .addCommit(Suppliers.ofInstance(branch4.getId()))
                .addCommit(Suppliers.ofInstance(branch5.getId()))
                .addCommit(Suppliers.ofInstance(branch6.getId())).call();

        Optional<NodeRef> ref = geogit.command(FindTreeChild.class)
                .setChildPath(pointsName + "/" + idP1).call();
        assertTrue(ref.isPresent());
        assertEquals(points1Id, ref.get().getNode().getObjectId());
        ref = geogit.command(FindTreeChild.class).setChildPath(pointsName + "/" + idP2).call();
        assertTrue(ref.isPresent());
        assertEquals(points2Id, ref.get().getNode().getObjectId());
        ref = geogit.command(FindTreeChild.class).setChildPath(pointsName + "/" + idP3).call();
        assertTrue(ref.isPresent());
        assertEquals(points3Id, ref.get().getNode().getObjectId());
        ref = geogit.command(FindTreeChild.class).setChildPath(linesName + "/" + idL1).call();
        assertTrue(ref.isPresent());
        assertEquals(lines1Id, ref.get().getNode().getObjectId());
        ref = geogit.command(FindTreeChild.class).setChildPath(linesName + "/" + idL2).call();
        assertTrue(ref.isPresent());
        assertEquals(lines2Id, ref.get().getNode().getObjectId());
        ref = geogit.command(FindTreeChild.class).setChildPath(linesName + "/" + idL3).call();
        assertTrue(ref.isPresent());
        assertEquals(lines3Id, ref.get().getNode().getObjectId());

        Iterator<RevCommit> log = geogit.command(LogOp.class).setFirstParentOnly(true).call();

        // MergeCommit
        RevCommit logMerge = log.next();
        assertEquals(7, logMerge.getParentIds().size());

        // Initial Commit
        RevCommit initial = log.next();
        assertEquals(initialCommit.getMessage(), initial.getMessage());
        assertEquals(initialCommit.getCommitter().getName(), initial.getCommitter().getName());
        assertEquals(initialCommit.getCommitter().getEmail(), initial.getCommitter().getEmail());
        assertEquals(initialCommit.getAuthor().getTimeZoneOffset(), initial.getAuthor()
                .getTimeZoneOffset());
        assertEquals(initialCommit.getCommitter().getTimeZoneOffset(), initial.getCommitter()
                .getTimeZoneOffset());
        assertEquals(initialCommit.getTreeId(), initial.getTreeId());
        assertEquals(initialCommit.getId(), initial.getId());

        assertFalse(log.hasNext());
    }

    @Test
    public void testOctopusMergeWithAutomerge() throws Exception {
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("branch1").call();
        geogit.command(BranchCreateOp.class).setName("branch2").call();
        geogit.command(BranchCreateOp.class).setName("branch3").call();
        geogit.command(BranchCreateOp.class).setName("branch4").call();
        geogit.command(BranchCreateOp.class).setName("branch5").call();
        geogit.command(BranchCreateOp.class).setName("branch6").call();
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(points1Modified);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch1").call();
        insertAndAdd(points2);
        RevCommit branch1 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch2").call();
        insertAndAdd(points3);
        RevCommit branch2 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch3").call();
        insertAndAdd(lines1);
        RevCommit branch3 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch4").call();
        insertAndAdd(lines2);
        RevCommit branch4 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch5").call();
        insertAndAdd(lines3);
        RevCommit branch5 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch6").call();
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1ModifiedB);
        RevCommit branch6 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("master").call();
        MergeOp mergeOp = geogit.command(MergeOp.class)
                .addCommit(Suppliers.ofInstance(branch1.getId()))
                .addCommit(Suppliers.ofInstance(branch2.getId()))
                .addCommit(Suppliers.ofInstance(branch3.getId()))
                .addCommit(Suppliers.ofInstance(branch4.getId()))
                .addCommit(Suppliers.ofInstance(branch5.getId()))
                .addCommit(Suppliers.ofInstance(branch6.getId()));
        try {
            mergeOp.call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains(
                    "Cannot merge more than two commits when conflicts exist"));
        }
    }

    @Test
    public void testOctopusMergeSameFeatureChanges() throws Exception {
        insertAndAdd(points1);
        geogit.command(CommitOp.class).call();
        geogit.command(BranchCreateOp.class).setName("branch1").call();
        geogit.command(BranchCreateOp.class).setName("branch2").call();
        geogit.command(BranchCreateOp.class).setName("branch3").call();
        insertAndAdd(points1_modified);
        geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch1").call();
        insertAndAdd(points2);
        RevCommit branch1 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch2").call();
        insertAndAdd(points3);
        RevCommit branch2 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("branch3").call();
        insertAndAdd(points1_modified);
        RevCommit branch3 = geogit.command(CommitOp.class).call();
        geogit.command(CheckoutOp.class).setSource("master").call();
        geogit.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch1.getId()))
                .addCommit(Suppliers.ofInstance(branch2.getId()))
                .addCommit(Suppliers.ofInstance(branch3.getId())).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        Optional<RevFeature> revFeature = geogit.command(RevObjectParse.class)
                .setRefSpec(Ref.HEAD + ":" + path).call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        assertEquals(new RevFeatureBuilder().build(points1_modified), revFeature.get());
    }

}
