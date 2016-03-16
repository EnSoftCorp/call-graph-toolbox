package com.ensoftcorp.open.cg.utils;

import com.ensoftcorp.atlas.core.indexing.IIndexListener;

public class CodeMapChangeListener implements IIndexListener {

	private boolean indexHasChanged = false;
	
	public boolean hasIndexChanged(){
		return indexHasChanged;
	}
	
	public void reset(){
		indexHasChanged = false;
	}
	
	@Override
	public void indexOperationCancelled(IndexOperation io) {}

	@Override
	public void indexOperationError(IndexOperation io, Throwable t) {}

	@Override
	public void indexOperationStarted(IndexOperation io) {}

	@Override
	public void indexOperationComplete(IndexOperation io) {
		indexHasChanged = true;
	}

	@Override
	public void indexOperationScheduled(IndexOperation io) {}
};
