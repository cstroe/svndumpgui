package com.github.cstroe.svndumpgui.internal.transform;

import com.github.cstroe.svndumpgui.api.SvnDumpMutator;
import com.github.cstroe.svndumpgui.api.SvnNode;
import com.github.cstroe.svndumpgui.api.SvnRevision;

public class NodeAdd implements SvnDumpMutator {
    private final int targetRevision;
    private final SvnNode node;

    private boolean nodeAdded = false;

    public NodeAdd(int targetRevision, SvnNode node) {
        this.targetRevision = targetRevision;
        this.node = node;
    }

    @Override
    public void mutateRevision(SvnRevision revision) {
        if(revision.getNumber() == targetRevision && !nodeAdded) {
            revision.getNodes().add(node);
            nodeAdded = true;
        }
    }

    @Override
    public void finish() {
        if(!nodeAdded) {
            throw new IllegalArgumentException("Could not find revision " + targetRevision);
        }
        nodeAdded = false;
    }
}
