package cs7492Project3;

import java.util.concurrent.ConcurrentSkipListMap;

public class buildMCData implements Runnable{
	public base_MarchingCubes MC;
	public int numVerts;
	public buildMCData(base_MarchingCubes _mc, int _numVerts){
		MC = _mc;
		numVerts = _numVerts;
		MC.vertList = new ConcurrentSkipListMap<Integer, myMCVert> ();
		MC.usedVertList = new ConcurrentSkipListMap<Integer, myMCVert> ();
	}
	@Override
	public void run() {
		for(int idx = 0; idx < numVerts; ++idx){
			MC.vertList.put(idx, new myMCVert());
		}
		
	}
	
}//buildMCData