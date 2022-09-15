package cs7492Project3;

public class myMCCalcThreads extends base_MCCalcThreads {
	/**
	 * displacement in info array for current data - use this when 
	 * putting multiple data fields (like U chem and V chem concentration) 
	 * in the same primitive value in MC data array, used as mask
	 */
	private int disp;
	private int mask;

	public myMCCalcThreads(base_MarchingCubes _MC, myMCCube[] _grid, int _stIdx, int _gxM1,int _gyM1) {
		super(_MC, _grid, _stIdx, _gxM1, _gyM1);
	}
	
	/**
	 * Build modified iso level as integer value to correspond to channel value from shader
	 */
	@Override
	protected final int getModIsoLevel() {	return (int)(isolevel*256.0f)<<disp;}

	/**
	 * This will mask the data from the shader to provide the appropriate channel's data for the U or V chem being displayed
	 */
	@Override
	protected int getDataMask() {		return mask;}
	
	/**
	 * Set disp value based on what value is being 
	 * used from shader - this will be used to bitshift values to align with appropriate channel from shader
	 * @param _disp
	 */
	public void setCustomSimVals(int _disp) {	
		disp = _disp;
		mask = 0xFF << disp;
	}
	

}//class myMCCalcThreads
