package cs7492Project3;

public class myMCCalcThreads extends base_MCCalcThreads {
	/**
	 * displacement in info array for current data - use this when 
	 * putting multiple data fields (like U chem and V chem concentration) 
	 * in the same primitive value in MC data array, used as mask
	 */
	public int disp;

	public myMCCalcThreads(base_MarchingCubes _MC, myMCCube[] _grid, int _stIdx, int _gxM1,int _gyM1) {
		super(_MC, _grid, _stIdx, _gxM1, _gyM1);
	}

	@Override
	protected final int getModIsoLevel() {	return (int)(isolevel*256.0f)<<disp;}

	@Override
	protected int getDataMask() {		return 0xFF << disp;}
	
	public void setCustomSimVals(int _disp) {	disp=_disp;}
	

}//class myMCCalcThreads
