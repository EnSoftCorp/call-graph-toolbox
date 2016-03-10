package com.ensoftcorp.open.cg.utils;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class StandardQueries {
	
	/**
	 * Returns the containing method of a given Q or empty if one is not found
	 * @param nodes
	 * @return
	 */
	public static Q getContainingMethods(Q nodes) {
		AtlasSet<GraphElement> nodeSet = nodes.eval().nodes();
		AtlasSet<GraphElement> containingMethods = new AtlasHashSet<GraphElement>();
		for (GraphElement currentNode : nodeSet) {
			GraphElement method = getContainingMethod(currentNode);
			if (method != null)
				containingMethods.add(method);
		}
		return Common.toQ(Common.toGraph(containingMethods));
	}
	
	/**
	 * Returns the containing method of a given graph element or null if one is not found
	 * NOTE: the enclosing method may be two steps or more above
	 * @param ge
	 * @return
	 */
	public static GraphElement getContainingMethod(GraphElement ge) {
		return getContainingNode(ge, XCSG.Method);
	}
	
	/**
	 * Find the next immediate containing node with the given tag.
	 * 
	 * @param node
	 * @param containingTag
	 * @return the next immediate containing node, or null if none exists; never
	 *         returns the given node
	 */
	private static GraphElement getContainingNode(GraphElement node, String containingTag) {
		if(node == null) {
			return null;
		}

		while(true) {
			GraphElement containsEdge = Graph.U.edges(node, NodeDirection.IN).taggedWithAll(XCSG.Contains).getFirst();
			if (containsEdge == null) {
				return null;
			}
			GraphElement parent = containsEdge.getNode(EdgeDirection.FROM);
			if (parent.taggedWith(containingTag)) {
				return parent;
			}
			node = parent;
		}

	}
	
}
