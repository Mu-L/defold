package com.dynamo.cr.parted.operations;

import com.dynamo.cr.parted.nodes.EmitterNode;
import com.dynamo.cr.parted.nodes.ParticleFXNode;
import com.dynamo.cr.sceneed.core.ISceneView.IPresenterContext;
import com.dynamo.cr.sceneed.core.Node;
import com.dynamo.cr.sceneed.core.NodeUtil;
import com.dynamo.cr.sceneed.core.operations.AddChildrenOperation;

public class AddEmitterOperation extends AddChildrenOperation {

    public AddEmitterOperation(ParticleFXNode node, EmitterNode emitter, IPresenterContext presenterContext) {
        super("Add Emitter", node, emitter, presenterContext);
        String id = emitter.getId();
        id = NodeUtil.getUniqueId(node, id, new NodeUtil.IdFetcher<Node>() {
            @Override
            public String getId(Node child) {
                return ((EmitterNode)child).getId();
            }
        });
        emitter.setId(id);
    }

}