package com.ensoftcorp.open.cg.smart;

import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.markup.MarkupFromH;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.FrontierStyledResult;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.scripts.selections.FilteringAtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.scripts.selections.IResizableScript;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;

public abstract class CallGraphSmartView extends FilteringAtlasSmartViewScript implements IResizableScript {

	/**
	 * Returns a call graph for the given analysis
	 * @return
	 */
	protected abstract Q getCallGraph();
	
	@Override
	protected String[] getSupportedNodeTags() {
		return new String[]{XCSG.Method};
	}

	@Override
	protected String[] getSupportedEdgeTags() {
		return NOTHING;
	}

	@Override
	public FrontierStyledResult evaluate(IAtlasSelectionEvent event, int reverse, int forward) {
		Q filteredSelection = filter(event.getSelection());

		Q cg = getCallGraph();
		Highlighter h = new Highlighter();
		
		Q completeResult = cg.forward(filteredSelection).union(cg.reverse(filteredSelection));
				
		// compute what to show for current steps
		Q f = filteredSelection.forwardStepOn(completeResult, forward);
		Q r = filteredSelection.reverseStepOn(completeResult, reverse);
		Q result = f.union(r);
		
		// compute what is on the frontier
		Q frontierForward = filteredSelection.forwardStepOn(completeResult, forward+1);
		frontierForward = frontierForward.retainEdges().differenceEdges(result);
		
		Q frontierReverse = filteredSelection.reverseStepOn(completeResult, reverse+1);
		frontierReverse = frontierReverse.retainEdges().differenceEdges(result);

		return new com.ensoftcorp.atlas.core.script.FrontierStyledResult(result, frontierReverse, frontierForward, new MarkupFromH(h));
	}

	@Override
	public int getDefaultStepBottom() {
		return 1;
	}

	@Override
	public int getDefaultStepTop() {
		return 1;
	}

	@Override
	protected StyledResult selectionChanged(IAtlasSelectionEvent input, Q filteredSelection) {
		return null;
	}

}
