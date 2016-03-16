package com.ensoftcorp.open.cg.utils;

import com.ensoftcorp.atlas.core.indexing.IIndexListener;

public class CodeMapChangeListener implements IIndexListener {

	private boolean indexHasChanged = false;
	private boolean indexIsChanging = false;
	
	public boolean hasIndexChanged(){
		return !indexIsChanging && indexHasChanged;
	}
	
	public void reset(){
		indexHasChanged = false;
	}
	
	@Override
	public void indexOperationCancelled(IndexOperation io) {}

	@Override
	public void indexOperationError(IndexOperation io, Throwable t) {}

	@Override
	public void indexOperationStarted(IndexOperation io) {
		indexHasChanged = false;
		indexIsChanging = true;	
	}

	@Override
	public void indexOperationComplete(IndexOperation io) {
		indexIsChanging = false;
		indexHasChanged = true;
	}

	@Override
	public void indexOperationScheduled(IndexOperation io) {}
};
