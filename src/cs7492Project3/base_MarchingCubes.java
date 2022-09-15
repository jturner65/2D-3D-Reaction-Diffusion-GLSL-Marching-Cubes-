package cs7492Project3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
//import java.util.Map.Entry;
import java.util.concurrent.*;

import processing.core.PConstants;
import processing.opengl.*;

public abstract class base_MarchingCubes {

	public PGL pgl;
	public PShader sh;

	public int gx, gy, gz, 
		numDataVals, gxgy, vgxgy;
	
	/**
	 * List of triangles making up the iso surface
	 */
	public List<myMCTri> triList = Collections.synchronizedList(new ArrayList<myMCTri>());
	
	/**
	 * 3d grid holding marching cubes
	 */
	protected myMCCube[] grid;
	
	/**
	 * These hold the Threading callables for used MC Iso surface construction
	 */
	public List<Future<Boolean>> callMCCalcFutures;
	public List<base_MCCalcThreads> callMCCalcs;

	/**
	 * structure to hold the per-edge iso surface mesh verts
	 */
	private HashMap<Tuple<Integer,Integer>, myMCVert> usedVertList;

	/**
	 * This holds the data ara from the shader solver
	 */
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
	//_x, _y and _z are pixel dimensions,
	//gx,gy,gz are cell dimensions -> # of grid cells in each dimension
	private final void setDimAndRes(int _cellSize, int _x, int _y, int _z) {
		gx = (int)(_x/_cellSize);gy = (int)(_y/_cellSize);gz = (int)(_z/_cellSize);
		gxgy = gx * gy;
		int gxm1gym1 = (gx-1) * (gy-1);
		int numCubes = gxm1gym1 * (gz-1);
		numDataVals = gx * gy * gz;

		triList = new ArrayList<myMCTri>();		
		myPointf dataStep = new myPointf(_cellSize, _cellSize, _cellSize);
		myMCCube.gx = gx;
		myMCCube.gxgy = gxgy;
		grid = new myMCCube[numCubes];			//is a global grid faster?
		//shader array - needs to be 1 bigger in each dimension than grid
		intData = new int[numDataVals];
		//build grid, processing will occur in slices of const k per thread
		int idx;
		for (int k = 0; k < gz-1; ++k) {
			int stIdx = k * gxm1gym1;
			idx = stIdx;
			for (int j = 0; j < gy-1; ++j) {		
				for (int i = 0; i < gx-1; ++i) {									
					grid[idx] = new myMCCube(i, j, k, idx, dataStep);					
					idx++;
				}
			}
			callMCCalcs.add(buildMCCalcThread(stIdx));	//process 2d grid for each thread, slice in k direction
		}
		usedVertList = new HashMap<Tuple<Integer,Integer>, myMCVert> ();
//		for (myMCCube cube : grid) {
//			for (Tuple<Integer,Integer> edge : cube.vIdx) {
//				usedVertList.put(edge, new myMCVert());
//			}
//		}
		System.out.println("Total # of grid cells made :"+grid.length);
		//testGrid();
	}//setDimAndRes
	
	private void testGrid() {
		HashMap<Tuple<Integer,Integer>, HashMap<Integer,Integer>> countGridOfVIDX = new HashMap<Tuple<Integer,Integer>, HashMap<Integer,Integer>>(); 
		int idx = -1;
		int gcount = 0;
		for (myMCCube cube : grid) {
			++idx;
			//A null cube means we did not build the grid correctly
			if (cube==null) {
				System.out.println("!!! " + (++gcount) +"th Null cube in grid at idx = "+ idx);
				continue;
			}			
			for(int iter=0;iter<cube.edgeGlblVertIDXs.length;++iter) {
				Tuple<Integer,Integer> glblIdx = cube.edgeGlblVertIDXs[iter];
				HashMap<Integer,Integer> glblIdxList = countGridOfVIDX.get(glblIdx);
				if (glblIdxList == null) {	
					glblIdxList = new HashMap<Integer,Integer>();
					countGridOfVIDX.put(glblIdx, glblIdxList);
				}
				glblIdxList.put(cube.idx, iter);
			}
		}
				
//		// want a per-count map of per raw idx map to all the internal cube vIdx idxs
//		HashMap<Integer, HashMap<Tuple<Integer,Integer>,HashMap<Integer,Integer>>> counts = new HashMap<Integer, HashMap<Tuple<Integer,Integer>,HashMap<Integer,Integer>>>();
//		System.out.println("vIdx vals with different counts :");
//		Integer countKey;
//		Tuple<Integer,Integer>glblIdxVal;
//		Integer lclIdxCount;
//		HashMap<Integer,Integer> perCubeMapOfLclIDX;
//		Integer cubeIdx, lclCubeIdx;
//		for(Entry<Tuple<Integer,Integer>, HashMap<Integer,Integer>> pair : countGridOfVIDX.entrySet()) {
//			//finding the number of values referencing this glblIdxVal
//			glblIdxVal = pair.getKey();
//			//per cube IDX map of lcl idxs - only one idx per cube
//			perCubeMapOfLclIDX = pair.getValue();
//			//# of times glblIdxVal is present across all cubes
//			countKey = perCubeMapOfLclIDX.size();
//			
//			// per count map of per glblIdx 
//			HashMap<Tuple<Integer,Integer>,HashMap<Integer,Integer>> perCountMap = counts.get(countKey);			
//			if (perCountMap == null) { 
//				perCountMap = new HashMap<Tuple<Integer,Integer>,HashMap<Integer,Integer>>(); 
//				counts.put(countKey, perCountMap);
//			}
//			// per glblIdx map of cubeIDX/lclIDX
//			HashMap<Integer,Integer> perGlblIdxMap = perCountMap.get(glblIdxVal);
//			if (perGlblIdxMap == null) { 
//				perGlblIdxMap = new HashMap<Integer,Integer>(); 
//				perCountMap.put(glblIdxVal, perGlblIdxMap);
//			}
//			// build per lcl index count of counts - see if any lcl idxs error out more often
//			for (Entry<Integer,Integer> perCubeLclIdx : perCubeMapOfLclIDX.entrySet()) {
//				cubeIdx = perCubeLclIdx.getKey();
//				lclCubeIdx = perCubeLclIdx.getValue();
//				lclIdxCount = perGlblIdxMap.get(lclCubeIdx);
//				if (lclIdxCount == null) {
//					lclIdxCount = 0;
//				}
//				perGlblIdxMap.put(lclCubeIdx, ++lclIdxCount);	
//			}			
//		}
//		Integer count, lclIdx, lclCount;
//		Tuple<Integer,Integer> glblIdx;
//		HashMap<Tuple<Integer,Integer>, HashMap<Integer,Integer>> idxValsMap;
//		//Key is lcl idx, val is count of that lcl idx across all grids
//		HashMap<Integer,Integer> countPerLclIdxAtTTLCount = new HashMap<Integer,Integer>();
//		HashMap<Integer,Integer> lclIdxCountPerGlblIdx;
//		for(Entry<Integer, HashMap<Tuple<Integer,Integer>, HashMap<Integer,Integer>>> countPair : counts.entrySet()) {
//			count = countPair.getKey();
//			//per glbl idx
//			idxValsMap = countPair.getValue();
//			System.out.println("Having count of "+ count + " there were "+ idxValsMap.size() +" unique global idxs present.");
//			//derive map of all local idxs and how many have contribute to count sharing
//			countPerLclIdxAtTTLCount.clear();
//			for (Entry<Tuple<Integer,Integer>, HashMap<Integer,Integer>> perGlblIDxLclIdxCount : idxValsMap.entrySet()) {
//				glblIdx = perGlblIDxLclIdxCount.getKey();
//				lclIdxCountPerGlblIdx = perGlblIDxLclIdxCount.getValue();
//				//add up the individual lclIdxs' that are present across all glblidxs for this particular count of idxs
//				for(Entry<Integer,Integer> lclIdxCounts : lclIdxCountPerGlblIdx.entrySet()) {
//					lclIdx = lclIdxCounts.getKey();
//					lclCount = lclIdxCounts.getValue();
//					Integer curCount = countPerLclIdxAtTTLCount.get(lclIdx);
//					if(curCount == null) {
//						curCount = 0;
//					}
//					curCount += lclCount;
//					countPerLclIdxAtTTLCount.put(lclIdx,curCount);
//				}
//			}
//			//for each lcl idx, find counts, for the specified count of shares of an idx
//			for(Entry<Integer,Integer> allLclIdxCounts : countPerLclIdxAtTTLCount.entrySet()) {
//				lclIdx = allLclIdxCounts.getKey();
//				lclCount = allLclIdxCounts.getValue();
//				System.out.println("\tlocal IDX : "+ lclIdx+ " | Counts : " + lclCount);
//			}
//		}
		
//		//Having count of 1 there were 594 idxs present.
//		//Having count of 2 there were 48708 idxs present.
//		//Having count of 3 there were 8 idxs present.
//		//Having count of 4 there were 961276 idxs present.
//		//Having count of 5 there were 586 idxs present.
//		//Having count of 6 there were 66152 idxs present.
//		//Having count of 8 there were 912576 idxs present.
		
	}
	
	protected abstract base_MCCalcThreads buildMCCalcThread(int stIdx);
	
	/**
	 * Set the location of a specific iso surface mesh vert based on interpolation
	 * @param idx global edge index, as tuple of vert IDs
	 * @param _loc
	 */
	public final void synchSetVertList(Tuple<Integer,Integer> idx, myPointf _loc){
		synchronized(usedVertList){
			myMCVert tmp = usedVertList.get(idx);
			if (tmp == null) {
				tmp = new myMCVert();
				usedVertList.put(idx, tmp);
			}
			tmp.addVertLoc(_loc);
		}//synch
	}
	
	/**
	 * Retrieve the vertex list based on the verts specified for the passed myMCCube.  This is to support per-vertex normals
	 * @param gCube Cube to determine vertices for
	 * @param araIDXpI initial index in cube's vert-tuple list to query for mesh vert 
	 * @return array of myMCVerts to be used to create mesh triangle
	 */
	public final myMCVert[] synchGetVertList(myMCCube gCube, int araIDXpI) {
		int araIDXpI1 = araIDXpI+1;
		int araIDXpI2 = araIDXpI+2;
		myMCVert v1, v2, v3;
		synchronized(usedVertList) {
			v1 = usedVertList.get(gCube.edgeGlblVertIDXs[myMC_Consts.triAra[araIDXpI]]);
			v2 = usedVertList.get(gCube.edgeGlblVertIDXs[myMC_Consts.triAra[araIDXpI1]]);
			v3 = usedVertList.get(gCube.edgeGlblVertIDXs[myMC_Consts.triAra[araIDXpI2]]);
			//normal of this triangle is 
			myVectorf n = new myVectorf(v1, v2)._cross(new myVectorf(v1,v3));
			if(n.sqMagn < .00000001f) {
// 				// If this happens, means a degenerate triangle is built from iso surface
//				System.out.println("Tiny norm because : ");
//				if (v1.loc.equals(v2.loc)) {
//					System.out.println("\t!!!!v1==v2:"+ buildDebugStr(gCube, v1, "v1",araIDXpI, true) +"|"+ buildDebugStr(gCube, v2, "v2",araIDXpI1, false));
//				}
//				if (v2.loc.equals(v3.loc)) {
//					System.out.println("\t!!!!v2==v3:"+ buildDebugStr(gCube, v2, "v2",araIDXpI1, true) +"|"+ buildDebugStr(gCube, v3, "v3",araIDXpI2, false));
//				}
//				if (v3.loc.equals(v1.loc)) {
//					System.out.println("\t!!!!v3==v1:"+ buildDebugStr(gCube, v3, "v3",araIDXpI2, true) +"|"+ buildDebugStr(gCube, v1, "v1",araIDXpI, false));
//				}
//				System.out.println("");
				return null;
			} else {	
				//add normal into aggregate norm - will be normalized before display
				v1.addToNorm(n);
				v2.addToNorm(n);
				v3.addToNorm(n);
				return new myMCVert[] {v1,v2,v3};	
			}
		}//synch
	}
	
//	private String buildDebugStr(myMCCube gCube, myMCVert v, String vertName, int araIDX, boolean showLoc) {
//		return(showLoc ? v.loc.toStrBrf() : "") + " "+ vertName+": triAra["+araIDX+"]=" +myMC_Consts.triAra[araIDX] +" usedVertList Idx : "+gCube.vIdx[myMC_Consts.triAra[araIDX]];
//	}

	/**
	 * Whether to use surface vert or face normals for shading iso surface
	 */
	public abstract boolean doUseVertNorms();	

	/**
	 * Get desired threshold for Iso surface
	 */
	public abstract float getIsoLevel();
	
	public void copyDataAraToMCLclData(int[] clrPxl){
		_configureDataForMC(clrPxl);
		// set all cube and surface values, 1 slice in K dir per thread
		for(base_MCCalcThreads c : callMCCalcs) {
			c.setSimVals(doUseVertNorms(), getIsoLevel());
			_setCustomSimValsInCallable(c);
			c.setFunction(0);
		}
		try {callMCCalcFutures = th_exec.invokeAll(callMCCalcs);for(Future<Boolean> f: callMCCalcFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
		// Now build sync usedVertList list if using vert norms
		usedVertList.clear();
		if(doUseVertNorms()) {
			for(base_MCCalcThreads c : callMCCalcs) {				c.setFunction(1);			}	
			try {callMCCalcFutures = th_exec.invokeAll(callMCCalcs);for(Future<Boolean> f: callMCCalcFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
			//find avg location if using vert normals  and set vert color
			for (myMCVert vert : usedVertList.values()) {
				vert.calcAvgLoc();
			}			
		}
		// Now, build triangles
		for(base_MCCalcThreads c : callMCCalcs) {			c.setFunction(2);		}		
		try {callMCCalcFutures = th_exec.invokeAll(callMCCalcs);for(Future<Boolean> f: callMCCalcFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
		if(doUseVertNorms()) {
			//normalize all vert normals
			for (myMCVert vert : usedVertList.values()) {
				vert.normalize();
			}
		}
		//if (usedVertList.size() > 0) {System.out.println("There are "+ usedVertList.size() + " vertices in the surface mesh.");}

		//normalize triangles for vertex shading, and add all triangles to MC.triList
		for(base_MCCalcThreads c : callMCCalcs) {			c.setFunction(3);		}
		try {callMCCalcFutures = th_exec.invokeAll(callMCCalcs);for(Future<Boolean> f: callMCCalcFutures) { f.get(); }} catch (Exception e) { e.printStackTrace(); }
		
	}
	
	/**
	 * set simulation parameters in callable for MC derivation
	 * @param c
	 */
	protected abstract void _setCustomSimValsInCallable(base_MCCalcThreads c);
	
	protected abstract void _configureDataForMC(int[] datAra);
	/**
	 * Use some specific state of the iso-surface to set the entire surface's color once per draw
	 */
	protected abstract void setColorBasedOnState();
	
	protected abstract boolean useFaceValForColor();
	

	public void draw(cs7492Proj3 pa) {
		setColorBasedOnState();
		pa.noStroke();
		pa.beginShape(PConstants.TRIANGLES);
		Iterator<myMCTri> i = triList.iterator(); 
		if (useFaceValForColor()) {
	        if (doUseVertNorms()){
	        	while (i.hasNext()){    	i.next().drawMeVerts_Color(pa);   }
	        } else {
	        	while (i.hasNext()){    	i.next().drawMe_Color(pa);   }
	        }					
		} else {
	        if (doUseVertNorms()){
	        	while (i.hasNext()){    	i.next().drawMeVerts(pa);   }
	        } else {
	        	while (i.hasNext()){    	i.next().drawMe(pa);   }
	        }
		}      
		pa.endShape();	
		triList.clear();
	}

	public String toString() {
		String res  ="";// "tris: " + ntri + " volume: " + gx + " " + gy+ " " + gz + " cells: " + numCubes + " iso: " + isolevel;
		return res;
	}
	
	
}//class def

