package cs7492Project3;

import java.util.ArrayList;
import java.util.concurrent.Callable;


public abstract class base_MCCalcThreads implements Callable<Boolean> {
	protected myMCCube[] grid;				//3d grid holding marching cubes
	
	public final base_MarchingCubes MC;
	//vert idx's for comparison along cube edges
	protected final int[][] edgeVertIDXs = new int[][]{	{0,1},{1,2},{2,3},{3,0},
										{4,5},{5,6},{6,7},{7,4},
										{0,4},{1,5},{2,6},{3,7}};
	// obj data
	public float isolevel;
	// precalc powers of 2
	public static final int[] pow2 = new int[]{1,2,4,8,16,32,64,128,256,512,1024,2048,4096, 8192, 16384, 32768 };

	public int stIdx;
	public final int endI, endJ;
	
	public ArrayList<myMCTri> triList;
	/**
	 * whether or not to use vertex normals for calculation and rendering
	 */
	protected boolean useVertNorms;
	
	public base_MCCalcThreads(base_MarchingCubes _MC, myMCCube[] _grid, int _stIdx, int _gxM1, int _gyM1){
		MC = _MC;
		grid = _grid;
		isolevel = 0.5f;	
		triList = new ArrayList<myMCTri>();
		useVertNorms = false;
		
		endI = _gxM1;
		endJ = _gyM1;
		stIdx = _stIdx;
	}
	
	//Determine the index into the edge table which tells us which vertices are inside of the surface
	private void buildCubeIDX(myMCCube gCube, int modIsoLvl) {
		int cubeIDX = 0;	
		for(int i =0; i<gCube.val.length;++i){if(gCube.val[i] < modIsoLvl){	cubeIDX |= pow2[i];}}
		// Cube is entirely in or out of the surface
		if ((myMC_Consts.edgeTable[cubeIDX] == 0) || (myMC_Consts.edgeTable[cubeIDX] == 255))  {	gCube.cubeIDX = -1;}
		gCube.cubeIDX = cubeIDX;
	}
	
	//Build triangles for face-shaded surface
	private void toTriangle(myMCCube gCube, int modIsoLvl) {
		if(gCube.cubeIDX == -1) {return;}		
		myPointf[] vertList = new myPointf[edgeVertIDXs.length];
		// Find the vertices where the surface intersects the cube
		for(int i =0; i<edgeVertIDXs.length;++i){
			if ((myMC_Consts.edgeTable[gCube.cubeIDX] & pow2[i]) != 0){
				vertList[i] = gCube.VertexInterp(edgeVertIDXs[i][0], edgeVertIDXs[i][1], modIsoLvl);
			}
		}
		// Create the triangle
		int araIDX = gCube.cubeIDX << 4, araIDXpI;
		//myVectorf _loc = new myVectorf(gridI,  gridJ,  gridK);
		myMCTri tmpTri;	
		//loop through by 3s - each triangle
		for (int i = 0; myMC_Consts.triAra[araIDX + i] != -1; i += 3) {	
			araIDXpI = araIDX + i;
			tmpTri = new myMCTri(new myPointf[]{ 
							vertList[myMC_Consts.triAra[araIDXpI]], 
							vertList[myMC_Consts.triAra[araIDXpI + 1]],	
							vertList[myMC_Consts.triAra[araIDXpI + 2]]});
			triList.add(tmpTri); 
		}
		
	}//toTriangle
	
	//Aggregate all surface vertices into single list, so adjacent faces can contribute their normals.
	private void buildSyncList(myMCCube gCube, int modIsoLvl) {
		if(gCube.cubeIDX == -1) {return;}				
		// Find the vertices where the surface intersects the cube
		for(int i =0; i<edgeVertIDXs.length;++i){
			if ((myMC_Consts.edgeTable[gCube.cubeIDX] & pow2[i]) != 0){
				MC.synchSetVertList(gCube.vIdx[i], gCube.VertexInterp(edgeVertIDXs[i][0], edgeVertIDXs[i][1], modIsoLvl));
			} 
		} 
	}
	
	
	private void toTriangleVertShade(myMCCube gCube, int modIsoLvl) {
		if(gCube.cubeIDX == -1) {return;}
		// Create the triangle
		//shift cube IDX by 4 bits since we use a single array for the triangle edge map
		int araIDX = gCube.cubeIDX << 4, araIDXpI;
		myMCTri tmpTri;
		for (int i = 0; myMC_Consts.triAra[araIDX + i] != -1; i += 3) {	
			araIDXpI = araIDX + i;
			myMCVert[] triVertList = MC.synchGetVertList(gCube, araIDXpI);
			//List is null if any 2 verts are equal
			if (triVertList != null) {
				tmpTri = new myMCTri(triVertList);
				triList.add(tmpTri);
			}
		}     
	}//toTriangleVertShade

//	private String buildDebugStr(myMCCube gCube, myMCVert v, String vertName, int araIDX, boolean showLoc) {
//		return(showLoc ? v.loc.toStrBrf() : "") + " "+ vertName+": triAra["+araIDX+"]=" +myMC_Consts.triAra[araIDX] +" usedVertList Idx : "+gCube.vIdx[myMC_Consts.triAra[araIDX]];
//	}
	
	public void setSimVals(boolean useVerts, float _isoLvl) {
		isolevel=_isoLvl;
		useVertNorms=useVerts;
	}
	
	protected final void setCubeVals(myMCCube cube, int mask, int modIsoLvl) {
		for (int id=0; id<8;++id) {			//each of 8 verts on grid
			cube.val[id]= (MC.intData[cube.dataPIdx[id]] & mask);
		}
		//build and set cube index
		buildCubeIDX(cube, modIsoLvl);
	}
	
	protected abstract int getModIsoLevel();
	/**
	 * if masking array of data is necessary, build mask here.  otherwise, set to 0xFFFFFFFF/-1;
	 * @return
	 */
	protected abstract int getDataMask();
	
	protected void setAllCubeVals() {
		int idx = stIdx;
		int modIsoLvl = getModIsoLevel();	
		int mask = getDataMask();//0xFF << disp;			//mask is necessary, both u and v results returned simultaneously, mask filters
		for(int j = 0; j < endJ; ++j){
			for (int i = 0; i < endI; ++i) {
				setCubeVals(grid[idx], mask, modIsoLvl);
				++idx;
			}
		}
	}//setCubeVals()
	
	protected void buildSyncVertList() {
		if(useVertNorms) {
			int modIsoLvl = getModIsoLevel();		
			int idx = stIdx;
			for(int j = 0; j < endJ; ++j){
				for (int i = 0; i < endI; ++i) {
					buildSyncList(grid[idx], modIsoLvl);
					++idx;
				}
			}
		}
	}
	
	protected void buildTriangles() {
		triList.clear();
		int modIsoLvl = getModIsoLevel();		
		int idx = stIdx;
		if(useVertNorms) {
			for(int j = 0; j < endJ; ++j){
				for (int i = 0; i < endI; ++i) {
					toTriangleVertShade(grid[idx], modIsoLvl);
					++idx;
				}
			}		
		} else {
			for(int j = 0; j < endJ; ++j){
				for (int i = 0; i < endI; ++i) {
					toTriangle(grid[idx], modIsoLvl);
					++idx;
				}
			}	
		}	
	}
	
	protected void mergeAllTris() {
		//normalize vertex norms if appropriate; Add triangle list to list
//		if(useVertNorms) {
//			for(myMCTri tri : triList) {
//				tri.normalizeVertNorms();
//			}
//		}			
		synchronized (MC.triList) {
			MC.triList.addAll(triList);
		}	
	}
	
	protected int func = 0;
	public void setFunction(int _func) {
		//func == 0 : set cube values
		//func == 1 : build sync list for vert shaded, nothing for face shaded
		//func == 2 : build triangles
		//func == 3 : merge triangles to list
		if ((_func >=0) && (_func <=3)){
			func = _func;
		}
	}	
	
	@Override
	public Boolean call() throws Exception {
		//instead of modifying each data value by shifting, masking and division, can we multiply and shift the iso level - 1 time mult + shift instead of many shift/divs
		switch (func) {
			case 0 : { setAllCubeVals(); return true;}
			case 1 : { buildSyncVertList(); return true;}
			case 2 : { buildTriangles(); return true;}
			case 3 : { mergeAllTris(); return true;}
			default : return false;
		}
	}//call
	
}//class def
	

