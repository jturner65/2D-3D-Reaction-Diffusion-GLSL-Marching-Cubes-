package cs7492Project3;

import java.nio.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Future;

import cs7492Project3.base_MCCalcThreads;
import processing.core.PConstants;
import processing.opengl.*;

public abstract class base_MarchingCubes {

	public PGL pgl;
	public PShader sh;

	public int gx, gy, gz, 
		vgx, vgy, vgz,
		numCubes, gxgy, vgxgy;
	
	public FloatBuffer dataBuf;

	public float minVal, maxVal;
	
	public List<myMCTri> triList = Collections.synchronizedList(new ArrayList<myMCTri>());

	protected myMCCube[] grid;				//3d grid holding marching cubes
	
	public List<Future<Boolean>> callMCCalcFutures;
	public List<base_MCCalcThreads> callMCCalcs;
//	//structure to hold mid-edge vertices
	public ConcurrentSkipListMap<Integer, myMCVert> vertList, usedVertList;

	// draw data
	public myVectorf dataStep;
	public ByteBuffer buf;
	public int cellSize;			//how big are the cells in the resultant grid
	//holding ara
	public int[] intData;
	//executor service to launch threads
	private ExecutorService th_exec;
	
	public base_MarchingCubes(ExecutorService _th_exec, int _cs, int _gx, int _gy, int _gz) {
		th_exec = _th_exec;
		
		callMCCalcs = new ArrayList<base_MCCalcThreads>();
		callMCCalcFutures = new ArrayList<Future<Boolean>>(); 		
		setDimAndRes( _cs, _gx, _gy, _gz);
	}
	//setup dimensions of resultant space, and dimensions of data array
	//sx, sy and sz are pixel dimensions,
	//x,y,z are cell dimensions -> # of grid cells in each dimension
	public final void setDimAndRes(int _cs, int _x, int _y, int _z) {
		cellSize = _cs;
		gx = (int)(_x/cellSize);gy = (int)(_y/cellSize);gz = (int)(_z/cellSize);
		gxgy = gx * gy;
		vgx = (gx*2)-1;
		vgy = (gy*2)-1;
		vgz = (gz*2)-1;
		numCubes = gx * gy * gz;

		triList = new ArrayList<myMCTri>();		
		dataStep = new myVectorf(cellSize, cellSize, cellSize);
		myMCCube.gx = gx;
		myMCCube.gxgy = gxgy;
		grid = new myMCCube[numCubes];			//is a global grid faster?
		intData = new int[numCubes];
				
		for (int k = 0; k < gz - 1; ++k) {
			int stIdx = k * gxgy, idx = stIdx;			
			for (int j = 0; j < gy - 1; ++j) {		
				for (int i = 0; i < gx - 1; ++i) {									
					grid[idx] = new myMCCube(i, j, k,  idx, dataStep);					
					idx++;
				}
			}
			callMCCalcs.add(buildMCCalcThread(stIdx));	//process 2d grid for each thread
		}
		th_exec.execute(new buildMCData(this, vgx * vgy * vgz));	
	}//setDimAndRes
	
	protected abstract base_MCCalcThreads buildMCCalcThread(int stIdx);
	
	public final void synchSetVertList(int idx, myPointf _loc){
		synchronized(usedVertList){
			myMCVert tmp = vertList.get(idx);
			tmp.setInitVert(_loc);
			usedVertList.put(idx, tmp);
		}
	}
	//idxing into 3d grid should be for z -> for y -> for x (inner)
	public final int IX(int x, int y, int z){return (x + (y * gy) + (z * gxgy));}
	
	public abstract boolean doUseVertNorms();	
	public abstract float getIsoLevel();
	
	public void copyDataAraToMCLclData(int[] clrPxl){
		_configureDataForMC(clrPxl);
		//intData = clrPxl;
		
		//int callIdx = 0;
		
//		for(myMCVert v : usedVertList.values()){v.clearVert();}
//		usedVertList.clear();
		for(base_MCCalcThreads c : callMCCalcs) {
			c.setSimVals(doUseVertNorms(), getIsoLevel());
			_setCustomSimValsInCallable(c);
		}
		try {callMCCalcFutures = th_exec.invokeAll(callMCCalcs);for(Future<Boolean> f: callMCCalcFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
	}
	
	/**
	 * set simulation parameters in callable for MC derivation
	 * @param c
	 */
	protected abstract void _setCustomSimValsInCallable(base_MCCalcThreads c);
	
	protected abstract void _configureDataForMC(int[] datAra);
	protected abstract void setColorBasedOnState();
	
	
	public void draw(cs7492Proj3 pa) {
		setColorBasedOnState();
		pa.noStroke();
		pa.beginShape(PConstants.TRIANGLES);		
		synchronized(triList) {
			Iterator<myMCTri> i = triList.iterator(); // Must be in synchronized block		
	        if (doUseVertNorms()){
	        	while (i.hasNext()){    	i.next().drawMeVerts(pa);   }
	        } else {
	        	while (i.hasNext()){    	i.next().drawMe(pa);   }
	        }
		}
		pa.endShape();	
		triList = new ArrayList<myMCTri>();
	}

	public String toString() {
		String res  ="";// "tris: " + ntri + " volume: " + gx + " " + gy+ " " + gz + " cells: " + numCubes + " iso: " + isolevel;
		return res;
	}
	
	
}//class def

