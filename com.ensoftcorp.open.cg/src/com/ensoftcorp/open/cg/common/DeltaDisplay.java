package com.ensoftcorp.open.cg.common;

import java.awt.Color;

import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.open.commons.utilities.DisplayUtils;

public class DeltaDisplay {

	/**
	 * Displays nodes/edges that were added from q1 to q2 in BLUE
	 * and displays the nodes/edges that were removed from q1 to
	 * q2 in RED.
	 * 
	 * @param q1
	 * @param q2
	 */
	public static void showDelta(Q q1, Q q2, boolean extend, String title){
		Q same = q2.intersection(q1);
		Q added = q2.difference(same);
		Q removed = q1.difference(q2);
		Highlighter h = new Highlighter();
		h.highlight(added, Color.BLUE);
		h.highlight(removed, Color.RED);
		DisplayUtils.show(q1.union(q2), h, extend, title);
	}
	
	/**
	 * Displays nodes/edges that were removed from q1 to q2
	 * 
	 * @param q1
	 * @param q2
	 */
	public static void showRemoved(Q q1, Q q2, boolean extend, String title){
		Q removed = q1.difference(q2);
		DisplayUtils.show(removed, null, extend, title);
	}
	
	/**
	 * Displays nodes/edges that were added from q1 to q2
	 * 
	 * @param q1
	 * @param q2
	 */
	public static void showAdded(Q q1, Q q2, boolean extend, String title){
		Q same = q2.intersection(q1);
		Q added = q2.difference(same);
		DisplayUtils.show(added, null, extend, title);
	}
	
}
