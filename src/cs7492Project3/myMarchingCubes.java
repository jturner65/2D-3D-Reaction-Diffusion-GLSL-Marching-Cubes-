package cs7492Project3;

import java.util.concurrent.ExecutorService;

public class myMarchingCubes extends base_MarchingCubes {
	public cs7492Proj3 pa;

	public myMarchingCubes(cs7492Proj3 _p, ExecutorService _th_exec, int _cs, int _gx, int _gy, int _gz) {
		super(_th_exec, _cs, _gx, _gy, _gz);
		pa=_p;
		
	}
	@Override
	public final boolean doUseVertNorms() {
		return pa.flags[pa.useVertNorms];
	}
	@Override
	public final float getIsoLevel() {
		return pa.flags[pa.dispChemU] ?  pa.guiObjs[pa.gIDX_isoLvl].valAsFloat() : 1.0f - pa.guiObjs[pa.gIDX_isoLvl].valAsFloat();
	}
	
	@Override
	protected final void setColorBasedOnState() {
		pa.setColorValFill(pa.triColors[pa.RD.dispChem][pa.currDispType]);	
	}
	@Override
	protected void _setCustomSimValsInCallable(base_MCCalcThreads c) {
		((myMCCalcThreads)c).setCustomSimVals((pa.flags[pa.dispChemU]) ? 16 : 8);
		
	}
	@Override
	protected void _configureDataForMC(int[] datAra) {
		int idx = 0, j_kgxgy;
		//isolevel =  p.flags[p.dispChemU] ?  p.guiObjs[p.gIDX_isoLvl].valAsFloat() : 1.0f - p.guiObjs[p.gIDX_isoLvl].valAsFloat();
	
		for (int j = 0; j < gy; ++j){for (int k = 0; k < numCubes; k+=gxgy){j_kgxgy = j + k; for (int i = 0; i < gx; ++i)  {
			intData[j_kgxgy]= datAra[idx++];
			j_kgxgy+=gx;
		}}}	
	}
	@Override
	protected base_MCCalcThreads buildMCCalcThread(int stIdx) {
		return new myMCCalcThreads(this, grid, stIdx, gx - 1, gy - 1);	//process 2d grid for each thread;
	}


}//myMarchingCubes
